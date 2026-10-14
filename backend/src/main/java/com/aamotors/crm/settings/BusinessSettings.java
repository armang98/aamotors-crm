package com.aamotors.crm.settings;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Single-row table (enforced by singleton_key = 1). */
@Entity
@Table(name = "business_settings")
public class BusinessSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "singleton_key", nullable = false)
    private short singletonKey = 1;

    @Column(name = "business_name", nullable = false, length = 120)
    private String businessName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "primary_phone", nullable = false, length = 20)
    private String primaryPhone;

    @Column(name = "secondary_phone", length = 20)
    private String secondaryPhone;

    @Column(name = "working_hours", length = 255)
    private String workingHours;

    @Column(name = "default_language", nullable = false, length = 8)
    private String defaultLanguage;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "logo_file_name", length = 255)
    private String logoFileName;

    @Column(name = "logo_content_type", length = 64)
    private String logoContentType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected BusinessSettings() {}

    public UUID getId() { return id; }
    public String getBusinessName() { return businessName; }
    public String getAddress() { return address; }
    public String getPrimaryPhone() { return primaryPhone; }
    public String getSecondaryPhone() { return secondaryPhone; }
    public String getWorkingHours() { return workingHours; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public String getTimeZone() { return timeZone; }
    public String getCurrency() { return currency; }
    public String getLogoFileName() { return logoFileName; }
    public String getLogoContentType() { return logoContentType; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBusinessName(String v) { this.businessName = v; }
    public void setAddress(String v) { this.address = v; }
    public void setPrimaryPhone(String v) { this.primaryPhone = v; }
    public void setSecondaryPhone(String v) { this.secondaryPhone = v; }
    public void setWorkingHours(String v) { this.workingHours = v; }
    public void setDefaultLanguage(String v) { this.defaultLanguage = v; }
    public void setTimeZone(String v) { this.timeZone = v; }
    public void setCurrency(String v) { this.currency = v; }
    public void setLogo(String fileName, String contentType) { this.logoFileName = fileName; this.logoContentType = contentType; }
}
