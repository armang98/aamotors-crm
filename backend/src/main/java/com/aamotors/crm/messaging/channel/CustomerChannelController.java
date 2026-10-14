package com.aamotors.crm.messaging.channel;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Customer channel identities")
public class CustomerChannelController {

    private final CustomerChannelService service;

    public CustomerChannelController(CustomerChannelService service) {
        this.service = service;
    }

    @GetMapping("/api/customers/{id}/channels")
    public Map<String, Object> channels(@PathVariable UUID id) { return service.channelsOf(id); }

    @PostMapping("/api/customers/{id}/telegram/link-code")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerChannelService.LinkCode telegramLinkCode(@PathVariable UUID id) {
        return service.issueTelegramLinkCode(id, service.telegramBotUsername());
    }

    @DeleteMapping("/api/customers/{id}/telegram")
    public ResponseEntity<Void> unlinkTelegram(@PathVariable UUID id) {
        service.unlinkTelegram(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/customers/{id}/viber-bot/link-code")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerChannelService.LinkCode viberLinkCode(@PathVariable UUID id) {
        return service.issueViberLinkCode(id);
    }

    @PostMapping("/api/customers/{id}/viber-bot/link")
    public Map<String, Object> linkViber(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        var s = service.linkViberSubscriber(body.get("viberUserId"), id);
        return Map.of("subscribed", s.isSubscribed(), "customerId", id);
    }

    /** Local-only helpers that stand in for the Telegram / Viber webhooks while the real bots are not configured. */
    @RestController
    @RequestMapping("/api/mock")
    @ConditionalOnProperty(name = "app.integrations.telegram.enabled", havingValue = "false", matchIfMissing = true)
    public static class MockOnboardingController {
        private final CustomerChannelService service;

        public MockOnboardingController(CustomerChannelService service) { this.service = service; }

        @PostMapping("/telegram/start")
        public Map<String, Object> telegramStart(@RequestBody Map<String, Object> body) {
            long chatId = Long.parseLong(String.valueOf(body.get("chatId")));
            var link = service.completeTelegramLink(String.valueOf(body.get("code")), chatId, (String) body.getOrDefault("username", "mock_user"));
            return Map.of("customerId", link.getCustomerId(), "linked", link.isLinked());
        }

        @PostMapping("/viber/subscribe")
        public Map<String, Object> viberSubscribe(@RequestBody Map<String, Object> body) {
            var s = service.upsertViberSubscriber(String.valueOf(body.get("viberUserId")), (String) body.getOrDefault("name", "Mock Viber user"),
                    !Boolean.FALSE.equals(body.get("subscribed")));
            return Map.of("viberUserId", s.getViberUserId(), "subscribed", s.isSubscribed());
        }
    }
}
