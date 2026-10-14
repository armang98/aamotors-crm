package com.aamotors.crm.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bootstrap credentials, read from ADMIN_USERNAME / ADMIN_INITIAL_PASSWORD. Never hardcoded. */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String username, String initialPassword) {
    public boolean isConfigured() {
        return username != null && !username.isBlank() && initialPassword != null && !initialPassword.isBlank();
    }
}
