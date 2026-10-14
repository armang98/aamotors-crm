package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.common.phone.PhoneNumberNormalizer;
import com.aamotors.crm.messaging.config.MessagingProperties;
import com.aamotors.crm.messaging.config.RabbitTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Performs one delivery attempt for a queued notification. Idempotent: a notification
 * that is no longer QUEUED is ignored, so redelivered broker messages are harmless.
 * Retries go through the TTL retry queue with exponential back-off, bounded by
 * {@code app.messaging.sms.max-attempts}; a provider timeout leaves the row UNCERTAIN
 * for reconciliation instead of blindly resending.
 */
@Component
public class SmsDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(SmsDeliveryWorker.class);

    private final SmsNotificationRepository notifications;
    private final SmsProvider provider;
    private final RabbitTemplate rabbit;
    private final MessagingProperties props;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public SmsDeliveryWorker(SmsNotificationRepository notifications, SmsProvider provider, RabbitTemplate rabbit,
                             MessagingProperties props, Clock clock) {
        this.notifications = notifications;
        this.provider = provider;
        this.rabbit = rabbit;
        this.props = props;
        this.clock = clock;
        this.rateLimiter = new RateLimiter(props.sms().ratePerSecond());
    }

    @Transactional
    public void deliver(UUID notificationId) {
        SmsNotification n = notifications.findById(notificationId).orElse(null);
        if (n == null) {
            log.warn("SMS notification {} not found; dropping message", notificationId);
            return;
        }
        if (n.getStatus() != SmsNotification.Status.QUEUED) {
            log.info("SMS notification {} is {} — skipping duplicate delivery", notificationId, n.getStatus());
            return;
        }
        rateLimiter.acquire();
        Instant now = Instant.now(clock);
        SmsNotificationAttempt attempt = n.beginAttempt(now);
        SmsProvider.SmsSendResult result;
        try {
            result = provider.send(new SmsProvider.SmsMessage(n.getId() + ":" + n.getAttemptCount(), n.getRecipient(), n.getBody(), n.getLanguage().name()));
        } catch (RuntimeException e) {
            result = SmsProvider.SmsSendResult.uncertain("Provider threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        Instant finished = Instant.now(clock);
        switch (result.outcome()) {
            case ACCEPTED -> {
                attempt.finish(SmsNotificationAttempt.Outcome.ACCEPTED, result.providerMessageId(), null, finished);
                n.markSent(result.providerMessageId(), finished);
                log.info("SMS {} accepted by {} for {} (attempt {})", n.getId(), provider.name(), PhoneNumberNormalizer.mask(n.getRecipient()), n.getAttemptCount());
            }
            case UNCERTAIN -> {
                attempt.finish(SmsNotificationAttempt.Outcome.UNCERTAIN, null, result.error(), finished);
                n.markUncertain(result.error());
                log.warn("SMS {} outcome uncertain: {}", n.getId(), result.error());
            }
            case FAILED -> {
                if (result.retryable() && n.getAttemptCount() < props.sms().maxAttempts()) {
                    attempt.finish(SmsNotificationAttempt.Outcome.RETRY, null, result.error(), finished);
                    n.markRetryScheduled(result.error());
                    scheduleRetry(n);
                } else {
                    attempt.finish(SmsNotificationAttempt.Outcome.FAILED, null, result.error(), finished);
                    n.markFailed(result.error(), finished);
                    log.warn("SMS {} failed permanently after {} attempt(s): {}", n.getId(), n.getAttemptCount(), result.error());
                }
            }
        }
    }

    private void scheduleRetry(SmsNotification n) {
        long base = props.sms().baseBackoff().toMillis();
        long max = props.sms().maxBackoff().toMillis();
        long delay = Math.min(max, base * (1L << Math.min(10, n.getAttemptCount() - 1)));
        rabbit.convertAndSend("", RabbitTopology.SMS_RETRY_QUEUE,
                Map.of("notificationId", n.getId().toString(), "eventType", RabbitTopology.SMS_ROUTING_KEY), m -> {
                    m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    m.getMessageProperties().setExpiration(String.valueOf(delay));
                    return m;
                });
        log.info("SMS {} retry #{} scheduled in {} ms", n.getId(), n.getAttemptCount() + 1, delay);
    }

    /** Mock provider only: simulate the asynchronous delivery receipt so the UI shows the full lifecycle. */
    @Transactional
    public int simulateMockDeliveries() {
        if (!(provider instanceof MockSmsProvider)) return 0;
        Instant cutoff = Instant.now(clock).minus(props.sms().mockDeliveryDelay());
        var due = notifications.findByStatusAndProviderAndSentAtBefore(SmsNotification.Status.SENT, "MOCK", cutoff);
        due.forEach(n -> n.markDelivered(Instant.now(clock)));
        return due.size();
    }

    /** Minimal token bucket: at most {@code ratePerSecond} provider calls per second per instance. */
    static final class RateLimiter {
        private final double ratePerSecond;
        private double tokens;
        private long lastNanos = System.nanoTime();

        RateLimiter(double ratePerSecond) {
            this.ratePerSecond = ratePerSecond <= 0 ? 1 : ratePerSecond;
            this.tokens = this.ratePerSecond;
        }

        synchronized void acquire() {
            while (true) {
                long now = System.nanoTime();
                tokens = Math.min(ratePerSecond, tokens + (now - lastNanos) / 1e9 * ratePerSecond);
                lastNanos = now;
                if (tokens >= 1) { tokens -= 1; return; }
                try { TimeUnit.MILLISECONDS.sleep((long) Math.ceil((1 - tokens) / ratePerSecond * 1000)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
}
