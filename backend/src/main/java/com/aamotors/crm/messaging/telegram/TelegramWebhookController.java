package com.aamotors.crm.messaging.telegram;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.CustomerDtos;
import com.aamotors.crm.customer.CustomerService;
import com.aamotors.crm.customer.Language;
import com.aamotors.crm.messaging.channel.CustomerChannelService;
import com.aamotors.crm.messaging.channel.TelegramChatLinkRepository;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Telegram webhook (header {@code X-Telegram-Bot-Api-Secret-Token} must match the configured secret).
 * Handles {@code /start <code>} onboarding, the "unsubscribe" inline button, and blocked/unblocked
 * bot events ({@code my_chat_member}).
 */
@RestController
@RequestMapping("/api/webhooks/telegram")
@ConditionalOnProperty(name = "app.integrations.telegram.enabled", havingValue = "true")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final IntegrationProperties props;
    private final TelegramBotProvider bot;
    private final CustomerChannelService channels;
    private final TelegramChatLinkRepository links;
    private final CustomerService customers;
    private final AuditService audit;
    private final ObjectMapper json;

    public TelegramWebhookController(IntegrationProperties props, TelegramBotProvider bot, CustomerChannelService channels,
                                     TelegramChatLinkRepository links, CustomerService customers, AuditService audit, ObjectMapper json) {
        this.props = props;
        this.bot = bot;
        this.channels = channels;
        this.links = links;
        this.customers = customers;
        this.audit = audit;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
                                        @RequestBody String rawBody) throws Exception {
        String expected = props.telegram().webhookSecret();
        if (expected == null || expected.isBlank() || secret == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(403).build();
        }
        JsonNode update = json.readTree(rawBody);
        try {
            if (update.has("message")) handleMessage(update.get("message"));
            else if (update.has("callback_query")) handleCallback(update.get("callback_query"));
            else if (update.has("my_chat_member")) handleMembership(update.get("my_chat_member"));
        } catch (Exception e) {
            log.warn("Telegram update handling failed: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();   // always 200 so Telegram does not retry forever
    }

    private void handleMessage(JsonNode msg) {
        long chatId = msg.path("chat").path("id").asLong();
        String text = msg.path("text").asText("").trim();
        String username = msg.path("from").path("username").asText(null);
        if (text.startsWith("/start")) {
            String code = text.length() > 6 ? text.substring(6).trim() : "";
            if (code.isEmpty()) {
                bot.sendText(chatId, welcome(Language.hy) + "\n\n" + welcome(Language.en), null);
                return;
            }
            try {
                var link = channels.completeTelegramLink(code, chatId, username);
                var customer = customers.get(link.getCustomerId());
                Language lang = customer.preferredLanguage();
                bot.sendText(chatId, linked(lang, customer.fullName()), unsubscribeKeyboard(lang));
            } catch (Exception e) {
                bot.sendText(chatId, "Կոդը սխալ է կամ ժամկետանց։ / The code is invalid or expired.", null);
            }
        } else if (text.equalsIgnoreCase("/stop")) {
            optOut(chatId);
        }
    }

    private void handleCallback(JsonNode cb) {
        String data = cb.path("data").asText("");
        long chatId = cb.path("message").path("chat").path("id").asLong();
        if ("unsubscribe".equals(data)) optOut(chatId);
    }

    private void handleMembership(JsonNode m) {
        long chatId = m.path("chat").path("id").asLong();
        String status = m.path("new_chat_member").path("status").asText("");
        if ("kicked".equals(status) || "left".equals(status)) {
            channels.markTelegramBlocked(chatId);
            audit.record("system", "TELEGRAM_BOT_BLOCKED", "telegram_chat", String.valueOf(chatId), null);
        }
    }

    private void optOut(long chatId) {
        links.findByChatId(chatId).ifPresent(link -> {
            customers.updateConsents(link.getCustomerId(), new CustomerDtos.UpdateConsentsRequest(
                    List.of(new CustomerDtos.ConsentInput(ConsentChannel.TELEGRAM, false, "TELEGRAM_STOP"))));
            audit.record("system", "TELEGRAM_OPT_OUT", "customer", link.getCustomerId().toString(), null);
            bot.sendText(chatId, "Դուք այլևս չեք ստանա գովազդային հաղորդագրություններ։ / You will no longer receive promotional messages.", null);
        });
    }

    private ObjectNode unsubscribeKeyboard(Language lang) {
        ObjectNode markup = json.createObjectNode();
        var row = markup.putArray("inline_keyboard").addArray();
        row.addObject().put("text", lang == Language.en ? "Unsubscribe from promotions" : "Անջատել գովազդային հաղորդագրությունները").put("callback_data", "unsubscribe");
        return markup;
    }

    private static String welcome(Language lang) {
        return lang == Language.en
                ? "Welcome to A&A Motors! To link this chat to your customer record, open the link your service advisor gave you, or send /start <code>."
                : "Բարի գալուստ A&A Motors։ Այս զրույցը Ձեր հաճախորդի քարտին կապելու համար բացեք սպասարկման մասնագետի տված հղումը կամ ուղարկեք /start <կոդ>։";
    }

    private static String linked(Language lang, String name) {
        return lang == Language.en
                ? "Thank you, " + name + "! Your Telegram is now linked to A&A Motors. You will receive service updates and offers here."
                : "Շնորհակալություն, " + name + "։ Ձեր Telegram-ը կապված է A&A Motors-ի հետ։ Այստեղ կստանաք սպասարկման տեղեկություններ և առաջարկներ։";
    }
}
