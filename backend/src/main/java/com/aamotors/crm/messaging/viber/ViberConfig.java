package com.aamotors.crm.messaging.viber;

import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.channel.CustomerChannelService;
import com.aamotors.crm.messaging.channel.ViberSubscriberRepository;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class ViberConfig {

    /** Partner implementation seam: provide a bean of this type when an approved partner is contracted. */
    @Bean
    @ConditionalOnMissingBean(ViberBusinessMessagesProvider.class)
    public ViberBusinessMessagesProvider viberBusinessMessagesProvider() {
        return new ViberBusinessMessagesProvider.Disabled();
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.viber-business.enabled", havingValue = "true")
    public ChannelProvider viberBusinessChannelProvider(ViberBusinessMessagesProvider partner, IntegrationProperties props) {
        return new ViberBusinessChannelProvider(partner, props);
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.viber-bot.enabled", havingValue = "true")
    public ViberBotProvider viberBotProvider(IntegrationProperties props, JsonHttpClient http, ObjectMapper json,
                                             ViberSubscriberRepository subscribers, @Lazy CustomerChannelService channels) {
        return new ViberBotProvider(props, http, json, subscribers, channels);
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.viber-bot.enabled", havingValue = "true")
    public ChannelProvider viberBotChannelProvider(ViberBotProvider provider) {
        return provider;
    }
}
