package com.aamotors.crm.referral;

import com.aamotors.crm.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** The referrer's one-time 10 % entitlement. Exactly one per referral. */
@Entity
@Table(name = "referral_rewards")
public class ReferralReward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referral_id", nullable = false, unique = true)
    private Referral referral;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RewardStatus status = RewardStatus.NOT_ISSUED;

    @Column(name = "discount_percent", nullable = false)
    private short discountPercent = ReferralPolicy.DISCOUNT_PERCENT;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "redeemed_service_order_id", unique = true)
    private UUID redeemedServiceOrderId;

    @Column(name = "redemption_notes", length = 255)
    private String redemptionNotes;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ReferralReward() {}

    ReferralReward(Referral referral, Customer customer) {
        this.referral = referral;
        this.customer = customer;
    }

    public UUID getId() { return id; }
    public Referral getReferral() { return referral; }
    public Customer getCustomer() { return customer; }
    public RewardStatus getStatus() { return status; }
    public int getDiscountPercent() { return discountPercent; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public UUID getRedeemedServiceOrderId() { return redeemedServiceOrderId; }
    public String getRedemptionNotes() { return redemptionNotes; }
    public String getVoidReason() { return voidReason; }

    void issue(Instant at) {
        this.status = RewardStatus.AVAILABLE;
        this.issuedAt = at;
    }

    void redeem(UUID serviceOrderId, String notes, Instant at) {
        this.status = RewardStatus.REDEEMED;
        this.redeemedServiceOrderId = serviceOrderId;
        this.redemptionNotes = notes;
        this.redeemedAt = at;
    }

    void voidReward(String reason) {
        this.status = RewardStatus.VOIDED;
        this.voidReason = reason;
    }
}
