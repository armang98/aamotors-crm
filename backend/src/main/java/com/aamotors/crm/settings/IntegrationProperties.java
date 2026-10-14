package com.aamotors.crm.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider configuration read from the environment. Secrets live here and are
 * never serialised to clients; {@link SettingsService} only exposes booleans.
 */
@ConfigurationProperties(prefix = "app.integrations")
public record IntegrationProperties(
        String publicBaseUrl,
        Sms sms,
        WhatsApp whatsapp,
        ViberBusiness viberBusiness,
        ViberBot viberBot,
        Telegram telegram,
        Aws aws) {

    public record Sms(String provider, boolean enabled) {}

    public record WhatsApp(boolean enabled, String accessToken, String phoneNumberId, String businessAccountId,
                           String graphApiVersion, String webhookVerifyToken, String appSecret,
                           String marketingTemplate, String marketingMediaTemplate, String templateLanguage) {
        public boolean isConfigured() {
            return notBlank(accessToken) && notBlank(phoneNumberId) && notBlank(businessAccountId)
                    && notBlank(webhookVerifyToken) && notBlank(appSecret);
        }
        public boolean hasTemplate() { return notBlank(marketingTemplate); }
    }

    public record ViberBusiness(boolean enabled, String partnerApiKey) {
        public boolean isConfigured() { return notBlank(partnerApiKey); }
    }

    public record ViberBot(boolean enabled, String authToken, String botName, String botUri) {
        public boolean isConfigured() { return notBlank(authToken); }
    }

    public record Telegram(boolean enabled, String botToken, String webhookSecret, String botUsername) {
        public boolean isConfigured() { return notBlank(botToken) && notBlank(webhookSecret); }
    }

    public record Aws(String region, String smsOriginationIdentity, String smsConfigurationSet) {
        public boolean isSmsConfigured() { return notBlank(region) && notBlank(smsOriginationIdentity); }
    }

    /** Absolute URL for a relative public media path, or null when no public base URL is configured. */
    public String absoluteUrl(String relative) {
        if (relative == null || !notBlank(publicBaseUrl)) return null;
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative;
        return publicBaseUrl.replaceAll("/+$", "") + relative;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
