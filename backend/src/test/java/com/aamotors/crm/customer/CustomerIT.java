package com.aamotors.crm.customer;

import com.aamotors.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(username = "admin", roles = "ADMIN")
class CustomerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("Customer creation normalises the phone, rejects duplicates, and consents are tracked per channel")
    void lifecycle() throws Exception {
        String local = "093 77 66 55";
        MvcResult r = mvc.perform(post("/api/customers").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Անի Սարգսյան", "phone", local, "preferredLanguage", "en"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+37493776655"))
                .andExpect(jsonPath("$.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.consents").isEmpty())
                .andReturn();
        String id = json.readTree(r.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/customers").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Other", "phone", "+37493776655"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("customer.phoneExists"));

        mvc.perform(put("/api/customers/{id}/consents", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("consents", List.of(
                                Map.of("channel", "WHATSAPP", "granted", true, "source", "IN_PERSON"),
                                Map.of("channel", "SMS", "granted", false))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[?(@.channel=='WHATSAPP')].granted").value(true))
                .andExpect(jsonPath("$.consents[?(@.channel=='WHATSAPP')].grantedAt").isNotEmpty())
                .andExpect(jsonPath("$.consents[?(@.channel=='SMS')].granted").value(false));

        mvc.perform(put("/api/customers/{id}/consents", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("consents", List.of(Map.of("channel", "WHATSAPP", "granted", false))))))
                .andExpect(jsonPath("$.consents[?(@.channel=='WHATSAPP')].revokedAt").isNotEmpty());

        mvc.perform(get("/api/customers").param("q", "սարգս"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + id + "')]").exists());
        mvc.perform(get("/api/customers").param("q", "0937766"))
                .andExpect(jsonPath("$.content[?(@.id=='" + id + "')]").exists());

        mvc.perform(delete("/api/customers/{id}", id).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/{id}", id)).andExpect(status().isNotFound());
    }
}
