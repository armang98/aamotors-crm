package com.aamotors.crm.serviceorder;

import com.aamotors.crm.AbstractIntegrationTest;
import com.aamotors.crm.messaging.outbox.OutboxEventRepository;
import com.aamotors.crm.messaging.sms.MockSmsProvider;
import com.aamotors.crm.messaging.sms.SmsNotification;
import com.aamotors.crm.messaging.sms.SmsNotificationRepository;
import com.aamotors.crm.messaging.sms.SmsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: status change → outbox row (same transaction) → relay → RabbitMQ →
 * consumer → mock provider → SENT → simulated receipt → DELIVERED.
 */
@WithMockUser(username = "admin", roles = "ADMIN")
class VehicleReadySmsIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SmsNotificationRepository notifications;
    @Autowired OutboxEventRepository outbox;
    @Autowired SmsProvider provider;

    @Test
    @DisplayName("Service order creation and status workflow")
    void workflow() throws Exception {
        UUID vehicle = vehicle("+37494" + seq(), "hy");
        MvcResult r = mvc.perform(post("/api/service-orders").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("vehicleId", vehicle, "description", "Յուղի փոխարինում", "totalAmount", 18000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.startsWith("SO-")))
                .andExpect(jsonPath("$.currency").value("AMD"))
                .andReturn();
        UUID id = UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(post("/api/service-orders/{id}/status", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("serviceOrder.invalidTransition"));
        mvc.perform(post("/api/service-orders/{id}/status", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(get("/api/service-orders").param("q", "SO-")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + id + "')]").exists());
    }

    @Test
    @DisplayName("Marking a vehicle ready sends exactly one automatic SMS through the outbox and mock provider")
    void markReadySendsSingleSms() throws Exception {
        String phone = "+37494" + seq();
        UUID vehicle = vehicle(phone, "hy");
        UUID id = order(vehicle);

        // preview in the customer's language and in English
        mvc.perform(get("/api/service-orders/{id}/sms-preview", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("hy"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("Toyota Camry (34 AB 567) ավտոմեքենան պատրաստ է")))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("+37494889881")))
                .andExpect(jsonPath("$.unicode").value(true));
        mvc.perform(get("/api/service-orders/{id}/sms-preview", id).param("language", "en"))
                .andExpect(jsonPath("$.text").value("Your Toyota Camry (34 AB 567) is ready for pickup at A&A Motors. Address: 52/2 Vazgen Sargsyan Street, Gyumri, Armenia. Phone: +37494889881."))
                .andExpect(jsonPath("$.segments").value(1));

        long outboxBefore = outbox.count();
        mvc.perform(post("/api/service-orders/{id}/mark-ready", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.readyAt").isNotEmpty())
                .andExpect(jsonPath("$.readyNotification.status").value("QUEUED"));
        assertThat(outbox.count()).isEqualTo(outboxBefore + 1);

        List<SmsNotification> list = notifications.findByServiceOrderIdOrderByCreatedAtDesc(id);
        assertThat(list).hasSize(1);
        UUID notificationId = list.get(0).getId();

        // relay + consumer + mock provider run asynchronously
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.findById(notificationId).orElseThrow().getStatus()).isEqualTo(SmsNotification.Status.SENT));
        SmsNotification sent = notifications.findById(notificationId).orElseThrow();
        assertThat(sent.getProviderMessageId()).startsWith("mock-");
        assertThat(sent.getAttemptCount()).isEqualTo(1);
        assertThat(((MockSmsProvider) provider).sentMessages()).anySatisfy(m -> {
            assertThat(m.recipient()).isEqualTo(phone);
            assertThat(m.body()).contains("Toyota Camry");
        });
        // the mock simulates the delivery receipt
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.findById(notificationId).orElseThrow().getStatus()).isEqualTo(SmsNotification.Status.DELIVERED));

        // bouncing the status back and forth must NOT produce a second automatic SMS
        mvc.perform(post("/api/service-orders/{id}/status", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/service-orders/{id}/mark-ready", id).with(csrf())).andExpect(status().isOk());
        assertThat(notifications.findByServiceOrderIdOrderByCreatedAtDesc(id)).hasSize(1);

        // manual resend requires confirmation and creates a separate MANUAL notification
        mvc.perform(post("/api/service-orders/{id}/notifications/resend", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("sms.confirmRequired"));
        mvc.perform(post("/api/service-orders/{id}/notifications/resend", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("VEHICLE_READY_MANUAL"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
        mvc.perform(get("/api/service-orders/{id}/notifications", id))
                .andExpect(jsonPath("$.length()").value(2));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.findByServiceOrderIdOrderByCreatedAtDesc(id)).allSatisfy(n ->
                        assertThat(n.getStatus()).isIn(SmsNotification.Status.SENT, SmsNotification.Status.DELIVERED)));

        mvc.perform(get("/api/sms/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/sms/status")).andExpect(jsonPath("$.provider").value("MOCK")).andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    @DisplayName("Retryable provider failures are retried with back-off; permanent failures end as FAILED; timeouts become UNCERTAIN")
    void failureModes() throws Exception {
        UUID failing = order(vehicle("+37494" + seq() + "0000".substring(0, 0), "en"));
        // numbers ending in 0000 fail permanently in the mock
        UUID permanent = order(vehicle("+3749400" + "0000", "en"));
        mvc.perform(post("/api/service-orders/{id}/mark-ready", permanent).with(csrf())).andExpect(status().isOk());
        UUID permId = notifications.findByServiceOrderIdOrderByCreatedAtDesc(permanent).get(0).getId();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.findById(permId).orElseThrow().getStatus()).isEqualTo(SmsNotification.Status.FAILED));
        assertThat(notifications.findById(permId).orElseThrow().getAttemptCount()).isEqualTo(1);

        // numbers ending in 8888 time out → UNCERTAIN, no automatic retry, no fallback
        UUID uncertain = order(vehicle("+3749411" + "8888", "en"));
        mvc.perform(post("/api/service-orders/{id}/mark-ready", uncertain).with(csrf())).andExpect(status().isOk());
        UUID uncId = notifications.findByServiceOrderIdOrderByCreatedAtDesc(uncertain).get(0).getId();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.findById(uncId).orElseThrow().getStatus()).isEqualTo(SmsNotification.Status.UNCERTAIN));

        // numbers ending in 9999 are throttled → retried via the TTL queue (base back-off is short in tests)
        UUID throttled = order(vehicle("+3749422" + "9999", "en"));
        mvc.perform(post("/api/service-orders/{id}/mark-ready", throttled).with(csrf())).andExpect(status().isOk());
        UUID thrId = notifications.findByServiceOrderIdOrderByCreatedAtDesc(throttled).get(0).getId();
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            SmsNotification n = notifications.findById(thrId).orElseThrow();
            assertThat(n.getAttemptCount()).isGreaterThanOrEqualTo(2);
        });
        assertThat(failing).isNotNull();
    }

    @Test
    @DisplayName("Customers who explicitly opted out of SMS are skipped, never messaged")
    void optOutSkipped() throws Exception {
        String phone = "+37494" + seq();
        UUID vehicle = vehicle(phone, "hy");
        String customerId = json.readTree(mvc.perform(get("/api/vehicles/{id}", vehicle)).andReturn().getResponse().getContentAsString()).get("customerId").asText();
        mvc.perform(put("/api/customers/{id}/consents", customerId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("consents", List.of(Map.of("channel", "SMS", "granted", true))))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/customers/{id}/consents", customerId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("consents", List.of(Map.of("channel", "SMS", "granted", false))))))
                .andExpect(status().isOk());
        UUID id = order(vehicle);
        mvc.perform(post("/api/service-orders/{id}/mark-ready", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyNotification.status").value("SKIPPED"));
        assertThat(notifications.findByServiceOrderIdOrderByCreatedAtDesc(id).get(0).getSkipReason()).isEqualTo("SMS_OPTED_OUT");
        mvc.perform(post("/api/service-orders/{id}/notifications/resend", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("sms.disabledForCustomer"));
    }

    // ---- helpers ----

    private UUID vehicle(String phone, String lang) throws Exception {
        Map<String, Object> customer = new HashMap<>();
        customer.put("fullName", "Test Customer");
        customer.put("phone", phone);
        customer.put("preferredLanguage", lang);
        Map<String, Object> req = new HashMap<>();
        req.put("customer", customer);
        req.put("brand", "Toyota");
        req.put("model", "Camry");
        req.put("licensePlate", "34 AB 567");
        req.put("currentMileageKm", 1000);
        MvcResult r = mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID order(UUID vehicle) throws Exception {
        MvcResult r = mvc.perform(post("/api/service-orders").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("vehicleId", vehicle))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger((int) (System.nanoTime() % 400000) + 100000);
    private static String seq() { return String.format("%06d", SEQ.incrementAndGet() % 1000000); }
}
