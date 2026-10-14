package com.aamotors.crm.common.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(String root, long logoMaxBytes) {
}
