package com.aamotors.crm.messaging.whatsapp;

import com.aamotors.crm.advertising.FallbackOrchestrator;
import com.aamotors.crm.advertising.MessageDeliveryRepository;
import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.CustomerDtos;
import com.aamotors.crm.customer.CustomerService;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Meta webhook: GET verification handshake and POST notifications signed with
 * {@code X-Hub-Signature-256} = "sha256=" + HMAC-SHA256(app secret, raw body).
 * Handles message status updates (sent / delivered / read / failed) and inbound
 * STOP keywords (opt-out).
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final Set<String> STOP_WORDS = Set.of("stop", "unsubscribe", "stop promotions", "դադար", "стоп");

    private final IntegrationProperties props;
    private final MessageDeliveryRepository deliveries;
    private final FallbackOrchestrator orchestrator;
    private final CustomerService customers;
    private final AuditService audit;
    private final ObjectMapper json;

    public WhatsAppWebhookController(IntegrationProperties props, MessageDeliveryRepository deliveries, FallbackOrchestrator orchestrator,
                                     CustomerService customers, AuditService audit, ObjectMapper json) {
        this.props = props;
        this.deliveries = deliveries;
        this.orchestrator = orchestrator;
        this.customers = customers;
        this.audit = audit;
        this.json = json;
    }

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = props.whatsapp().webhookVerifyToken();
        if ("subscribe".equals(mode) && expected != null && !expected.isBlank() && constantTimeEquals(expected, token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                        @RequestBody byte[] rawBody) throws Exception {
        if (!verifySignature(signature, rawBody)) {
            log.warn("Rejected WhatsApp webhook with bad signature");
            return ResponseEntity.status(403).build();
        }
        JsonNode root = json.readTree(rawBody);
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                for (JsonNode st : value.path("statuses")) handleStatus(st);
                for (JsonNode msg : value.path("messages")) handleInbound(msg);
            }
        }
        return ResponseEntity.ok().build();
    }

    private void handleStatus(JsonNode st) {
        String id = st.path("id").asText(null);
        String status = st.path("status").asText("");
        if (id == null) return;
        deliveries.findByProviderMessageId(id).ifPresent(d -> {
            switch (status) {
                case "delivered", "read" -> orchestrator.confirmDelivered(d.getId());
                case "failed" -> {
                    String err = st.path("errors").path(0).path("title").asText("WhatsApp reported failure");
                    orchestrator.reopenAfterProviderFailure(d.getId(), "WhatsApp: " + err);
                }
                default -> { /* sent: still ACCEPTED */ }
            }
            audit.record("system", "WHATSAPP_STATUS", "message_delivery", d.getId().toString(), Map.of("status", status));
        });
    }

    private void handleInbound(JsonNode msg) {
        String from = msg.path("from").asText(null);
        String text = msg.path("text").path("body").asText("").trim().toLowerCase();
        if (from == null || !STOP_WORDS.contains(text)) return;
        customers.lookupByPhone("+" + from).ifPresent(c -> {
            customers.updateConsents(c.id(), new CustomerDtos.UpdateConsentsRequest(List.of(new CustomerDtos.ConsentInput(ConsentChannel.WHATSAPP, false, "WHATSAPP_STOP"))));
            audit.record("system", "WHATSAPP_OPT_OUT", "customer", c.id().toString(), null);
        });
    }

    boolean verifySignature(String header, byte[] body) {
        String secret = props.whatsapp().appSecret();
        if (secret == null || secret.isBlank() || header == null || !header.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(body));
            return constantTimeEquals(expected, header.substring("sha256=".length()));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
