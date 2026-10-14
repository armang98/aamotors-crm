package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.customer.Language;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SmsDtos {
    private SmsDtos() {}

    public record AttemptView(int attemptNo, Instant startedAt, Instant finishedAt, String outcome, String providerMessageId, String error) {}

    public record NotificationView(UUID id, UUID serviceOrderId, String orderNumber, UUID customerId, SmsNotification.Kind kind,
                                   String recipient, Language language, String body, SmsNotification.Status status, String provider,
                                   String providerMessageId, int attemptCount, String lastError, String skipReason,
                                   Instant queuedAt, Instant sentAt, Instant deliveredAt, Instant failedAt, List<AttemptView> attempts,
                                   Instant createdAt) {
        public static NotificationView from(SmsNotification n, String orderNumber) {
            return new NotificationView(n.getId(), n.getServiceOrderId(), orderNumber, n.getCustomerId(), n.getKind(), n.getRecipient(),
                    n.getLanguage(), n.getBody(), n.getStatus(), n.getProvider(), n.getProviderMessageId(), n.getAttemptCount(),
                    n.getLastError(), n.getSkipReason(), n.getQueuedAt(), n.getSentAt(), n.getDeliveredAt(), n.getFailedAt(),
                    n.getAttempts().stream().map(a -> new AttemptView(a.getAttemptNo(), a.getStartedAt(), a.getFinishedAt(),
                            a.getOutcome() == null ? null : a.getOutcome().name(), a.getProviderMessageId(), a.getError())).toList(),
                    n.getCreatedAt());
        }
    }

    public record ProviderStatus(String provider, boolean enabled, boolean configured, String originationIdentity, String region,
                                 long queued, long sent, long delivered, long failed, long uncertain) {}
}
