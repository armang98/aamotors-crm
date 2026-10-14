package com.aamotors.crm.advertising;

import com.aamotors.crm.common.media.MediaAsset;
import com.aamotors.crm.common.media.MediaAssetRepository;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerRepository;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.channel.ChannelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aamotors.crm.messaging.config.MessagingProperties;
import com.aamotors.crm.messaging.config.RabbitTopology;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Walks the channel list for one delivery, strictly in order, one channel at a time:
 * <ul>
 *   <li>eligibility (consent, identity, configuration) is re-checked immediately before each attempt;</li>
 *   <li>ineligible channels are recorded as SKIPPED attempts and the next channel is tried;</li>
 *   <li>ACCEPTED stops the chain (delivery confirmation arrives later via webhooks);</li>
 *   <li>FAILED moves to the next channel;</li>
 *   <li>UNCERTAIN stops the chain and leaves the delivery for manual reconciliation — never a blind resend;</li>
 *   <li>when channels are exhausted the delivery is FAILED (or SKIPPED when no channel was ever tried).</li>
 * </ul>
 * Idempotent: a delivery that is not PENDING is ignored, and each attempt carries a unique idempotency key.
 */
@Component
public class FallbackOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FallbackOrchestrator.class);

    private final MessageDeliveryRepository deliveries;
    private final CustomerRepository customers;
    private final MediaAssetRepository media;
    private final ChannelRegistry registry;
    private final RabbitTemplate rabbit;
    private final MessagingProperties props;
    private final Clock clock;

    static final int MAX_RETRIES_PER_CHANNEL = 3;

    public FallbackOrchestrator(MessageDeliveryRepository deliveries, CustomerRepository customers, MediaAssetRepository media,
                                ChannelRegistry registry, RabbitTemplate rabbit, MessagingProperties props, Clock clock) {
        this.deliveries = deliveries;
        this.customers = customers;
        this.media = media;
        this.registry = registry;
        this.rabbit = rabbit;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void process(UUID deliveryId) {
        MessageDelivery d = deliveries.findById(deliveryId).orElse(null);
        if (d == null) { log.warn("Delivery {} not found", deliveryId); return; }
        if (d.getStatus() != MessageDelivery.Status.PENDING) {
            log.info("Delivery {} is {} — ignoring duplicate message", deliveryId, d.getStatus());
            return;
        }
        Customer c = customers.findById(d.getCustomerId()).orElse(null);
        if (c == null) { d.setStatus(MessageDelivery.Status.FAILED); d.setSkipReason("CUSTOMER_DELETED"); d.getSend().recompute(); return; }
        if (c.isMarketingOptOut()) { d.setStatus(MessageDelivery.Status.SKIPPED); d.setSkipReason(ChannelProvider.OPTED_OUT); d.getSend().recompute(); return; }

        String imageUrl = d.getSend().getImageAssetId() == null ? null
                : media.findById(d.getSend().getImageAssetId()).map(MediaAsset::publicUrl).orElse(null);
        List<ChannelProvider> order = registry.inFallbackOrder();
        d.setStatus(MessageDelivery.Status.ATTEMPTING);
        boolean anyTried = false;

        for (int i = d.getChannelIndex(); i < order.size(); i++) {
            ChannelProvider p = order.get(i);
            Channel ch = p.channel();
            d.setChannelIndex(i);
            d.setCurrentChannel(ch);
            Instant now = Instant.now(clock);
            MessageAttempt attempt = d.newAttempt(ch, now);

            var e = p.eligibility(c);           // consent is re-checked right before sending
            if (!e.eligible()) {
                attempt.finish(MessageAttempt.Outcome.SKIPPED, e.reason(), null, null, now);
                continue;
            }
            anyTried = true;
            ChannelProvider.SendResult r;
            try {
                r = p.send(new ChannelProvider.OutboundMessage(attempt.getIdempotencyKey(), c, d.getRenderedMessage(), imageUrl, c.getPreferredLanguage().name()));
            } catch (RuntimeException ex) {
                r = ChannelProvider.SendResult.uncertain("Provider threw " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            Instant done = Instant.now(clock);
            switch (r.outcome()) {
                case ACCEPTED -> {
                    attempt.finish(MessageAttempt.Outcome.ACCEPTED, null, r.providerMessageId(), null, done);
                    d.markAccepted(ch, r.providerMessageId());
                    log.info("Delivery {} accepted on {} ({})", d.getId(), ch, r.providerMessageId());
                    d.getSend().recompute();
                    return;
                }
                case UNCERTAIN -> {
                    attempt.finish(MessageAttempt.Outcome.UNCERTAIN, null, null, r.error(), done);
                    d.setStatus(MessageDelivery.Status.UNCERTAIN);
                    log.warn("Delivery {} uncertain on {}: {} — stopping fallback for manual review", d.getId(), ch, r.error());
                    d.getSend().recompute();
                    return;
                }
                case RETRY -> {
                    long retriesOnChannel = d.getAttempts().stream().filter(a -> a.getChannel() == ch && a.getOutcome() == MessageAttempt.Outcome.RETRY).count();
                    if (retriesOnChannel + 1 >= MAX_RETRIES_PER_CHANNEL) {
                        attempt.finish(MessageAttempt.Outcome.FAILED, "RETRIES_EXHAUSTED", null, r.error(), done);
                        log.info("Delivery {} exhausted retries on {} ({}); trying next channel", d.getId(), ch, r.error());
                        continue;
                    }
                    attempt.finish(MessageAttempt.Outcome.RETRY, null, null, r.error(), done);
                    d.setStatus(MessageDelivery.Status.PENDING);   // same channel, later
                    scheduleRetry(d, retriesOnChannel + 1);
                    d.getSend().recompute();
                    return;
                }
                case FAILED -> {
                    attempt.finish(MessageAttempt.Outcome.FAILED, null, null, r.error(), done);
                    log.info("Delivery {} failed on {} ({}); trying next channel", d.getId(), ch, r.error());
                }
            }
        }
        d.setCurrentChannel(null);
        if (anyTried) {
            d.setStatus(MessageDelivery.Status.FAILED);
        } else {
            d.setStatus(MessageDelivery.Status.SKIPPED);
            d.setSkipReason("NO_ELIGIBLE_CHANNEL");
        }
        d.getSend().recompute();
    }

    private void scheduleRetry(MessageDelivery d, long retryNo) {
        long base = props.sms().baseBackoff().toMillis();
        long max = props.sms().maxBackoff().toMillis();
        long delay = Math.min(max, base * (1L << Math.min(10, retryNo - 1)));
        rabbit.convertAndSend("", RabbitTopology.ADVERTISING_RETRY_QUEUE,
                Map.of("deliveryId", d.getId().toString(), "eventType", RabbitTopology.ADVERTISING_ROUTING_KEY), m -> {
                    m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    m.getMessageProperties().setExpiration(String.valueOf(delay));
                    return m;
                });
        log.info("Delivery {} retry #{} on {} scheduled in {} ms", d.getId(), retryNo, d.getCurrentChannel(), delay);
    }

    /** Provider webhooks report a conclusive failure after acceptance → resume the fallback chain on the next channel. */
    @Transactional
    public void reopenAfterProviderFailure(UUID deliveryId, String error) {
        MessageDelivery d = deliveries.findById(deliveryId).orElse(null);
        if (d == null || d.getStatus() != MessageDelivery.Status.ACCEPTED) return;
        Instant now = Instant.now(clock);
        d.getAttempts().stream().filter(a -> a.getOutcome() == MessageAttempt.Outcome.ACCEPTED)
                .reduce((a, b) -> b).ifPresent(a -> a.finish(MessageAttempt.Outcome.FAILED, "PROVIDER_REPORTED_FAILURE", a.getProviderMessageId(), error, now));
        d.setStatus(MessageDelivery.Status.PENDING);
        d.setChannelIndex(d.getChannelIndex() + 1);
        rabbit.convertAndSend(RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.ADVERTISING_ROUTING_KEY,
                Map.of("deliveryId", d.getId().toString(), "eventType", RabbitTopology.ADVERTISING_ROUTING_KEY),
                m -> { m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT); return m; });
        d.getSend().recompute();
        log.info("Delivery {} reopened after provider failure on {}: {}", d.getId(), d.getCurrentChannel(), error);
    }

    /** Provider webhooks confirm delivery. */
    @Transactional
    public void confirmDelivered(UUID deliveryId) {
        deliveries.findById(deliveryId).ifPresent(d -> {
            if (d.getStatus() == MessageDelivery.Status.ACCEPTED) { d.markDelivered(Instant.now(clock)); d.getSend().recompute(); }
        });
    }
}
