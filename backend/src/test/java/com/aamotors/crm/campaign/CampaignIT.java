package com.aamotors.crm.campaign;

import com.aamotors.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
class CampaignIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CampaignRepository campaigns;

    @Test
    @DisplayName("Campaign creation, segment filters, draft lifecycle (edit, duplicate, delete)")
    void creationAndSegment() throws Exception {
        String tag = "Seg" + seq();
        UUID toyotaEngine = customerWithVehicle(tag + "A", "+37494" + seq(), List.of("WHATSAPP"), "Toyota", "Camry", "ENGINE", "Castrol Edge 5W-30");
        UUID kiaTransmission = customerWithVehicle(tag + "B", "+37494" + seq(), List.of("WHATSAPP"), "Kia", "Rio", "TRANSMISSION", "ZIC ATF");
        UUID toyotaNoConsent = customerWithVehicle(tag + "C", "+37494" + seq(), List.of(), "Toyota", "RAV4", "ENGINE", "Mobil 1");

        Map<String, Object> filters = new HashMap<>();
        filters.put("brand", "toyota");
        filters.put("oilType", "ENGINE");
        MvcResult seg = mvc.perform(post("/api/campaigns/segment/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("filters", filters))))
                .andExpect(status().isOk()).andReturn();
        var tree = json.readTree(seg.getResponse().getContentAsString());
        List<String> ids = tree.get("sample").findValues("customerId").stream().map(n -> n.asText()).toList();
        assertThat(ids).contains(toyotaEngine.toString(), toyotaNoConsent.toString()).doesNotContain(kiaTransmission.toString());
        assertThat(tree.get("byChannel").get("WHATSAPP").asLong()).isGreaterThanOrEqualTo(1);

        Map<String, Object> req = campaignRequest("Spring oil change " + tag, filters);
        MvcResult created = mvc.perform(post("/api/campaigns").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.filters.brand").value("toyota"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Yerevan"))
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());

        req.put("name", "Spring oil change v2 " + tag);
        mvc.perform(put("/api/campaigns/{id}", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Spring oil change v2 " + tag));

        MvcResult dup = mvc.perform(post("/api/campaigns/{id}/duplicate", id).with(csrf())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spring oil change v2 " + tag + " (copy)")).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        UUID copy = UUID.fromString(json.readTree(dup.getResponse().getContentAsString()).get("id").asText());
        mvc.perform(delete("/api/campaigns/{id}", copy).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/campaigns/{id}", copy)).andExpect(status().isNotFound());

        // scheduling in the past is refused; scheduling in the future materialises recipients
        mvc.perform(post("/api/campaigns/{id}/schedule", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scheduledDate", "2020-01-01", "scheduledTime", "10:00"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("campaign.scheduleInPast"));
        mvc.perform(post("/api/campaigns/{id}/schedule", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scheduledDate", LocalDate.now().plusDays(7).toString(), "scheduledTime", "10:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledAt").isNotEmpty())
                .andExpect(jsonPath("$.eligibleCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/campaigns/{id}/recipients", id))
                .andExpect(jsonPath("$.content[?(@.customerId=='" + toyotaEngine + "')].eligible").value(true))
                .andExpect(jsonPath("$.content[?(@.customerId=='" + toyotaNoConsent + "')].eligible").value(false))
                .andExpect(jsonPath("$.content[?(@.customerId=='" + toyotaNoConsent + "')].reason").value("NO_ELIGIBLE_CHANNEL"));

        // scheduled campaigns can be cancelled; cancelled ones cannot be edited
        mvc.perform(post("/api/campaigns/{id}/cancel", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Changed plans"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(put("/api/campaigns/{id}", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("campaign.notEditable"));
        mvc.perform(delete("/api/campaigns/{id}", id).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("campaign.notDraft"));
    }

    @Test
    @DisplayName("Scheduled campaign execution: the scheduler starts it, dispatches in batches and completes it with delivery results")
    void scheduledExecution() throws Exception {
        String brand = "Brand" + seq();
        UUID a = customerWithVehicle("A", "+37494" + seq(), List.of("WHATSAPP"), brand, "One", "ENGINE", "Oil");
        UUID b = customerWithVehicle("B", "+37494" + seq(), List.of("WHATSAPP"), brand, "Two", "ENGINE", "Oil");
        UUID failing = customerWithVehicle("C", "+3749477" + "0000", List.of("WHATSAPP"), brand, "Three", "ENGINE", "Oil"); // 0000 → mock failure

        Map<String, Object> filters = Map.of("brand", brand);
        Map<String, Object> req = campaignRequest("Scheduled " + brand, filters);
        UUID id = UUID.fromString(json.readTree(mvc.perform(post("/api/campaigns").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andReturn().getResponse().getContentAsString()).get("id").asText());

        LocalDateTime when = LocalDateTime.now(ZoneId.of("Asia/Yerevan")).plusSeconds(3);
        mvc.perform(post("/api/campaigns/{id}/schedule", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scheduledDate", when.toLocalDate().toString(),
                                "scheduledTime", when.toLocalTime().withNano(0).toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED")).andExpect(jsonPath("$.eligibleCount").value(3));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(campaigns.findById(id).orElseThrow().getStatus()).isIn(CampaignStatus.RUNNING, CampaignStatus.COMPLETED));
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(campaigns.findById(id).orElseThrow().getStatus()).isEqualTo(CampaignStatus.COMPLETED));

        mvc.perform(get("/api/campaigns/{id}", id))
                .andExpect(jsonPath("$.stats.selected").value(3))
                .andExpect(jsonPath("$.stats.eligible").value(3))
                .andExpect(jsonPath("$.stats.accepted").value(2))
                .andExpect(jsonPath("$.stats.failed").value(1))
                .andExpect(jsonPath("$.stats.pending").value(0))
                .andExpect(jsonPath("$.stats.byChannel.WHATSAPP").value(2))
                .andExpect(jsonPath("$.stats.actualCostAmd").doesNotExist())   // unit costs unknown → null, never fabricated
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        mvc.perform(get("/api/campaigns/{id}/results", id))
                .andExpect(jsonPath("$.deliveries.content.length()").value(3))
                .andExpect(jsonPath("$.deliveries.content[?(@.customerId=='" + failing + "')].status").value("FAILED"));
        mvc.perform(get("/api/campaigns/{id}/recipients", id))
                .andExpect(jsonPath("$.content[?(@.customerId=='" + a + "')].deliveryStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.content[?(@.customerId=='" + b + "')].deliveredVia").value("WHATSAPP"));
    }

    @Test
    @DisplayName("Start now, pause, resume and cancel a running campaign")
    void pauseResumeCancel() throws Exception {
        String brand = "Pause" + seq();
        for (int i = 0; i < 3; i++) customerWithVehicle("P" + i, "+37494" + seq(), List.of("WHATSAPP"), brand, "M" + i, "ENGINE", "Oil");
        UUID id = UUID.fromString(json.readTree(mvc.perform(post("/api/campaigns").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(campaignRequest("Pause " + brand, Map.of("brand", brand))))).andReturn().getResponse().getContentAsString()).get("id").asText());

        mvc.perform(post("/api/campaigns/{id}/pause", id).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("campaign.invalidTransition"));
        mvc.perform(post("/api/campaigns/{id}/start", id).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(post("/api/campaigns/{id}/pause", id).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(post("/api/campaigns/{id}/resume", id).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(post("/api/campaigns/{id}/cancel", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/campaigns/{id}/resume", id).with(csrf())).andExpect(status().isConflict());
    }

    // ---- helpers ----

    private Map<String, Object> campaignRequest(String name, Map<String, Object> filters) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("description", "Test campaign");
        m.put("message", "Գարնանային զեղչ յուղի փոխարինման վրա։");
        m.put("includeServiceHistory", true);
        m.put("filters", filters);
        return m;
    }

    private UUID customerWithVehicle(String name, String phone, List<String> consents, String brand, String model, String oilType, String oilName) throws Exception {
        Map<String, Object> customer = new HashMap<>();
        customer.put("fullName", name);
        customer.put("phone", phone);
        Map<String, Object> service = new HashMap<>();
        service.put("serviceDate", LocalDate.now().minusDays(5).toString());
        service.put("mileageKm", 50_000);
        service.put("nextOilChangeMileageKm", 60_000);
        service.put("oilType", oilType);
        service.put("oilName", oilName);
        Map<String, Object> req = new HashMap<>();
        req.put("customer", customer);
        req.put("brand", brand);
        req.put("model", model);
        req.put("licensePlate", "11 CP " + (SEQ.incrementAndGet() % 1000));
        req.put("currentMileageKm", 50_000);
        req.put("initialService", service);
        MvcResult r = mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        UUID customerId = UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("customerId").asText());
        if (!consents.isEmpty()) {
            mvc.perform(put("/api/customers/{id}/consents", customerId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("consents", consents.stream().map(ch -> Map.of("channel", ch, "granted", true)).toList()))))
                    .andExpect(status().isOk());
        }
        return customerId;
    }

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger((int) (System.nanoTime() % 200000) + 700000);
    private static String seq() { return String.format("%06d", SEQ.incrementAndGet() % 1000000); }
}
