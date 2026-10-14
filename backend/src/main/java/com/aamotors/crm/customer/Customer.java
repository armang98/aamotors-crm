package com.aamotors.crm.customer;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone_e164", nullable = false, unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 8)
    private Language preferredLanguage = Language.hy;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "marketing_opt_out", nullable = false)
    private boolean marketingOptOut;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerConsent> consents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Customer() {}

    public Customer(String fullName, String phone, Language preferredLanguage) {
        this.fullName = fullName;
        this.phone = phone;
        this.preferredLanguage = preferredLanguage == null ? Language.hy : preferredLanguage;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public Language getPreferredLanguage() { return preferredLanguage; }
    public String getNotes() { return notes; }
    public boolean isMarketingOptOut() { return marketingOptOut; }
    public List<CustomerConsent> getConsents() { return consents; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setPreferredLanguage(Language l) { this.preferredLanguage = l; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setMarketingOptOut(boolean v) { this.marketingOptOut = v; }

    public CustomerConsent consentFor(ConsentChannel channel) {
        return consents.stream().filter(c -> c.getChannel() == channel).findFirst().orElse(null);
    }

    /** Marketing is allowed on a channel only when not opted out globally and the channel consent is granted. */
    public boolean hasMarketingConsent(ConsentChannel channel) {
        if (marketingOptOut) return false;
        CustomerConsent c = consentFor(channel);
        return c != null && c.isGranted();
    }
}
