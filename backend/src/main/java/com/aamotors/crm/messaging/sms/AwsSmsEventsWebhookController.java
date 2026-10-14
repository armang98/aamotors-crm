package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Receives AWS End User Messaging SMS delivery events via an SNS HTTPS subscription
 * (event destination → SNS topic → this endpoint). Every message's SNS signature is
 * verified before anything is trusted. Event types follow the AWS documentation:
 * TEXT_DELIVERED, TEXT_SUCCESSFUL, TEXT_PENDING, TEXT_QUEUED, TEXT_SENT, TEXT_BLOCKED,
 * TEXT_CARRIER_BLOCKED, TEXT_CARRIER_UNREACHABLE, TEXT_INVALID, TEXT_INVALID_MESSAGE,
 * TEXT_SPAM, TEXT_TTL_EXPIRED, TEXT_UNKNOWN, TEXT_UNREACHABLE.
 */
@RestController
@RequestMapping("/api/webhooks/aws-sms-events")
public class AwsSmsEventsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AwsSmsEventsWebhookController.class);

    private final SmsNotificationRepository notifications;
    private final SnsMessageVerifier verifier;
    private final ObjectMapper json;
    private final AuditService audit;
    private final Clock clock;

    public AwsSmsEventsWebhookController(SmsNotificationRepository notifications, SnsMessageVerifier verifier,
                                         ObjectMapper json, AuditService audit, Clock clock) {
        this.notifications = notifications;
        this.verifier = verifier;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> receive(@RequestBody String rawBody) throws Exception {
        JsonNode sns = json.readTree(rawBody);
        if (!verifier.verify(sns)) {
            log.warn("Rejected SNS message with invalid signature");
            return ResponseEntity.status(403).build();
        }
        String type = sns.path("Type").asText();
        if ("SubscriptionConfirmation".equals(type)) {
            verifier.confirmSubscription(sns.path("SubscribeURL").asText());
            audit.record("system", "AWS_SNS_SUBSCRIPTION_CONFIRMED", "webhook", sns.path("TopicArn").asText(), null);
            return ResponseEntity.ok().build();
        }
        if (!"Notification".equals(type)) {
            return ResponseEntity.ok().build();
        }
        JsonNode event = json.readTree(sns.path("Message").asText());
        String eventType = event.path("eventType").asText();
        String messageId = event.path("messageId").asText(null);
        if (messageId == null) {
            return ResponseEntity.ok().build();
        }
        notifications.findByProviderMessageId(messageId).ifPresent(n -> {
            Instant now = Instant.now(clock);
            switch (eventType) {
                case "TEXT_DELIVERED", "TEXT_SUCCESSFUL" -> n.markDelivered(now);
                case "TEXT_BLOCKED", "TEXT_CARRIER_BLOCKED", "TEXT_CARRIER_UNREACHABLE", "TEXT_INVALID", "TEXT_INVALID_MESSAGE",
                     "TEXT_SPAM", "TEXT_TTL_EXPIRED", "TEXT_UNREACHABLE" -> n.markFailed("AWS " + eventType, now);
                default -> { /* TEXT_PENDING / TEXT_QUEUED / TEXT_SENT / TEXT_UNKNOWN: still SENT */ }
            }
            audit.record("system", "AWS_SMS_EVENT", "sms_notification", n.getId().toString(),
                    Map.of("eventType", eventType, "status", n.getStatus().name()));
        });
        return ResponseEntity.ok().build();
    }
}
