package com.aamotors.crm.customer;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_consents")
public class CustomerConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentChannel channel;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(length = 32)
    private String source;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerConsent() {}

    public CustomerConsent(Customer customer, ConsentChannel channel) {
        this.customer = customer;
        this.channel = channel;
    }

    public UUID getId() { return id; }
    public Customer getCustomer() { return customer; }
    public ConsentChannel getChannel() { return channel; }
    public boolean isGranted() { return granted; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getSource() { return source; }

    public void apply(boolean grant, String source, Instant now) {
        if (grant && !granted) {
            granted = true;
            grantedAt = now;
            revokedAt = null;
        } else if (!grant && granted) {
            granted = false;
            revokedAt = now;
        }
        if (source != null) this.source = source;
    }
}
