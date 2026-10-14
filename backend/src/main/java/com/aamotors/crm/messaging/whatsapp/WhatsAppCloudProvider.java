package com.aamotors.crm.messaging.whatsapp;

import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Meta WhatsApp Business Cloud API adapter. Business-initiated marketing messages must
 * use an approved template, so the composed text is sent as the template's body
 * parameter {{1}} (newlines are flattened, as the API rejects them in parameters).
 * Endpoint: POST https://graph.facebook.com/{version}/{phone-number-id}/messages
 * Reference: Meta "Send Message Templates" and "Message Templates" documentation.
 */
public class WhatsAppCloudProvider implements ChannelProvider {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudProvider.class);

    private final IntegrationProperties props;
    private final JsonHttpClient http;
    private final ObjectMapper json;

    public WhatsAppCloudProvider(IntegrationProperties props, JsonHttpClient http, ObjectMapper json) {
        this.props = props;
        this.http = http;
        this.json = json;
    }

    @Override public Channel channel() { return Channel.WHATSAPP; }
    @Override public boolean isConfigured() { return props.whatsapp().isConfigured(); }
    @Override public boolean isEnabled() { return props.whatsapp().enabled(); }
    @Override public boolean isMock() { return false; }
    @Override public String note() {
        return props.whatsapp().hasTemplate()
                ? "Meta Cloud API: sends the approved marketing template '" + props.whatsapp().marketingTemplate() + "' with the message as body parameter."
                : "Meta Cloud API is enabled but no approved marketing template is configured (WHATSAPP_MARKETING_TEMPLATE).";
    }

    @Override
    public Eligibility eligibility(Customer c) {
        if (!isEnabled()) return Eligibility.no(DISABLED);
        if (!isConfigured()) return Eligibility.no(NOT_CONFIGURED);
        if (!props.whatsapp().hasTemplate()) return Eligibility.no(TEMPLATE_REQUIRED);
        if (c.getPhone() == null) return Eligibility.no(NO_PHONE);
        if (c.isMarketingOptOut()) return Eligibility.no(OPTED_OUT);
        if (!c.hasMarketingConsent(ConsentChannel.WHATSAPP)) return Eligibility.no(NO_CONSENT);
        return Eligibility.ok();
    }

    @Override
    public SendResult send(OutboundMessage m) {
        var wa = props.whatsapp();
        String absoluteImage = props.absoluteUrl(m.imageUrl());
        boolean withImage = absoluteImage != null && wa.marketingMediaTemplate() != null && !wa.marketingMediaTemplate().isBlank();
        String templateName = withImage ? wa.marketingMediaTemplate() : wa.marketingTemplate();

        ObjectNode body = json.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", m.customer().getPhone().replace("+", ""));
        body.put("type", "template");
        ObjectNode template = body.putObject("template");
        template.put("name", templateName);
        template.putObject("language").put("code", wa.templateLanguage() == null ? "hy" : wa.templateLanguage());
        ArrayNode components = template.putArray("components");
        if (withImage) {
            ObjectNode header = components.addObject();
            header.put("type", "header");
            header.putArray("parameters").addObject().put("type", "image").putObject("image").put("link", absoluteImage);
        }
        ObjectNode bodyComponent = components.addObject();
        bodyComponent.put("type", "body");
        bodyComponent.putArray("parameters").addObject().put("type", "text").put("text", flatten(m.text()));

        String url = "https://graph.facebook.com/" + wa.graphApiVersion() + "/" + wa.phoneNumberId() + "/messages";
        JsonHttpClient.Response res;
        try {
            res = http.postJson(url, Map.of("Authorization", "Bearer " + wa.accessToken()), body.toString());
        } catch (JsonHttpClient.TransportException e) {
            return SendResult.uncertain("WhatsApp transport error: " + e.getMessage());
        }
        try {
            JsonNode node = json.readTree(res.body() == null || res.body().isBlank() ? "{}" : res.body());
            if (res.is2xx()) {
                String id = node.path("messages").path(0).path("id").asText(null);
                return id == null ? SendResult.uncertain("WhatsApp accepted without message id") : SendResult.accepted(id);
            }
            JsonNode err = node.path("error");
            int code = err.path("code").asInt(res.status());
            String message = "WhatsApp " + res.status() + " code " + code + ": " + err.path("message").asText("");
            // 130429 = rate limit hit, 131056 = pair rate limit, 4 = app throttled, 5xx = Meta side
            if (res.status() == 429 || res.status() >= 500 || code == 130429 || code == 131056 || code == 4 || code == 80007) return SendResult.retry(message);
            return SendResult.failed(message);
        } catch (Exception e) {
            return SendResult.uncertain("WhatsApp response unreadable (" + res.status() + ")");
        }
    }

    /** Template parameters may not contain newlines, tabs or 4+ consecutive spaces. */
    static String flatten(String text) {
        return text.replaceAll("\\s*\\n+\\s*", " · ").replaceAll("\\t", " ").replaceAll(" {4,}", "   ").trim();
    }
}
