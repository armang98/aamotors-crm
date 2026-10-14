package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.messaging.config.RabbitTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class SmsDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(SmsDeliveryConsumer.class);

    private final SmsDeliveryWorker worker;

    public SmsDeliveryConsumer(SmsDeliveryWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = RabbitTopology.SMS_QUEUE)
    public void onMessage(Map<String, Object> payload) {
        UUID id;
        try {
            id = UUID.fromString(String.valueOf(payload.get("notificationId")));
        } catch (Exception e) {
            // poison message → dead-letter queue, never requeued
            throw new AmqpRejectAndDontRequeueException("Malformed SMS delivery message: " + payload);
        }
        try {
            worker.deliver(id);
        } catch (Exception e) {
            log.error("SMS delivery for {} crashed; sending to DLQ", id, e);
            throw new AmqpRejectAndDontRequeueException("SMS delivery failed", e);
        }
    }

    @Scheduled(fixedDelayString = "2s", initialDelayString = "10s")
    public void mockDeliveryReceipts() {
        try {
            worker.simulateMockDeliveries();
        } catch (Exception e) {
            log.debug("mock delivery simulation skipped: {}", e.getMessage());
        }
    }
}
