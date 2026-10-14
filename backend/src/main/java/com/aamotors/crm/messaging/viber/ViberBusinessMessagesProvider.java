package com.aamotors.crm.messaging.viber;

/**
 * Viber Business Messages are sent on behalf of a verified business through an
 * <em>approved Viber messaging partner</em>; there is no public Viber endpoint for
 * phone-number based business messages. This interface is the seam a partner
 * implementation plugs into. Until A&A Motors contracts a partner and receives the
 * partner's API documentation, {@link Disabled} is the only implementation and the
 * channel reports NOT_CONFIGURED. No endpoint is invented here on purpose.
 */
public interface ViberBusinessMessagesProvider {

    boolean isConfigured();

    String partnerName();

    /** @param phoneE164 recipient phone, @param text message, @param imageUrl absolute image URL or null */
    com.aamotors.crm.messaging.channel.ChannelProvider.SendResult send(String idempotencyKey, String phoneE164, String text, String imageUrl, String language);

    final class Disabled implements ViberBusinessMessagesProvider {
        @Override public boolean isConfigured() { return false; }
        @Override public String partnerName() { return null; }
        @Override public com.aamotors.crm.messaging.channel.ChannelProvider.SendResult send(String k, String p, String t, String i, String l) {
            return com.aamotors.crm.messaging.channel.ChannelProvider.SendResult.failed("Viber Business Messages: no approved partner configured");
        }
    }
}
