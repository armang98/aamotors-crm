package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.settings.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.pinpointsmsvoicev2.PinpointSmsVoiceV2Client;

import java.time.Duration;

@Configuration
public class SmsProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(SmsProviderConfig.class);

    @Bean
    public SmsProvider smsProvider(IntegrationProperties integrations) {
        if ("AWS".equalsIgnoreCase(integrations.sms().provider())) {
            PinpointSmsVoiceV2Client client = PinpointSmsVoiceV2Client.builder()
                    .region(Region.of(integrations.aws().region()))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .httpClientBuilder(UrlConnectionHttpClient.builder().socketTimeout(Duration.ofSeconds(10)).connectionTimeout(Duration.ofSeconds(5)))
                    .build();
            log.info("SMS provider: AWS End User Messaging (region {}, enabled={}, configured={})",
                    integrations.aws().region(), integrations.sms().enabled(), integrations.aws().isSmsConfigured());
            return new AwsSmsProvider(client, integrations.aws(), integrations.sms().enabled());
        }
        log.info("SMS provider: MOCK (no real messages are sent)");
        return new MockSmsProvider();
    }
}
