package com.aamotors.crm.messaging.channel;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_chat_links")
public class TelegramChatLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Column(name = "chat_id", unique = true)
    private Long chatId;

    @Column(name = "link_code", length = 12, unique = true)
    private String linkCode;

    @Column(name = "code_expires_at") private Instant codeExpiresAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "blocked_at") private Instant blockedAt;
    @Column(length = 64) private String username;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TelegramChatLink() {}

    public TelegramChatLink(UUID customerId, Instant now) {
        this.customerId = customerId;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public Long getChatId() { return chatId; }
    public String getLinkCode() { return linkCode; }
    public Instant getCodeExpiresAt() { return codeExpiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getBlockedAt() { return blockedAt; }
    public String getUsername() { return username; }

    public boolean isLinked() { return chatId != null && verifiedAt != null && blockedAt == null; }

    public void issueCode(String code, Instant expiresAt) { this.linkCode = code; this.codeExpiresAt = expiresAt; }
    public void verify(long chatId, String username, Instant now) {
        this.chatId = chatId;
        this.username = username;
        this.verifiedAt = now;
        this.blockedAt = null;
        this.linkCode = null;
        this.codeExpiresAt = null;
    }
    public void markBlocked(Instant now) { this.blockedAt = now; }
    public void unlink() { this.chatId = null; this.verifiedAt = null; this.username = null; this.blockedAt = null; }
}
