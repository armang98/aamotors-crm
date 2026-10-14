package com.aamotors.crm.messaging.channel;

import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerConsent;
import com.aamotors.crm.customer.Language;
import com.aamotors.crm.messaging.http.JsonHttpClient;
import com.aamotors.crm.messaging.telegram.TelegramBotProvider;
import com.aamotors.crm.messaging.viber.ViberBotProvider;
import com.aamotors.crm.messaging.whatsapp.WhatsAppCloudProvider;
import com.aamotors.crm.settings.IntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the official-API adapters with a scripted HTTP client: request shapes
 * follow the providers' documented contracts and error classes map to ACCEPTED / FAILED /
 * RETRY / UNCERTAIN as the fallback orchestrator expects. No network access.
 */
class RealChannelAdaptersTest {

    private final ObjectMapper json = new ObjectMapper();

    /** Records requests and replays canned responses (or a transport failure). */
    static final class ScriptedHttp implements JsonHttpClient {
        record Call(String url, Map<String, String> headers, String body) {}
        final List<Call> calls = new ArrayList<>();
        final Deque<Object> responses = new ArrayDeque<>();

        ScriptedHttp reply(int status, String body) { responses.add(new Response(status, body)); return this; }
        ScriptedHttp fail() { responses.add(new TransportException("read timed out", null)); return this; }

        private Response next() {
            Object r = responses.poll();
            if (r instanceof TransportException te) throw te;
            return (Response) r;
        }
        @Override public Response postJson(String url, Map<String, String> headers, String jsonBody) { calls.add(new Call(url, headers, jsonBody)); return next(); }
        @Override public Response postForm(String url, Map<String, String> headers, Map<String, String> form) { calls.add(new Call(url, headers, form.toString())); return next(); }
        @Override public Response get(String url, Map<String, String> headers) { calls.add(new Call(url, headers, null)); return next(); }
    }

    private static IntegrationProperties props(String publicBaseUrl) {
        return new IntegrationProperties(publicBaseUrl,
                new IntegrationProperties.Sms("MOCK", false),
                new IntegrationProperties.WhatsApp(true, "EAAtoken", "123456", "987654", "v21.0", "verify", "appsecret", "aam_promo", "aam_promo_image", "hy"),
                new IntegrationProperties.ViberBusiness(false, ""),
                new IntegrationProperties.ViberBot(true, "vibertoken", "A&A Motors", "aamotors"),
                new IntegrationProperties.Telegram(true, "123:ABC", "whsecret", "AAMotorsBot"),
                new IntegrationProperties.Aws("eu-central-1", "", ""));
    }

    private static Customer customer(ConsentChannel... granted) {
        Customer c = new Customer("Արամ Պետրոսյան", "+37494889881", Language.hy);
        for (ConsentChannel ch : granted) {
            CustomerConsent consent = new CustomerConsent(c, ch);
            consent.apply(true, "TEST", Instant.now());
            c.getConsents().add(consent);
        }
        return c;
    }

    private static ChannelProvider.OutboundMessage message(Customer c, String imageUrl) {
        return new ChannelProvider.OutboundMessage("d1:WHATSAPP:1", c, "Ամառային զեղչ 15%։\n\n— Վերջին սպասարկում —\nՅուղ՝ Castrol", imageUrl, "hy");
    }

    // ---------- WhatsApp ----------

    @Test
    void whatsAppSendsApprovedMediaTemplateWithFlattenedBodyParameter() throws Exception {
        ScriptedHttp http = new ScriptedHttp().reply(200, "{\"messages\":[{\"id\":\"wamid.HBgL\"}]}");
        var p = new WhatsAppCloudProvider(props("https://crm.example.am"), http, json);
        Customer c = customer(ConsentChannel.WHATSAPP);

        assertThat(p.eligibility(c).eligible()).isTrue();
        var r = p.send(message(c, "/api/public/media/images/ad.png"));

        assertThat(r.outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.ACCEPTED);
        assertThat(r.providerMessageId()).isEqualTo("wamid.HBgL");
        ScriptedHttp.Call call = http.calls.get(0);
        assertThat(call.url()).isEqualTo("https://graph.facebook.com/v21.0/123456/messages");
        assertThat(call.headers()).containsEntry("Authorization", "Bearer EAAtoken");
        JsonNode body = json.readTree(call.body());
        assertThat(body.get("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(body.get("to").asText()).isEqualTo("37494889881");
        assertThat(body.get("type").asText()).isEqualTo("template");
        assertThat(body.at("/template/name").asText()).isEqualTo("aam_promo_image");
        assertThat(body.at("/template/language/code").asText()).isEqualTo("hy");
        assertThat(body.at("/template/components/0/type").asText()).isEqualTo("header");
        assertThat(body.at("/template/components/0/parameters/0/image/link").asText()).isEqualTo("https://crm.example.am/api/public/media/images/ad.png");
        String param = body.at("/template/components/1/parameters/0/text").asText();
        assertThat(param).doesNotContain("\n").contains("Ամառային զեղչ 15%։ · — Վերջին սպասարկում —");
    }

    @Test
    void whatsAppUsesTextTemplateWithoutImageAndRequiresTemplateAndConsent() {
        ScriptedHttp http = new ScriptedHttp().reply(200, "{\"messages\":[{\"id\":\"wamid.1\"}]}");
        var p = new WhatsAppCloudProvider(props(null), http, json);
        assertThat(p.eligibility(customer()).reason()).isEqualTo(ChannelProvider.NO_CONSENT);
        p.send(message(customer(ConsentChannel.WHATSAPP), "/api/public/media/images/ad.png"));
        // no public base URL → image cannot be referenced → plain text template
        assertThat(http.calls.get(0).body()).contains("\"name\":\"aam_promo\"").doesNotContain("header");

        var noTemplate = new IntegrationProperties.WhatsApp(true, "t", "1", "2", "v21.0", "v", "s", "", "", "hy");
        var p2 = new WhatsAppCloudProvider(new IntegrationProperties(null, null, noTemplate, null, null, null, null), http, json);
        assertThat(p2.eligibility(customer(ConsentChannel.WHATSAPP)).reason()).isEqualTo(ChannelProvider.TEMPLATE_REQUIRED);
    }

    @Test
    void whatsAppMapsRateLimitToRetryRejectionToFailedAndTimeoutToUncertain() {
        ScriptedHttp http = new ScriptedHttp()
                .reply(429, "{\"error\":{\"code\":130429,\"message\":\"Rate limit hit\"}}")
                .reply(400, "{\"error\":{\"code\":131026,\"message\":\"Message undeliverable\"}}")
                .fail();
        var p = new WhatsAppCloudProvider(props(null), http, json);
        Customer c = customer(ConsentChannel.WHATSAPP);
        assertThat(p.send(message(c, null)).outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.RETRY);
        assertThat(p.send(message(c, null)).outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.FAILED);
        assertThat(p.send(message(c, null)).outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.UNCERTAIN);
    }

    // ---------- Telegram ----------

    @Test
    void telegramSendsPhotoWithCaptionToVerifiedChatAndMarksBlockedUsers() throws Exception {
        TelegramChatLinkRepository links = mock(TelegramChatLinkRepository.class);
        CustomerChannelService channels = mock(CustomerChannelService.class);
        Customer c = customer(ConsentChannel.TELEGRAM);
        TelegramChatLink link = new TelegramChatLink(UUID.randomUUID(), Instant.now());
        link.verify(777000111L, "aram", Instant.now());
        when(links.findByCustomerId(any())).thenReturn(Optional.of(link));

        ScriptedHttp http = new ScriptedHttp()
                .reply(200, "{\"ok\":true,\"result\":{\"message_id\":42}}")
                .reply(403, "{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}");
        var p = new TelegramBotProvider(props("https://crm.example.am"), http, json, links, channels);

        assertThat(p.eligibility(c).eligible()).isTrue();
        var ok = p.send(message(c, "/api/public/media/images/ad.png"));
        assertThat(ok.outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.ACCEPTED);
        assertThat(ok.providerMessageId()).isEqualTo("42");
        ScriptedHttp.Call call = http.calls.get(0);
        assertThat(call.url()).isEqualTo("https://api.telegram.org/bot123:ABC/sendPhoto");
        JsonNode body = json.readTree(call.body());
        assertThat(body.get("chat_id").asLong()).isEqualTo(777000111L);
        assertThat(body.get("photo").asText()).startsWith("https://crm.example.am/");
        assertThat(body.get("caption").asText()).contains("Ամառային զեղչ");

        var blocked = p.send(message(c, null));
        assertThat(blocked.outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.FAILED);
        verify(channels).markTelegramBlocked(777000111L);
        assertThat(http.calls.get(1).url()).endsWith("/sendMessage");
    }

    @Test
    void telegramIsIneligibleWithoutVerifiedChat() {
        TelegramChatLinkRepository links = mock(TelegramChatLinkRepository.class);
        when(links.findByCustomerId(any())).thenReturn(Optional.empty());
        var p = new TelegramBotProvider(props(null), new ScriptedHttp(), json, links, mock(CustomerChannelService.class));
        assertThat(p.eligibility(customer(ConsentChannel.TELEGRAM)).reason()).isEqualTo(ChannelProvider.NO_IDENTITY);
        assertThat(p.send(message(customer(ConsentChannel.TELEGRAM), null)).outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.FAILED);
    }

    // ---------- Viber Bot ----------

    @Test
    void viberBotSendsToSubscribedUserIdNeverToPhoneAndHandlesStatusCodes() throws Exception {
        ViberSubscriberRepository subs = mock(ViberSubscriberRepository.class);
        CustomerChannelService channels = mock(CustomerChannelService.class);
        Customer c = customer(ConsentChannel.VIBER_BOT);
        ViberSubscriber sub = new ViberSubscriber("01234567890A=", "Aram", Instant.now());
        when(subs.findByCustomerIdAndSubscribedTrue(any())).thenReturn(List.of(sub));

        ScriptedHttp http = new ScriptedHttp()
                .reply(200, "{\"status\":0,\"status_message\":\"ok\",\"message_token\":5098034272017990000}")
                .reply(200, "{\"status\":6,\"status_message\":\"notSubscribed\"}")
                .reply(200, "{\"status\":12,\"status_message\":\"tooManyRequests\"}");
        var p = new ViberBotProvider(props("https://crm.example.am"), http, json, subs, channels);

        assertThat(p.eligibility(c).eligible()).isTrue();
        var ok = p.send(message(c, "/api/public/media/images/ad.png"));
        assertThat(ok.outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.ACCEPTED);
        assertThat(ok.providerMessageId()).isEqualTo("5098034272017990000");
        ScriptedHttp.Call call = http.calls.get(0);
        assertThat(call.url()).isEqualTo("https://chatapi.viber.com/pa/send_message");
        assertThat(call.headers()).containsEntry("X-Viber-Auth-Token", "vibertoken");
        JsonNode body = json.readTree(call.body());
        assertThat(body.get("receiver").asText()).isEqualTo("01234567890A=");
        assertThat(call.body()).doesNotContain("+37494889881");
        assertThat(body.get("type").asText()).isEqualTo("picture");
        assertThat(body.get("media").asText()).startsWith("https://crm.example.am/");
        assertThat(body.at("/sender/name").asText()).isEqualTo("A&A Motors");

        var notSubscribed = p.send(message(c, null));
        assertThat(notSubscribed.outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.FAILED);
        verify(channels).upsertViberSubscriber("01234567890A=", "Aram", false);

        assertThat(p.send(message(c, null)).outcome()).isEqualTo(ChannelProvider.SendResult.Outcome.RETRY);
    }

    @Test
    void viberBotIsIneligibleWithoutSubscriber() {
        ViberSubscriberRepository subs = mock(ViberSubscriberRepository.class);
        when(subs.findByCustomerIdAndSubscribedTrue(any())).thenReturn(List.of());
        var p = new ViberBotProvider(props(null), new ScriptedHttp(), json, subs, mock(CustomerChannelService.class));
        assertThat(p.eligibility(customer(ConsentChannel.VIBER_BOT)).reason()).isEqualTo(ChannelProvider.NO_IDENTITY);
    }
}
