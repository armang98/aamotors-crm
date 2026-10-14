package com.aamotors.crm.messaging.channel;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "viber_subscribers")
public class ViberSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "viber_user_id", nullable = false, unique = true, length = 64)
    private String viberUserId;

    @Column(name = "customer_id") private UUID customerId;
    @Column(name = "display_name", length = 120) private String displayName;
    @Column(nullable = false) private boolean subscribed = true;
    @Column(name = "subscribed_at", nullable = false) private Instant subscribedAt;
    @Column(name = "unsubscribed_at") private Instant unsubscribedAt;
    @Column(name = "link_code", length = 12) private String linkCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ViberSubscriber() {}

    public ViberSubscriber(String viberUserId, String displayName, Instant now) {
        this.viberUserId = viberUserId;
        this.displayName = displayName;
        this.subscribedAt = now;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getViberUserId() { return viberUserId; }
    public UUID getCustomerId() { return customerId; }
    public String getDisplayName() { return displayName; }
    public boolean isSubscribed() { return subscribed; }
    public Instant getSubscribedAt() { return subscribedAt; }
    public Instant getUnsubscribedAt() { return unsubscribedAt; }

    public void linkCustomer(UUID customerId) { this.customerId = customerId; }
    public void resubscribe(Instant now) { subscribed = true; subscribedAt = now; unsubscribedAt = null; }
    public void unsubscribe(Instant now) { subscribed = false; unsubscribedAt = now; }
}
