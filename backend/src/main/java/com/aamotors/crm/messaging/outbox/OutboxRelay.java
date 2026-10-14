package com.aamotors.crm.messaging.outbox;

import com.aamotors.crm.messaging.config.MessagingProperties;
import com.aamotors.crm.messaging.config.RabbitTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polls the outbox and publishes each event to RabbitMQ with persistent delivery.
 * At-least-once: an event is only marked published after the broker accepted it, so
 * consumers must be idempotent (they are keyed by the notification / delivery id).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbit;
    private final MessagingProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbit, MessagingProperties props, Clock clock,
                       PlatformTransactionManager txManager) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${app.messaging.outbox.poll-interval:2s}", initialDelayString = "5s")
    public void poll() {
        try {
            int published = relayBatch();
            if (published > 0) log.debug("Outbox relayed {} events", published);
        } catch (Exception e) {
            log.warn("Outbox relay failed: {}", e.getMessage());
        }
    }

    /** Runs in its own transaction (explicitly, because it is invoked from this same bean). */
    public int relayBatch() {
        Integer n = tx.execute(status -> relayLocked());
        return n == null ? 0 : n;
    }

    private int relayLocked() {
        List<OutboxEvent> batch = repository.lockPending(PageRequest.of(0, props.outbox().batchSize()));
        int ok = 0;
        for (OutboxEvent e : batch) {
            try {
                Map<String, Object> body = new java.util.LinkedHashMap<>(e.getPayload());
                body.put("eventId", e.getId().toString());
                body.put("eventType", e.getEventType());
                rabbit.convertAndSend(RabbitTopology.EVENTS_EXCHANGE, e.getEventType(), body, m -> {
                    m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    m.getMessageProperties().setMessageId(e.getId().toString());
                    return m;
                });
                e.markPublished(Instant.now(clock));
                ok++;
            } catch (Exception ex) {
                e.markFailed(ex.getMessage());
                log.warn("Could not publish outbox event {} ({}): {}", e.getId(), e.getEventType(), ex.getMessage());
            }
        }
        return ok;
    }
}
