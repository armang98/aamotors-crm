package com.aamotors.crm.auth;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 16)
    private String role = "ADMIN";

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage = "hy";

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AdminUser() {}

    public AdminUser(String username, String passwordHash, boolean mustChangePassword) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.mustChangePassword = mustChangePassword;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public boolean isEnabled() { return enabled; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
