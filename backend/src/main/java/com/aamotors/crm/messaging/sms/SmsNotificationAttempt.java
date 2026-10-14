package com.aamotors.crm.messaging.sms;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sms_notification_attempts")
public class SmsNotificationAttempt {

    public enum Outcome { ACCEPTED, FAILED, RETRY, UNCERTAIN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private SmsNotification notification;

    @Column(name = "attempt_no", nullable = false) private int attemptNo;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Enumerated(EnumType.STRING) @Column(length = 16) private Outcome outcome;
    @Column(name = "provider_message_id", length = 128) private String providerMessageId;
    @Column(length = 500) private String error;

    protected SmsNotificationAttempt() {}

    SmsNotificationAttempt(SmsNotification notification, int attemptNo, Instant startedAt) {
        this.notification = notification;
        this.attemptNo = attemptNo;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public int getAttemptNo() { return attemptNo; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Outcome getOutcome() { return outcome; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getError() { return error; }

    public void finish(Outcome outcome, String providerMessageId, String error, Instant now) {
        this.outcome = outcome;
        this.providerMessageId = providerMessageId;
        this.error = error == null ? null : error.substring(0, Math.min(500, error.length()));
        this.finishedAt = now;
    }
}
