package com.aamotors.crm.messaging.channel;

import java.util.List;

/** Advertising channels in the mandated fallback order. */
public enum Channel {
    WHATSAPP, VIBER_BUSINESS, TELEGRAM, VIBER_BOT;

    public static final List<Channel> FALLBACK_ORDER = List.of(WHATSAPP, VIBER_BUSINESS, TELEGRAM, VIBER_BOT);
}
