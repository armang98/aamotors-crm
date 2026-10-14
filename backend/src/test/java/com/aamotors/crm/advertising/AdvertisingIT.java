package com.aamotors.crm.advertising;

import com.aamotors.crm.AbstractIntegrationTest;
import com.aamotors.crm.messaging.channel.MockChannelProviders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(username = "admin", roles = "ADMIN")
class AdvertisingIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessageDeliveryRepository deliveries;

    @Test
    @DisplayName("Consent validation: no channel consent → no eligible channel, nothing is sent")
    void consentValidation() throws Exception {
        UUID noConsent = customer("+37494" + seq(), List.of());
        MvcResult r = mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(compose(List.of(noConsent), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.recipients[0].skipReason").value("NO_ELIGIBLE_CHANNEL"))
                .andExpect(jsonPath("$.recipients[0].channels[0].channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.recipients[0].channels[0].reason").value("NO_CONSENT"))
                .andExpect(jsonPath("$.recipients[0].channels[1].channel").value("VIBER_BUSINESS"))
                .andExpect(jsonPath("$.recipients[0].channels[1].reason").value("NOT_CONFIGURED"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("TELEGRAM").contains("VIBER_BOT");

        mvc.perform(post("/api/advertising/send").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(compose(List.of(noConsent), true))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("advertising.noEligibleRecipients"));

        // globally opted-out customers are never messaged even with consents
        UUID optedOut = customer("+37494" + seq(), List.of("WHATSAPP"));
        mvc.perform(put("/api/customers/{id}", optedOut).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fullName", "Opt Out", "phone", phoneOf(optedOut), "marketingOptOut", true)))).andExpect(status().isOk());
        mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(compose(List.of(optedOut), false))))
                .andExpect(jsonPath("$.recipients[0].channels[0].reason").value("OPTED_OUT"))
                .andExpect(jsonPath("$.eligibleCount").value(0));
    }

    @Test
    @DisplayName("WhatsApp mock delivery with image and service history; duplicate messages are prevented")
    void whatsappDeliveryWithHistory() throws Exception {
        UUID c = customer("+37494" + seq(), List.of("WHATSAPP"));
        vehicleWithHistory(c);
        MvcResult img = mvc.perform(multipart("/api/media/images").file(new MockMultipartFile("file", "ad.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3})).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/public/media/images/"))).andReturn();
        UUID imageId = UUID.fromString(json.readTree(img.getResponse().getContentAsString()).get("id").asText());

        Map<String, Object> req = compose(List.of(c), true);
        req.put("includeServiceHistory", true);
        req.put("imageId", imageId);
        mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(jsonPath("$.recipients[0].firstChannel").value("WHATSAPP"))
                .andExpect(jsonPath("$.recipients[0].needsVehicleSelection").value(false))
                .andExpect(jsonPath("$.recipients[0].renderedMessage").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Ամառային զեղչ"),
                        org.hamcrest.Matchers.containsString("Վերջին սպասարկում"),
                        org.hamcrest.Matchers.containsString("Castrol Edge 5W-30 (Շարժիչ)"),
                        org.hamcrest.Matchers.containsString("Յուղի ֆիլտր՝ Mann W 712/95 (W712-95)"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Սրահի ֆիլտր")))));

        int sentBefore = MockChannelProviders.SENT.size();
        MvcResult send = mvc.perform(post("/api/advertising/send").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.counts.total").value(1))
                .andReturn();
        UUID sendId = UUID.fromString(json.readTree(send.getResponse().getContentAsString()).get("id").asText());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deliveries.findBySendId(sendId).get(0).getStatus()).isEqualTo(MessageDelivery.Status.ACCEPTED));
        MessageDelivery d = deliveries.findBySendId(sendId).get(0);
        assertThat(d.getCurrentChannel().name()).isEqualTo("WHATSAPP");
        assertThat(d.getProviderMessageId()).startsWith("mock-whatsapp-");
        assertThat(MockChannelProviders.SENT.size()).isEqualTo(sentBefore + 1);
        assertThat(MockChannelProviders.SENT.get(MockChannelProviders.SENT.size() - 1).imageUrl()).contains("/api/public/media/images/");

        // the orchestrator ignores a redelivered message for a non-pending delivery → no duplicate send
        mvc.perform(get("/api/advertising/sends/{id}", sendId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.deliveries[0].attempts.length()").value(1))
                .andExpect(jsonPath("$.deliveries[0].attempts[0].outcome").value("ACCEPTED"));
        assertThat(MockChannelProviders.SENT.size()).isEqualTo(sentBefore + 1);
    }

    @Test
    @DisplayName("Sequential fallback: WhatsApp skipped (no consent) → Viber Business skipped (not configured) → Telegram accepted only when linked")
    void telegramFallback() throws Exception {
        UUID c = customer("+37494" + seq(), List.of("TELEGRAM"));
        // not linked yet → Telegram ineligible
        mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(compose(List.of(c), false))))
                .andExpect(jsonPath("$.recipients[0].channels[2].channel").value("TELEGRAM"))
                .andExpect(jsonPath("$.recipients[0].channels[2].reason").value("NO_IDENTITY"))
                .andExpect(jsonPath("$.recipients[0].firstChannel").doesNotExist());

        // onboarding: link code → deep link + QR, then the (mock) bot start completes the link
        MvcResult code = mvc.perform(post("/api/customers/{id}/telegram/link-code", c).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deepLink").value(org.hamcrest.Matchers.containsString("?start=")))
                .andExpect(jsonPath("$.qrSvg").value(org.hamcrest.Matchers.startsWith("<svg")))
                .andReturn();
        String linkCode = json.readTree(code.getResponse().getContentAsString()).get("code").asText();
        mvc.perform(post("/api/mock/telegram/start").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", linkCode, "chatId", 123456789L + SEQ.get(), "username", "aram"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linked").value(true));
        mvc.perform(get("/api/customers/{id}/channels", c))
                .andExpect(jsonPath("$.telegram.linked").value(true))
                .andExpect(jsonPath("$.telegram.chatId").value(org.hamcrest.Matchers.containsString("*")));

        MvcResult send = mvc.perform(post("/api/advertising/send").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(compose(List.of(c), true))))
                .andExpect(status().isCreated()).andReturn();
        UUID sendId = UUID.fromString(json.readTree(send.getResponse().getContentAsString()).get("id").asText());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deliveries.findBySendId(sendId).get(0).getStatus()).isEqualTo(MessageDelivery.Status.ACCEPTED));

        MvcResult detail = mvc.perform(get("/api/advertising/sends/{id}", sendId)).andReturn();
        JsonNode attempts = json.readTree(detail.getResponse().getContentAsString()).get("deliveries").get(0).get("attempts");
        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0).get("channel").asText()).isEqualTo("WHATSAPP");
        assertThat(attempts.get(0).get("outcome").asText()).isEqualTo("SKIPPED");
        assertThat(attempts.get(0).get("reason").asText()).isEqualTo("NO_CONSENT");
        assertThat(attempts.get(1).get("channel").asText()).isEqualTo("VIBER_BUSINESS");
        assertThat(attempts.get(1).get("reason").asText()).isEqualTo("NOT_CONFIGURED");
        assertThat(attempts.get(2).get("channel").asText()).isEqualTo("TELEGRAM");
        assertThat(attempts.get(2).get("outcome").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("Viber Bot is the fourth channel and needs a subscribed, linked user id — never a phone number")
    void viberBotEligibility() throws Exception {
        UUID c = customer("+37494" + seq(), List.of("VIBER_BOT"));
        mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(compose(List.of(c), false))))
                .andExpect(jsonPath("$.recipients[0].channels[3].channel").value("VIBER_BOT"))
                .andExpect(jsonPath("$.recipients[0].channels[3].reason").value("NO_IDENTITY"));

        String viberId = "viber-user-" + seq();
        mvc.perform(post("/api/mock/viber/subscribe").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("viberUserId", viberId, "name", "Ani")))).andExpect(status().isOk());
        mvc.perform(post("/api/customers/{id}/viber-bot/link", c).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("viberUserId", viberId)))).andExpect(status().isOk());
        mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(compose(List.of(c), false))))
                .andExpect(jsonPath("$.recipients[0].firstChannel").value("VIBER_BOT"));

        // unsubscribe → ineligible again
        mvc.perform(post("/api/mock/viber/subscribe").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("viberUserId", viberId, "subscribed", false)))).andExpect(status().isOk());
        mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(compose(List.of(c), false))))
                .andExpect(jsonPath("$.recipients[0].channels[3].reason").value("NO_IDENTITY"));
    }

    @Test
    @DisplayName("Uncertain provider outcome stops the fallback chain and waits for manual reconciliation")
    void uncertainStopsFallback() throws Exception {
        UUID c = customer("+3749455" + "8888", List.of("WHATSAPP", "TELEGRAM"));   // 8888 → mock timeout
        MvcResult send = mvc.perform(post("/api/advertising/send").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(compose(List.of(c), true))))
                .andExpect(status().isCreated()).andReturn();
        UUID sendId = UUID.fromString(json.readTree(send.getResponse().getContentAsString()).get("id").asText());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deliveries.findBySendId(sendId).get(0).getStatus()).isEqualTo(MessageDelivery.Status.UNCERTAIN));
        MessageDelivery d = deliveries.findBySendId(sendId).get(0);
        // no fallback to Telegram while WhatsApp is ambiguous
        mvc.perform(get("/api/advertising/sends/{id}", sendId))
                .andExpect(jsonPath("$.deliveries[0].attempts.length()").value(1))
                .andExpect(jsonPath("$.deliveries[0].attempts[0].channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.deliveries[0].attempts[0].outcome").value("UNCERTAIN"));

        mvc.perform(get("/api/advertising/deliveries").param("status", "UNCERTAIN"))
                .andExpect(jsonPath("$.content[?(@.id=='" + d.getId() + "')]").exists());
        mvc.perform(post("/api/advertising/deliveries/{id}/resolve", d.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("outcome", "DELIVERED", "note", "Customer confirmed by phone"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("Vehicle selection is required when a customer owns several vehicles and history is included")
    void vehicleSelectionRequired() throws Exception {
        UUID c = customer("+37494" + seq(), List.of("WHATSAPP"));
        vehicleWithHistory(c);
        vehicleWithHistory(c);
        Map<String, Object> req = compose(List.of(c), true);
        req.put("includeServiceHistory", true);
        MvcResult p = mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(jsonPath("$.recipients[0].needsVehicleSelection").value(true))
                .andExpect(jsonPath("$.recipients[0].vehicles.length()").value(2)).andReturn();
        mvc.perform(post("/api/advertising/send").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("advertising.vehicleSelectionRequired"));
        String vehicleId = json.readTree(p.getResponse().getContentAsString()).get("recipients").get(0).get("vehicles").get(1).get("id").asText();
        req.put("vehicleSelections", Map.of(c.toString(), vehicleId));
        mvc.perform(post("/api/advertising/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(jsonPath("$.recipients[0].needsVehicleSelection").value(false));
    }

    // ---- helpers ----

    private Map<String, Object> compose(List<UUID> ids, boolean confirm) {
        Map<String, Object> m = new HashMap<>();
        m.put("customerIds", ids);
        m.put("message", "Ամառային զեղչ 15% բոլոր սպասարկումների վրա մինչև ամսվա վերջ։");
        m.put("includeServiceHistory", false);
        m.put("confirm", confirm);
        return m;
    }

    private final Map<UUID, String> phones = new HashMap<>();
    private String phoneOf(UUID id) { return phones.get(id); }

    private UUID customer(String phone, List<String> consents) throws Exception {
        MvcResult r = mvc.perform(post("/api/customers").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Արամ Պետրոսյան", "phone", phone, "preferredLanguage", "hy"))))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());
        phones.put(id, phone);
        if (!consents.isEmpty()) {
            mvc.perform(put("/api/customers/{id}/consents", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("consents", consents.stream().map(ch -> Map.of("channel", ch, "granted", true, "source", "TEST")).toList()))))
                    .andExpect(status().isOk());
        }
        return id;
    }

    private void vehicleWithHistory(UUID customerId) throws Exception {
        Map<String, Object> service = new HashMap<>();
        service.put("serviceDate", LocalDate.now().minusDays(10).toString());
        service.put("mileageKm", 120_500);
        service.put("nextOilChangeMileageKm", 130_500);
        service.put("oilName", "Castrol Edge 5W-30");
        service.put("oilType", "ENGINE");
        service.put("oilFilterName", "Mann W 712/95");
        service.put("oilFilterCode", "W712-95");
        service.put("airFilterName", "Mahle Air");
        service.put("description", "Փոխվել են յուղը և ֆիլտրերը։");
        Map<String, Object> req = new HashMap<>();
        req.put("customerId", customerId);
        req.put("brand", "Toyota");
        req.put("model", "Camry");
        req.put("licensePlate", "34 AB " + (SEQ.incrementAndGet() % 1000));
        req.put("currentMileageKm", 120_500);
        req.put("initialService", service);
        mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger((int) (System.nanoTime() % 300000) + 600000);
    private static String seq() { return String.format("%06d", SEQ.incrementAndGet() % 1000000); }
}
