package com.aamotors.crm.messaging.telegram;

import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.channel.CustomerChannelService;
import com.aamotors.crm.messaging.channel.TelegramChatLink;
import com.aamotors.crm.messaging.channel.TelegramChatLinkRepository;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Official Telegram Bot API adapter: {@code sendPhoto} (caption ≤ 1024) when an image
 * is attached and a public base URL exists, otherwise {@code sendMessage} (≤ 4096).
 * Only customers with a verified chat id are eligible — Telegram cannot be addressed by phone.
 */
public class TelegramBotProvider implements ChannelProvider {

    public static final String API = "https://api.telegram.org/bot";

    private final IntegrationProperties props;
    private final JsonHttpClient http;
    private final ObjectMapper json;
    private final TelegramChatLinkRepository links;
    private final CustomerChannelService channels;

    public TelegramBotProvider(IntegrationProperties props, JsonHttpClient http, ObjectMapper json,
                               TelegramChatLinkRepository links, CustomerChannelService channels) {
        this.props = props;
        this.http = http;
        this.json = json;
        this.links = links;
        this.channels = channels;
    }

    @Override public Channel channel() { return Channel.TELEGRAM; }
    @Override public boolean isConfigured() { return props.telegram().isConfigured(); }
    @Override public boolean isEnabled() { return props.telegram().enabled(); }
    @Override public boolean isMock() { return false; }
    @Override public String note() { return "Telegram Bot API: delivers to customers who started the bot and verified their link code."; }

    @Override
    public Eligibility eligibility(Customer c) {
        if (!isEnabled()) return Eligibility.no(DISABLED);
        if (!isConfigured()) return Eligibility.no(NOT_CONFIGURED);
        if (c.isMarketingOptOut()) return Eligibility.no(OPTED_OUT);
        if (!c.hasMarketingConsent(ConsentChannel.TELEGRAM)) return Eligibility.no(NO_CONSENT);
        var link = links.findByCustomerId(c.getId()).orElse(null);
        if (link == null || link.getChatId() == null || link.getVerifiedAt() == null) return Eligibility.no(NO_IDENTITY);
        if (link.getBlockedAt() != null) return Eligibility.no(BLOCKED);
        return Eligibility.ok();
    }

    @Override
    public SendResult send(OutboundMessage m) {
        TelegramChatLink link = links.findByCustomerId(m.customer().getId()).filter(TelegramChatLink::isLinked).orElse(null);
        if (link == null) return SendResult.failed("Customer has no verified Telegram chat");
        String image = props.absoluteUrl(m.imageUrl());
        ObjectNode body = json.createObjectNode();
        body.put("chat_id", link.getChatId());
        String method;
        if (image != null) {
            method = "sendPhoto";
            body.put("photo", image);
            body.put("caption", truncate(m.text(), 1024));
        } else {
            method = "sendMessage";
            body.put("text", truncate(m.text(), 4096));
        }
        return call(method, body, link);
    }

    SendResult call(String method, ObjectNode body, TelegramChatLink link) {
        JsonHttpClient.Response res;
        try {
            res = http.postJson(API + props.telegram().botToken() + "/" + method, Map.of(), body.toString());
        } catch (JsonHttpClient.TransportException e) {
            return SendResult.uncertain("Telegram transport error: " + e.getMessage());
        }
        try {
            JsonNode node = json.readTree(res.body() == null || res.body().isBlank() ? "{}" : res.body());
            if (node.path("ok").asBoolean(false)) {
                return SendResult.accepted(String.valueOf(node.path("result").path("message_id").asLong()));
            }
            int code = node.path("error_code").asInt(res.status());
            String desc = node.path("description").asText("");
            if (code == 403 && link != null) {
                channels.markTelegramBlocked(link.getChatId());
                return SendResult.failed("Telegram: bot blocked by the user");
            }
            if (code == 429 || code >= 500) return SendResult.retry("Telegram " + code + ": " + desc);
            return SendResult.failed("Telegram " + code + ": " + desc);
        } catch (Exception e) {
            return SendResult.uncertain("Telegram response unreadable (" + res.status() + ")");
        }
    }

    /** Plain text message with an optional inline keyboard (used by the webhook for onboarding replies). */
    public SendResult sendText(long chatId, String text, ObjectNode replyMarkup) {
        ObjectNode body = json.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", truncate(text, 4096));
        if (replyMarkup != null) body.set("reply_markup", replyMarkup);
        return call("sendMessage", body, null);
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
