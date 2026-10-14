package com.aamotors.crm.messaging.viber;

import com.aamotors.crm.advertising.FallbackOrchestrator;
import com.aamotors.crm.advertising.MessageDeliveryRepository;
import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.customer.Language;
import com.aamotors.crm.messaging.channel.CustomerChannelService;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Viber bot callbacks, authenticated with {@code X-Viber-Content-Signature} =
 * HMAC-SHA256(auth token, raw body) in hex. Events: webhook, subscribed, unsubscribed,
 * conversation_started (with deep-link context), message, delivered, seen, failed.
 */
@RestController
@RequestMapping("/api/webhooks/viber")
@ConditionalOnProperty(name = "app.integrations.viber-bot.enabled", havingValue = "true")
public class ViberWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ViberWebhookController.class);

    private final IntegrationProperties props;
    private final ViberBotProvider bot;
    private final CustomerChannelService channels;
    private final MessageDeliveryRepository deliveries;
    private final FallbackOrchestrator orchestrator;
    private final AuditService audit;
    private final ObjectMapper json;

    public ViberWebhookController(IntegrationProperties props, ViberBotProvider bot, CustomerChannelService channels,
                                  MessageDeliveryRepository deliveries, FallbackOrchestrator orchestrator, AuditService audit, ObjectMapper json) {
        this.props = props;
        this.bot = bot;
        this.channels = channels;
        this.deliveries = deliveries;
        this.orchestrator = orchestrator;
        this.audit = audit;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Viber-Content-Signature", required = false) String signature,
                                        @RequestBody byte[] rawBody) throws Exception {
        if (!verify(signature, rawBody)) {
            log.warn("Rejected Viber callback with bad signature");
            return ResponseEntity.status(403).build();
        }
        JsonNode ev = json.readTree(rawBody);
        String event = ev.path("event").asText("");
        try {
            switch (event) {
                case "subscribed" -> {
                    JsonNode u = ev.path("user");
                    channels.upsertViberSubscriber(u.path("id").asText(), u.path("name").asText(null), true);
                }
                case "unsubscribed" -> channels.upsertViberSubscriber(ev.path("user_id").asText(), null, false);
                case "conversation_started" -> {
                    JsonNode u = ev.path("user");
                    String context = ev.path("context").asText(null);
                    var linked = channels.completeViberLink(context, u.path("id").asText(), u.path("name").asText(null));
                    bot.sendText(u.path("id").asText(), linked.isPresent() ? linkedText() : welcomeText());
                }
                case "message" -> {
                    JsonNode sender = ev.path("sender");
                    String text = ev.path("message").path("text").asText("").trim();
                    if (text.matches("[A-Z0-9]{6}")) {
                        var linked = channels.completeViberLink(text, sender.path("id").asText(), sender.path("name").asText(null));
                        bot.sendText(sender.path("id").asText(), linked.isPresent() ? linkedText() : "Կոդը սխալ է կամ ժամկետանց։ / Invalid or expired code.");
                    } else {
                        channels.upsertViberSubscriber(sender.path("id").asText(), sender.path("name").asText(null), true);
                    }
                }
                case "delivered", "seen" -> byToken(ev).ifPresent(id -> orchestrator.confirmDelivered(id));
                case "failed" -> byToken(ev).ifPresent(id -> orchestrator.reopenAfterProviderFailure(id, "Viber: " + ev.path("desc").asText("delivery failed")));
                default -> { /* webhook ping etc. */ }
            }
            if (!event.isEmpty() && !"webhook".equals(event)) {
                audit.record("system", "VIBER_EVENT", "webhook", event, null);
            }
        } catch (Exception e) {
            log.warn("Viber event {} handling failed: {}", event, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private java.util.Optional<java.util.UUID> byToken(JsonNode ev) {
        String token = ev.path("message_token").asText(null);
        return token == null ? java.util.Optional.empty() : deliveries.findByProviderMessageId(token).map(d -> d.getId());
    }

    boolean verify(String signature, byte[] body) {
        String token = props.viberBot().authToken();
        if (token == null || token.isBlank() || signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static String welcomeText() {
        return "Բարի գալուստ A&A Motors։ Հաճախորդի քարտին կապվելու համար ուղարկեք Ձեր 6-նիշ կոդը։\nWelcome to A&A Motors! Send your 6-character code to link this chat to your customer record.";
    }

    private static String linkedText() {
        return "Շնորհակալություն, Ձեր Viber-ը կապված է A&A Motors-ի հետ։\nThank you, your Viber is now linked to A&A Motors.";
    }
}
