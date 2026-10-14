package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.customer.Language;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sms_notifications")
public class SmsNotification {

    public enum Kind { VEHICLE_READY_AUTO, VEHICLE_READY_MANUAL }
    public enum Status { QUEUED, SENDING, SENT, DELIVERED, FAILED, UNCERTAIN, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_order_id", nullable = false) private UUID serviceOrderId;
    @Column(name = "customer_id", nullable = false)      private UUID customerId;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Kind kind;
    @Column(nullable = false, length = 20) private String recipient;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Language language;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.QUEUED;
    @Column(nullable = false, length = 16) private String provider;
    @Column(name = "provider_message_id", length = 128) private String providerMessageId;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "skip_reason", length = 64) private String skipReason;
    @Column(name = "queued_at", nullable = false) private Instant queuedAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "failed_at") private Instant failedAt;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attemptNo asc")
    private List<SmsNotificationAttempt> attempts = new ArrayList<>();

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected SmsNotification() {}

    public SmsNotification(UUID serviceOrderId, UUID customerId, Kind kind, String recipient, Language language,
                           String body, String provider, Instant queuedAt) {
        this.serviceOrderId = serviceOrderId;
        this.customerId = customerId;
        this.kind = kind;
        this.recipient = recipient;
        this.language = language;
        this.body = body;
        this.provider = provider;
        this.queuedAt = queuedAt;
    }

    public UUID getId() { return id; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public UUID getCustomerId() { return customerId; }
    public Kind getKind() { return kind; }
    public String getRecipient() { return recipient; }
    public Language getLanguage() { return language; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public String getSkipReason() { return skipReason; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getFailedAt() { return failedAt; }
    public List<SmsNotificationAttempt> getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public SmsNotificationAttempt beginAttempt(Instant now) {
        attemptCount++;
        status = Status.SENDING;
        SmsNotificationAttempt a = new SmsNotificationAttempt(this, attemptCount, now);
        attempts.add(a);
        return a;
    }

    public void markSent(String providerMessageId, Instant now) { status = Status.SENT; this.providerMessageId = providerMessageId; sentAt = now; lastError = null; }
    public void markDelivered(Instant now) { status = Status.DELIVERED; deliveredAt = now; }
    public void markFailed(String error, Instant now) { status = Status.FAILED; lastError = trunc(error); failedAt = now; }
    public void markRetryScheduled(String error) { status = Status.QUEUED; lastError = trunc(error); }
    public void markUncertain(String error) { status = Status.UNCERTAIN; lastError = trunc(error); }
    public void markSkipped(String reason) { status = Status.SKIPPED; skipReason = reason; }

    private static String trunc(String s) { return s == null ? null : s.substring(0, Math.min(500, s.length())); }
}
