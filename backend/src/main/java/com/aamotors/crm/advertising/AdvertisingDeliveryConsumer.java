package com.aamotors.crm.advertising;

import com.aamotors.crm.messaging.config.RabbitTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** RabbitMQ entry point; the transactional work lives in {@link FallbackOrchestrator} (separate bean so the proxy applies). */
@Component
public class AdvertisingDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(AdvertisingDeliveryConsumer.class);

    private final FallbackOrchestrator orchestrator;

    public AdvertisingDeliveryConsumer(FallbackOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @RabbitListener(queues = RabbitTopology.ADVERTISING_QUEUE)
    public void onMessage(Map<String, Object> payload) {
        UUID id;
        try {
            id = UUID.fromString(String.valueOf(payload.get("deliveryId")));
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Malformed advertising delivery message: " + payload);
        }
        try {
            orchestrator.process(id);
        } catch (Exception e) {
            log.error("Advertising delivery {} crashed; dead-lettering", id, e);
            throw new AmqpRejectAndDontRequeueException("Delivery processing failed", e);
        }
    }
}
