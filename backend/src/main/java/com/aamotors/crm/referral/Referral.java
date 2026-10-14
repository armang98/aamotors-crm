package com.aamotors.crm.referral;

import com.aamotors.crm.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referrals")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_customer_id", nullable = false)
    private Customer referrer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_customer_id", nullable = false, unique = true)
    private Customer invited;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReferralStatus status = ReferralStatus.PENDING;

    @Column(name = "discount_percent", nullable = false)
    private short discountPercent = ReferralPolicy.DISCOUNT_PERCENT;

    @Column(name = "qualifying_service_order_id")
    private UUID qualifyingServiceOrderId;

    @Column(name = "qualified_at")
    private Instant qualifiedAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToOne(mappedBy = "referral", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ReferralReward reward;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Referral() {}

    public Referral(Customer referrer, Customer invited, String notes) {
        this.referrer = referrer;
        this.invited = invited;
        this.notes = notes;
        this.reward = new ReferralReward(this, referrer);
    }

    public UUID getId() { return id; }
    public Customer getReferrer() { return referrer; }
    public Customer getInvited() { return invited; }
    public ReferralStatus getStatus() { return status; }
    public int getDiscountPercent() { return discountPercent; }
    public UUID getQualifyingServiceOrderId() { return qualifyingServiceOrderId; }
    public Instant getQualifiedAt() { return qualifiedAt; }
    public String getCancelReason() { return cancelReason; }
    public String getNotes() { return notes; }
    public ReferralReward getReward() { return reward; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    void confirm(UUID serviceOrderId, Instant at) {
        this.status = ReferralStatus.CONFIRMED;
        this.qualifyingServiceOrderId = serviceOrderId;
        this.qualifiedAt = at;
    }

    void markRewarded() { this.status = ReferralStatus.REWARDED; }

    void cancel(String reason) {
        this.status = ReferralStatus.CANCELLED;
        this.cancelReason = reason;
    }
}
