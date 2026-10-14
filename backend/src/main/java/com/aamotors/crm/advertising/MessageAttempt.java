package com.aamotors.crm.advertising;

import com.aamotors.crm.messaging.channel.Channel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_attempts")
public class MessageAttempt {

    public enum Outcome { SKIPPED, ACCEPTED, FAILED, RETRY, UNCERTAIN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private MessageDelivery delivery;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Channel channel;
    @Column(name = "attempt_no", nullable = false) private int attemptNo;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(length = 16) private Outcome outcome;
    @Column(length = 64) private String reason;
    @Column(name = "provider_message_id", length = 128) private String providerMessageId;
    @Column(length = 500) private String error;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected MessageAttempt() {}

    MessageAttempt(MessageDelivery delivery, Channel channel, int attemptNo, String idempotencyKey, Instant startedAt) {
        this.delivery = delivery;
        this.channel = channel;
        this.attemptNo = attemptNo;
        this.idempotencyKey = idempotencyKey;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public Channel getChannel() { return channel; }
    public int getAttemptNo() { return attemptNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Outcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getError() { return error; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public void finish(Outcome outcome, String reason, String providerMessageId, String error, Instant now) {
        this.outcome = outcome;
        this.reason = reason;
        this.providerMessageId = providerMessageId;
        this.error = error == null ? null : error.substring(0, Math.min(500, error.length()));
        this.finishedAt = now;
    }
}
