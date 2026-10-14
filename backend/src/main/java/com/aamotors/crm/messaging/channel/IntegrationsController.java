package com.aamotors.crm.messaging.channel;

import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.messaging.telegram.TelegramBotProvider;
import com.aamotors.crm.messaging.viber.ViberBotProvider;
import com.aamotors.crm.settings.IntegrationProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin-triggered provider setup actions (webhook registration). Never automatic, so a misconfigured host cannot hijack callbacks. */
@RestController
@RequestMapping("/api/integrations")
@Tag(name = "Integrations")
public class IntegrationsController {

    private final IntegrationProperties props;
    private final JsonHttpClient http;
    private final ObjectProvider<ViberBotProvider> viber;
    private final ObjectProvider<TelegramBotProvider> telegram;

    public IntegrationsController(IntegrationProperties props, JsonHttpClient http, ObjectProvider<ViberBotProvider> viber, ObjectProvider<TelegramBotProvider> telegram) {
        this.props = props;
        this.http = http;
        this.viber = viber;
        this.telegram = telegram;
    }

    @PostMapping("/telegram/register-webhook")
    public Map<String, Object> registerTelegram() {
        if (telegram.getIfAvailable() == null) throw ApiException.conflict("integration.disabled", "Telegram is not enabled");
        String base = requireBaseUrl();
        var res = http.postForm(TelegramBotProvider.API + props.telegram().botToken() + "/setWebhook", Map.of(), Map.of(
                "url", base + "/api/webhooks/telegram",
                "secret_token", props.telegram().webhookSecret(),
                "allowed_updates", "[\"message\",\"callback_query\",\"my_chat_member\"]"));
        return Map.of("status", res.status(), "response", res.body());
    }

    @PostMapping("/viber/register-webhook")
    public Map<String, Object> registerViber() {
        ViberBotProvider bot = viber.getIfAvailable();
        if (bot == null) throw ApiException.conflict("integration.disabled", "Viber bot is not enabled");
        var res = bot.registerWebhook(requireBaseUrl() + "/api/webhooks/viber");
        return Map.of("status", res.status(), "response", res.body());
    }

    private String requireBaseUrl() {
        String base = props.publicBaseUrl();
        if (base == null || base.isBlank() || !base.startsWith("https://")) {
            throw ApiException.badRequest("integration.publicUrlRequired", "APP_PUBLIC_BASE_URL must be an https URL");
        }
        return base.replaceAll("/+$", "");
    }
}
