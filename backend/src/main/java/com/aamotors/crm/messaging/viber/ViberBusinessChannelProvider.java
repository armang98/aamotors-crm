package com.aamotors.crm.messaging.viber;

import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.settings.IntegrationProperties;

/** Channel wrapper around {@link ViberBusinessMessagesProvider}; active only when VIBER_BUSINESS_ENABLED=true. */
public class ViberBusinessChannelProvider implements ChannelProvider {

    private final ViberBusinessMessagesProvider partner;
    private final IntegrationProperties props;

    public ViberBusinessChannelProvider(ViberBusinessMessagesProvider partner, IntegrationProperties props) {
        this.partner = partner;
        this.props = props;
    }

    @Override public Channel channel() { return Channel.VIBER_BUSINESS; }
    @Override public boolean isConfigured() { return partner.isConfigured(); }
    @Override public boolean isEnabled() { return props.viberBusiness().enabled(); }
    @Override public boolean isMock() { return false; }
    @Override public String note() {
        return partner.isConfigured() ? "Viber Business Messages via partner " + partner.partnerName()
                : "Viber Business Messages requires an approved Viber partner; no partner is configured.";
    }

    @Override
    public Eligibility eligibility(Customer c) {
        if (!isEnabled()) return Eligibility.no(DISABLED);
        if (!partner.isConfigured()) return Eligibility.no(NOT_CONFIGURED);
        if (c.getPhone() == null) return Eligibility.no(NO_PHONE);
        if (c.isMarketingOptOut()) return Eligibility.no(OPTED_OUT);
        if (!c.hasMarketingConsent(ConsentChannel.VIBER_BUSINESS)) return Eligibility.no(NO_CONSENT);
        return Eligibility.ok();
    }

    @Override
    public SendResult send(OutboundMessage m) {
        return partner.send(m.idempotencyKey(), m.customer().getPhone(), m.text(), props.absoluteUrl(m.imageUrl()), m.language());
    }
}
