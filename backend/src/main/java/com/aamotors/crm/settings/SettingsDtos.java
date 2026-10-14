package com.aamotors.crm.settings;

import com.aamotors.crm.common.phone.E164;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record IntegrationStatus(String provider, boolean enabled, boolean configured) {
        public static IntegrationStatus of(boolean enabled, boolean configured) {
            return new IntegrationStatus(null, enabled, configured);
        }
    }

    public record Integrations(
            IntegrationStatus sms,
            IntegrationStatus whatsapp,
            IntegrationStatus viberBusiness,
            IntegrationStatus viberBot,
            IntegrationStatus telegram) {}

    public record BusinessSettingsResponse(
            String businessName,
            String address,
            String primaryPhone,
            String secondaryPhone,
            String workingHours,
            String defaultLanguage,
            String timeZone,
            String currency,
            String logoUrl,
            Integrations integrations,
            Instant updatedAt) {}

    public record UpdateBusinessSettingsRequest(
            @NotBlank @Size(max = 120) String businessName,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @E164 String primaryPhone,
            @E164 String secondaryPhone,
            @Size(max = 255) String workingHours,
            @NotBlank @Pattern(regexp = "hy|en", message = "must be hy or en") String defaultLanguage,
            @NotBlank @Size(max = 64) String timeZone,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String currency) {}
}
