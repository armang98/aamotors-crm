package com.aamotors.crm.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {}

    public record AdminProfile(
            UUID id,
            String username,
            String role,
            String preferredLanguage,
            boolean mustChangePassword,
            Instant lastLoginAt) {
        public static AdminProfile from(AdminUser u) {
            return new AdminProfile(u.getId(), u.getUsername(), u.getRole(), u.getPreferredLanguage(),
                    u.isMustChangePassword(), u.getLastLoginAt());
        }
    }

    public record LanguageRequest(
            @NotBlank @Pattern(regexp = "hy|en", message = "must be hy or en") String language) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {}
}
