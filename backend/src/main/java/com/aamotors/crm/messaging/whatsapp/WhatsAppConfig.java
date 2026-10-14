package com.aamotors.crm.messaging.whatsapp;

import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WhatsAppConfig {
    @Bean
    @ConditionalOnProperty(name = "app.integrations.whatsapp.enabled", havingValue = "true")
    public ChannelProvider whatsAppCloudProvider(IntegrationProperties props, JsonHttpClient http, ObjectMapper json) {
        return new WhatsAppCloudProvider(props, http, json);
    }
}
