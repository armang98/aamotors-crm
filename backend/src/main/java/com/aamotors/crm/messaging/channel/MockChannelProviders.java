package com.aamotors.crm.messaging.channel;

import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.settings.IntegrationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock implementations used in local development and tests. They apply the same
 * eligibility rules as the real adapters (consent, identity, configuration) but
 * never call a provider. Numbers ending in {@code 0000} fail everywhere, {@code 8888}
 * time out (UNCERTAIN) — handy for demonstrating reconciliation.
 *
 * Real adapters (Phase 7) replace these beans when their {@code *_ENABLED} flag is true.
 */
@Configuration
public class MockChannelProviders {

    public record SentMessage(Channel channel, UUID customerId, String recipient, String text, String imageUrl, String providerMessageId) {}

    /** Shared in-memory log so the UI/tests can inspect what the mocks "sent". */
    public static final List<SentMessage> SENT = new CopyOnWriteArrayList<>();

    static ChannelProvider.SendResult simulate(Channel channel, ChannelProvider.OutboundMessage m, String recipient) {
        String phone = m.customer().getPhone();
        if (phone != null && phone.endsWith("0000")) return ChannelProvider.SendResult.failed("MOCK " + channel + ": provider rejected recipient (simulated)");
        if (phone != null && phone.endsWith("8888")) return ChannelProvider.SendResult.uncertain("MOCK " + channel + ": timeout (simulated)");
        String id = "mock-" + channel.name().toLowerCase() + "-" + UUID.randomUUID();
        SENT.add(new SentMessage(channel, m.customer().getId(), recipient, m.text(), m.imageUrl(), id));
        return ChannelProvider.SendResult.accepted(id);
    }

    static ChannelProvider.Eligibility consent(Customer c, ConsentChannel channel) {
        if (c.isMarketingOptOut()) return ChannelProvider.Eligibility.no(ChannelProvider.OPTED_OUT);
        if (!c.hasMarketingConsent(channel)) return ChannelProvider.Eligibility.no(ChannelProvider.NO_CONSENT);
        return ChannelProvider.Eligibility.ok();
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.whatsapp.enabled", havingValue = "false", matchIfMissing = true)
    public ChannelProvider mockWhatsApp() {
        return new ChannelProvider() {
            public Channel channel() { return Channel.WHATSAPP; }
            public boolean isConfigured() { return false; }
            public boolean isEnabled() { return true; }
            public boolean isMock() { return true; }
            public String note() { return "Mock: simulates an approved marketing template send to consenting customers."; }
            public Eligibility eligibility(Customer c) {
                if (c.getPhone() == null) return Eligibility.no(NO_PHONE);
                return consent(c, ConsentChannel.WHATSAPP);
            }
            public SendResult send(OutboundMessage m) { return simulate(Channel.WHATSAPP, m, m.customer().getPhone()); }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.viber-business.enabled", havingValue = "false", matchIfMissing = true)
    public ChannelProvider mockViberBusiness(IntegrationProperties props) {
        return new ChannelProvider() {
            public Channel channel() { return Channel.VIBER_BUSINESS; }
            public boolean isConfigured() { return props.viberBusiness().isConfigured(); }
            public boolean isEnabled() { return false; }
            public boolean isMock() { return true; }
            public String note() { return "Viber Business Messages requires an approved Viber partner; disabled until partner credentials and API documentation are provided."; }
            public Eligibility eligibility(Customer c) { return Eligibility.no(NOT_CONFIGURED); }
            public SendResult send(OutboundMessage m) { return SendResult.failed("Viber Business Messages is not configured"); }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.telegram.enabled", havingValue = "false", matchIfMissing = true)
    public ChannelProvider mockTelegram(TelegramChatLinkRepository links) {
        return new ChannelProvider() {
            public Channel channel() { return Channel.TELEGRAM; }
            public boolean isConfigured() { return false; }
            public boolean isEnabled() { return true; }
            public boolean isMock() { return true; }
            public String note() { return "Mock: delivers only to customers who linked a Telegram chat (use the link code / mock start endpoint)."; }
            public Eligibility eligibility(Customer c) {
                Eligibility consent = consent(c, ConsentChannel.TELEGRAM);
                if (!consent.eligible()) return consent;
                return links.findByCustomerId(c.getId()).filter(TelegramChatLink::isLinked).map(l -> Eligibility.ok())
                        .orElse(Eligibility.no(NO_IDENTITY));
            }
            public SendResult send(OutboundMessage m) {
                return links.findByCustomerId(m.customer().getId()).filter(TelegramChatLink::isLinked)
                        .map(l -> simulate(Channel.TELEGRAM, m, "chat:" + l.getChatId()))
                        .orElse(SendResult.failed("Customer has no linked Telegram chat"));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.viber-bot.enabled", havingValue = "false", matchIfMissing = true)
    public ChannelProvider mockViberBot(ViberSubscriberRepository subscribers) {
        return new ChannelProvider() {
            public Channel channel() { return Channel.VIBER_BOT; }
            public boolean isConfigured() { return false; }
            public boolean isEnabled() { return true; }
            public boolean isMock() { return true; }
            public String note() { return "Mock: delivers only to customers linked to a subscribed Viber bot user id (never by phone number)."; }
            public Eligibility eligibility(Customer c) {
                Eligibility consent = consent(c, ConsentChannel.VIBER_BOT);
                if (!consent.eligible()) return consent;
                return subscribers.findByCustomerIdAndSubscribedTrue(c.getId()).isEmpty() ? Eligibility.no(NO_IDENTITY) : Eligibility.ok();
            }
            public SendResult send(OutboundMessage m) {
                var subs = subscribers.findByCustomerIdAndSubscribedTrue(m.customer().getId());
                if (subs.isEmpty()) return SendResult.failed("Customer is not a subscribed Viber bot user");
                return simulate(Channel.VIBER_BOT, m, "viber:" + subs.get(0).getViberUserId());
            }
        };
    }
}
