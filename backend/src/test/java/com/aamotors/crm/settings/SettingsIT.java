package com.aamotors.crm.settings;

import com.aamotors.crm.AbstractIntegrationTest;
import com.aamotors.crm.audit.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettingsIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuditLogRepository auditLogs;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Seeded A&A Motors details are returned and secrets are never exposed")
    void seededSettings() throws Exception {
        MvcResult r = mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("A&A Motors"))
                .andExpect(jsonPath("$.address").value("52/2 Vazgen Sargsyan Street, Gyumri, Armenia"))
                .andExpect(jsonPath("$.primaryPhone").value("+37494889881"))
                .andExpect(jsonPath("$.secondaryPhone").value("+37493099473"))
                .andExpect(jsonPath("$.defaultLanguage").value("hy"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Yerevan"))
                .andExpect(jsonPath("$.currency").value("AMD"))
                .andExpect(jsonPath("$.integrations.sms.provider").value("MOCK"))
                .andExpect(jsonPath("$.integrations.whatsapp.configured").value(false))
                .andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("secret");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Settings update normalises phones to E.164 and writes an audit row")
    void updateSettings() throws Exception {
        Map<String, Object> req = baseRequest();
        req.put("primaryPhone", "094 88 98 81");          // local Armenian format
        req.put("secondaryPhone", "+374 (93) 09-94-73");
        req.put("workingHours", "Mon–Fri 09:00–18:00");

        mvc.perform(put("/api/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryPhone").value("+37494889881"))
                .andExpect(jsonPath("$.secondaryPhone").value("+37493099473"))
                .andExpect(jsonPath("$.workingHours").value("Mon–Fri 09:00–18:00"));

        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("SETTINGS_UPDATED")).isNotEmpty();
        // audit details must mask phone numbers
        var details = auditLogs.findByActionOrderByOccurredAtDesc("SETTINGS_UPDATED").get(0).getDetails();
        assertThat(details.toString()).doesNotContain("+37494889881").contains("+374*****881");

        // restore seed working hours
        req.put("workingHours", "Mon–Sat 09:00–19:00");
        mvc.perform(put("/api/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Invalid phone, language or time zone are rejected with field errors")
    void validation() throws Exception {
        Map<String, Object> req = baseRequest();
        req.put("primaryPhone", "12345");
        req.put("defaultLanguage", "ru");
        mvc.perform(put("/api/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='primaryPhone')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='defaultLanguage')]").exists());

        req = baseRequest();
        req.put("timeZone", "Mars/Olympus");
        mvc.perform(put("/api/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("settings.invalidTimeZone"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Logo upload accepts images, serves them publicly, rejects other types, and can be removed")
    void logoLifecycle() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MvcResult up = mvc.perform(multipart("/api/settings/logo").file(new MockMultipartFile("file", "logo.png", "image/png", png)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").isString())
                .andReturn();
        String logoUrl = json.readTree(up.getResponse().getContentAsString()).get("logoUrl").asText();
        assertThat(logoUrl).startsWith("/api/public/media/logo/").doesNotContain("logo.png");

        mvc.perform(get(logoUrl).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mvc.perform(multipart("/api/settings/logo").file(new MockMultipartFile("file", "evil.html", "text/html", "<script>".getBytes())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("upload.invalidType"));

        mvc.perform(delete("/api/settings/logo").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
        mvc.perform(get(logoUrl).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Health endpoint is public")
    void health() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private static Map<String, Object> baseRequest() {
        Map<String, Object> m = new HashMap<>();
        m.put("businessName", "A&A Motors");
        m.put("address", "52/2 Vazgen Sargsyan Street, Gyumri, Armenia");
        m.put("primaryPhone", "+37494889881");
        m.put("secondaryPhone", "+37493099473");
        m.put("workingHours", "Mon–Sat 09:00–19:00");
        m.put("defaultLanguage", "hy");
        m.put("timeZone", "Asia/Yerevan");
        m.put("currency", "AMD");
        return m;
    }
}
