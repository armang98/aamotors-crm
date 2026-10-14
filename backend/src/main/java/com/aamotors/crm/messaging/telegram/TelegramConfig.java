package com.aamotors.crm.messaging.telegram;

import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.channel.CustomerChannelService;
import com.aamotors.crm.messaging.channel.TelegramChatLinkRepository;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class TelegramConfig {
    @Bean
    @ConditionalOnProperty(name = "app.integrations.telegram.enabled", havingValue = "true")
    public TelegramBotProvider telegramBotProvider(IntegrationProperties props, JsonHttpClient http, ObjectMapper json,
                                                   TelegramChatLinkRepository links, @Lazy CustomerChannelService channels) {
        return new TelegramBotProvider(props, http, json, links, channels);
    }

    /** Exposes the Telegram provider under the ChannelProvider type for the registry. */
    @Bean
    @ConditionalOnProperty(name = "app.integrations.telegram.enabled", havingValue = "true")
    public ChannelProvider telegramChannelProvider(TelegramBotProvider provider) {
        return provider;
    }
}
