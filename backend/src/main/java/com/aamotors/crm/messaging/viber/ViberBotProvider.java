package com.aamotors.crm.messaging.viber;

import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.channel.CustomerChannelService;
import com.aamotors.crm.messaging.channel.ViberSubscriber;
import com.aamotors.crm.messaging.channel.ViberSubscriberRepository;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Official Viber REST Bot API adapter. Endpoint {@code https://chatapi.viber.com/pa/send_message},
 * authenticated with the {@code X-Viber-Auth-Token} header. Recipients are Viber user ids of
 * subscribed bot users — a phone number is never used as a recipient id.
 * Response {@code status} 0 = ok; 6 = notSubscribed; 12 = tooManyRequests.
 */
public class ViberBotProvider implements ChannelProvider {

    public static final String SEND_MESSAGE = "https://chatapi.viber.com/pa/send_message";
    public static final String SET_WEBHOOK = "https://chatapi.viber.com/pa/set_webhook";

    private final IntegrationProperties props;
    private final JsonHttpClient http;
    private final ObjectMapper json;
    private final ViberSubscriberRepository subscribers;
    private final CustomerChannelService channels;

    public ViberBotProvider(IntegrationProperties props, JsonHttpClient http, ObjectMapper json,
                            ViberSubscriberRepository subscribers, CustomerChannelService channels) {
        this.props = props;
        this.http = http;
        this.json = json;
        this.subscribers = subscribers;
        this.channels = channels;
    }

    @Override public Channel channel() { return Channel.VIBER_BOT; }
    @Override public boolean isConfigured() { return props.viberBot().isConfigured(); }
    @Override public boolean isEnabled() { return props.viberBot().enabled(); }
    @Override public boolean isMock() { return false; }
    @Override public String note() { return "Viber Bot API: delivers to subscribed bot users linked to the customer (never by phone number)."; }

    @Override
    public Eligibility eligibility(Customer c) {
        if (!isEnabled()) return Eligibility.no(DISABLED);
        if (!isConfigured()) return Eligibility.no(NOT_CONFIGURED);
        if (c.isMarketingOptOut()) return Eligibility.no(OPTED_OUT);
        if (!c.hasMarketingConsent(ConsentChannel.VIBER_BOT)) return Eligibility.no(NO_CONSENT);
        return subscribers.findByCustomerIdAndSubscribedTrue(c.getId()).isEmpty() ? Eligibility.no(NO_IDENTITY) : Eligibility.ok();
    }

    @Override
    public SendResult send(OutboundMessage m) {
        List<ViberSubscriber> subs = subscribers.findByCustomerIdAndSubscribedTrue(m.customer().getId());
        if (subs.isEmpty()) return SendResult.failed("Customer is not a subscribed Viber bot user");
        String image = props.absoluteUrl(m.imageUrl());
        ObjectNode body = json.createObjectNode();
        body.put("receiver", subs.get(0).getViberUserId());
        body.put("min_api_version", 1);
        body.putObject("sender").put("name", props.viberBot().botName() == null ? "A&A Motors" : props.viberBot().botName());
        body.put("tracking_data", m.idempotencyKey());
        if (image != null) {
            body.put("type", "picture");
            body.put("media", image);
            body.put("text", truncate(m.text(), 512));   // picture description limit
        } else {
            body.put("type", "text");
            body.put("text", truncate(m.text(), 7000));
        }
        return call(body, subs.get(0));
    }

    SendResult call(ObjectNode body, ViberSubscriber subscriber) {
        JsonHttpClient.Response res;
        try {
            res = http.postJson(SEND_MESSAGE, Map.of("X-Viber-Auth-Token", props.viberBot().authToken()), body.toString());
        } catch (JsonHttpClient.TransportException e) {
            return SendResult.uncertain("Viber transport error: " + e.getMessage());
        }
        try {
            JsonNode node = json.readTree(res.body() == null || res.body().isBlank() ? "{}" : res.body());
            int status = node.path("status").asInt(-1);
            String message = node.path("status_message").asText("");
            if (res.is2xx() && status == 0) return SendResult.accepted(node.path("message_token").asText(null));
            if (status == 6) {   // notSubscribed
                if (subscriber != null) channels.upsertViberSubscriber(subscriber.getViberUserId(), subscriber.getDisplayName(), false);
                return SendResult.failed("Viber: user is not subscribed");
            }
            if (status == 12 || res.status() == 429 || res.status() >= 500) return SendResult.retry("Viber " + status + ": " + message);
            return SendResult.failed("Viber " + status + ": " + message);
        } catch (Exception e) {
            return SendResult.uncertain("Viber response unreadable (" + res.status() + ")");
        }
    }

    /** Welcome / confirmation text to a single user (used by the webhook). */
    public SendResult sendText(String viberUserId, String text) {
        ObjectNode body = json.createObjectNode();
        body.put("receiver", viberUserId);
        body.put("min_api_version", 1);
        body.putObject("sender").put("name", props.viberBot().botName() == null ? "A&A Motors" : props.viberBot().botName());
        body.put("type", "text");
        body.put("text", truncate(text, 7000));
        return call(body, null);
    }

    /** Registers the webhook URL with Viber (set_webhook). */
    public JsonHttpClient.Response registerWebhook(String url) {
        ObjectNode body = json.createObjectNode();
        body.put("url", url);
        body.putArray("event_types").add("delivered").add("seen").add("failed").add("subscribed").add("unsubscribed").add("conversation_started").add("message");
        body.put("send_name", true);
        body.put("send_photo", false);
        return http.postJson(SET_WEBHOOK, Map.of("X-Viber-Auth-Token", props.viberBot().authToken()), body.toString());
    }

    static String truncate(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
}
