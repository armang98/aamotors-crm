package com.aamotors.crm.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.login")
public record LoginSecurityProperties(int maxFailures, Duration lockDuration) {
}
