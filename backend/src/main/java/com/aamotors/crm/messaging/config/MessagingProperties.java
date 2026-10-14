package com.aamotors.crm.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(Outbox outbox, Sms sms) {
    public record Outbox(Duration pollInterval, int batchSize) {}
    public record Sms(int maxAttempts, Duration baseBackoff, Duration maxBackoff, double ratePerSecond, Duration mockDeliveryDelay) {}
}
