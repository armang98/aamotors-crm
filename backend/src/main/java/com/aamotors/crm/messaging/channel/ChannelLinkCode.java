package com.aamotors.crm.messaging.channel;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channel_link_codes")
public class ChannelLinkCode {

    @Id
    @Column(length = 12)
    private String code;

    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Channel channel;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ChannelLinkCode() {}

    public ChannelLinkCode(String code, UUID customerId, Channel channel, Instant expiresAt, Instant now) {
        this.code = code;
        this.customerId = customerId;
        this.channel = channel;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public String getCode() { return code; }
    public UUID getCustomerId() { return customerId; }
    public Channel getChannel() { return channel; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public boolean isValid(Instant now) { return usedAt == null && expiresAt.isAfter(now); }
    public void use(Instant now) { this.usedAt = now; }
}
