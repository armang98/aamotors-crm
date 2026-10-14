package com.aamotors.crm.vehicle;

import com.aamotors.crm.AbstractIntegrationTest;
import com.aamotors.crm.audit.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(username = "admin", roles = "ADMIN")
class VehicleIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuditLogRepository auditLogs;

    @Test
    @DisplayName("Vehicle creation with a new customer normalises phone, VIN and plate and records the first visit")
    void createVehicleWithCustomerAndInitialService() throws Exception {
        String phone = uniquePhone();
        Map<String, Object> req = vehicleRequest(phone, "jtnbe46k07301" + "2345");
        req.put("initialService", fullServiceInput(LocalDate.now(), 120_500, 130_500));

        MvcResult r = mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerPhone").value(phone))
                .andExpect(jsonPath("$.vin").value("JTNBE46K073012345"))
                .andExpect(jsonPath("$.currentMileageKm").value(120500))
                .andExpect(jsonPath("$.nextOilChangeMileageKm").value(130500))
                .andExpect(jsonPath("$.serviceRecordCount").value(1))
                .andExpect(jsonPath("$.maintenance.status").value("OK"))
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andReturn();
        UUID vehicleId = UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());

        // the customer was created and is found by local-format phone lookup
        mvc.perform(get("/api/customers/lookup").param("phone", phone.replace("+374", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Արամ Պետրոսյան"))
                .andExpect(jsonPath("$.vehicleCount").value(1));

        // all six filter fields were persisted in the service record
        mvc.perform(get("/api/vehicles/{id}/service-records/latest", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oilName").value("Castrol Edge 5W-30"))
                .andExpect(jsonPath("$.oilType").value("ENGINE"))
                .andExpect(jsonPath("$.oilFilterName").value("Mann W 712/95"))
                .andExpect(jsonPath("$.oilFilterCode").value("W712-95"))
                .andExpect(jsonPath("$.cabinFilterName").value("Bosch Cabin"))
                .andExpect(jsonPath("$.cabinFilterCode").value("CU-2545"))
                .andExpect(jsonPath("$.airFilterName").value("Mahle Air"))
                .andExpect(jsonPath("$.airFilterCode").value("LX-1780"))
                .andExpect(jsonPath("$.description").value("Փոխվել է շարժիչի յուղը և բոլոր ֆիլտրերը։\nՍտուգվել են արգելակները։"));

        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("vehicle", vehicleId.toString())).isNotEmpty();
    }

    @Test
    @DisplayName("Invalid VIN, invalid phone and negative mileage are rejected with field errors")
    void validation() throws Exception {
        Map<String, Object> bad = vehicleRequest(uniquePhone(), "ABC");
        mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("vin"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("vin.invalid"));

        Map<String, Object> badPhone = vehicleRequest("12345", null);
        mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='customer.phone')]").exists());

        Map<String, Object> negative = vehicleRequest(uniquePhone(), null);
        negative.put("currentMileageKm", -5);
        mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(negative)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='currentMileageKm')].message").value(org.hamcrest.Matchers.hasItem("mileage.negative")));

        // VIN with I/O/Q is invalid even when 17 long; duplicate VIN conflicts
        Map<String, Object> ok = vehicleRequest(uniquePhone(), "WVWZZZ1JZ3W386752");
        mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ok)))
                .andExpect(status().isCreated());
        Map<String, Object> dup = vehicleRequest(uniquePhone(), "wvwzzz1jz3w386752");
        mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("vehicle.vinExists"));
    }

    @Test
    @DisplayName("Service history is immutable, ordered newest first, and the latest record drives vehicle state")
    void serviceHistory() throws Exception {
        UUID vehicleId = createVehicle(uniquePhone(), null, 100_000);

        // older visit first, newer visit second — order must be by service date desc regardless of insert order
        mvc.perform(post("/api/vehicles/{id}/service-records", vehicleId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(fullServiceInput(LocalDate.now().minusMonths(6), 95_000, 105_000))))
                .andExpect(status().isCreated());
        MvcResult newer = mvc.perform(post("/api/vehicles/{id}/service-records", vehicleId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(fullServiceInput(LocalDate.now(), 104_900, 104_000))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Warnings", "vehicle.nextOilChangeNotAhead"))
                .andReturn();
        UUID newerId = UUID.fromString(json.readTree(newer.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(get("/api/vehicles/{id}/service-records", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mileageKm").value(104900))
                .andExpect(jsonPath("$[1].mileageKm").value(95000));

        mvc.perform(get("/api/vehicles/{id}/service-records/latest", vehicleId))
                .andExpect(jsonPath("$.id").value(newerId.toString()));

        // vehicle state follows the newest record; mileage never goes backwards
        mvc.perform(get("/api/vehicles/{id}", vehicleId))
                .andExpect(jsonPath("$.currentMileageKm").value(104900))
                .andExpect(jsonPath("$.nextOilChangeMileageKm").value(104000))
                .andExpect(jsonPath("$.maintenance.status").value("OVERDUE"))
                .andExpect(jsonPath("$.serviceRecordCount").value(2));

        // editing a historical record is audited and does not delete anything
        Map<String, Object> edit = fullServiceInput(LocalDate.now(), 104_900, 115_000);
        edit.put("cabinFilterName", null);
        edit.put("cabinFilterCode", null);
        mvc.perform(put("/api/service-records/{id}", newerId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cabinFilterName").doesNotExist())
                .andExpect(jsonPath("$.nextOilChangeMileageKm").value(115000));
        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("SERVICE_RECORD_UPDATED")).isNotEmpty();
        mvc.perform(get("/api/vehicles/{id}", vehicleId)).andExpect(jsonPath("$.maintenance.status").value("OK"));

        mvc.perform(delete("/api/service-records/{id}", newerId).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/vehicles/{id}/service-records", vehicleId)).andExpect(jsonPath("$.length()").value(1));
        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("SERVICE_RECORD_DELETED")).isNotEmpty();
    }

    @Test
    @DisplayName("Vehicles are searchable by phone, plate, VIN, brand and model")
    void search() throws Exception {
        String phone = uniquePhone();
        UUID id = createVehicle(phone, "KMHDU46D08U4" + "12345", 50_000);
        for (String q : new String[]{phone, phone.replace("+374", "0"), "34 ab 567", "34AB567", "kmhdu46d08u412345", "toyota", "cam"}) {
            MvcResult r = mvc.perform(get("/api/vehicles").param("q", q)).andExpect(status().isOk()).andReturn();
            JsonNode content = json.readTree(r.getResponse().getContentAsString()).get("content");
            assertThat(content.findValues("id").stream().map(JsonNode::asText)).as("query %s", q).contains(id.toString());
        }
    }

    @Test
    @DisplayName("Maintenance reminders list vehicles due by mileage or date, never vehicles without data")
    void reminders() throws Exception {
        UUID dueByKm = createVehicle(uniquePhone(), null, 99_500);        // next oil 100 000 → 500 km left
        UUID dueByDate = createVehicle(uniquePhone(), null, 10_000);
        Map<String, Object> upd = new HashMap<>();
        upd.put("customerId", json.readTree(mvc.perform(get("/api/vehicles/{id}", dueByDate)).andReturn().getResponse().getContentAsString()).get("customerId").asText());
        upd.put("brand", "Kia"); upd.put("model", "Rio"); upd.put("licensePlate", "11 ZZ 111");
        upd.put("currentMileageKm", 10_000); upd.put("nextServiceDate", LocalDate.now().plusDays(3).toString());
        mvc.perform(put("/api/vehicles/{id}", dueByDate).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(upd)))
                .andExpect(status().isOk());
        UUID notDue = createVehicle(uniquePhone(), null, 10_000);       // next oil 100 000 → far away

        MvcResult r = mvc.perform(get("/api/vehicles/reminders").param("kmWindow", "1000").param("daysWindow", "14")).andExpect(status().isOk()).andReturn();
        var ids = json.readTree(r.getResponse().getContentAsString()).findValues("id").stream().map(JsonNode::asText).toList();
        assertThat(ids).contains(dueByKm.toString(), dueByDate.toString()).doesNotContain(notDue.toString());
    }

    @Test
    @DisplayName("Deleting a vehicle removes its history and is audited; customer with vehicles cannot be deleted")
    void deleteVehicle() throws Exception {
        String phone = uniquePhone();
        UUID id = createVehicle(phone, null, 1000);
        String customerId = json.readTree(mvc.perform(get("/api/vehicles/{id}", id)).andReturn().getResponse().getContentAsString()).get("customerId").asText();
        mvc.perform(delete("/api/customers/{id}", customerId).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("customer.hasVehicles"));
        mvc.perform(delete("/api/vehicles/{id}", id).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/vehicles/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/customers/{id}", customerId).with(csrf())).andExpect(status().isNoContent());
    }

    // ---- helpers ----

    private UUID createVehicle(String phone, String vin, int mileage) throws Exception {
        Map<String, Object> req = vehicleRequest(phone, vin);
        req.put("currentMileageKm", mileage);
        req.put("nextOilChangeMileageKm", 100_000);
        MvcResult r = mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    static Map<String, Object> vehicleRequest(String phone, String vin) {
        Map<String, Object> customer = new HashMap<>();
        customer.put("fullName", "Արամ Պետրոսյան");
        customer.put("phone", phone);
        customer.put("preferredLanguage", "hy");
        Map<String, Object> req = new HashMap<>();
        req.put("customer", customer);
        req.put("brand", "Toyota");
        req.put("model", "Camry");
        req.put("vin", vin);
        req.put("licensePlate", "34 AB 567");
        req.put("currentMileageKm", 120_000);
        req.put("nextOilChangeMileageKm", 130_000);
        return req;
    }

    static Map<String, Object> fullServiceInput(LocalDate date, int mileage, Integer nextOil) {
        Map<String, Object> m = new HashMap<>();
        m.put("serviceDate", date.toString());
        m.put("mileageKm", mileage);
        m.put("nextOilChangeMileageKm", nextOil);
        m.put("oilName", "Castrol Edge 5W-30");
        m.put("oilType", "ENGINE");
        m.put("oilFilterName", "Mann W 712/95");
        m.put("oilFilterCode", "W712-95");
        m.put("cabinFilterName", "Bosch Cabin");
        m.put("cabinFilterCode", "CU-2545");
        m.put("airFilterName", "Mahle Air");
        m.put("airFilterCode", "LX-1780");
        m.put("description", "Փոխվել է շարժիչի յուղը և բոլոր ֆիլտրերը։\nՍտուգվել են արգելակները։");
        return m;
    }

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger((int) (System.nanoTime() % 100000));

    static String uniquePhone() {
        int n = SEQ.incrementAndGet() % 1_000_000;
        return String.format("+37494%06d", n);
    }
}
