package com.aamotors.crm.auth;

import com.aamotors.crm.AbstractIntegrationTest;
import com.aamotors.crm.audit.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real HTTP stack (session cookie, CSRF cookie, JSON error bodies)
 * on a random port, exactly as the browser frontend will use it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIT extends AbstractIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired AdminUserRepository users;
    @Autowired AuditLogRepository auditLogs;
    @Autowired LoginRateLimiter rateLimiter;

    @Test
    @DisplayName("ADMIN is bootstrapped from environment, never hardcoded")
    void adminBootstrappedFromEnvironment() {
        AdminUser admin = users.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain(ADMIN_PASSWORD);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPreferredLanguage()).isEqualTo("hy");
    }

    @Test
    @DisplayName("Protected endpoints return 401 JSON without a session")
    void unauthenticatedIsRejected() throws Exception {
        Browser b = new Browser();
        ResponseEntity<String> r = b.get("/api/auth/me");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(r.getBody()).get("code").asText()).isEqualTo("auth.unauthenticated");
        assertThat(b.get("/api/settings").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /api/auth/csrf issues the XSRF-TOKEN cookie readable by the SPA")
    void csrfCookieIssued() {
        Browser b = new Browser();
        ResponseEntity<String> r = b.get("/api/auth/csrf");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(b.cookies).containsKey("XSRF-TOKEN");
        assertThat(r.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("XSRF-TOKEN").doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("ADMIN login succeeds, creates an HttpOnly session and an audit row")
    void loginSucceeds() throws Exception {
        resetLimiter();
        Browser b = new Browser();
        b.get("/api/auth/csrf");
        ResponseEntity<String> r = b.post("/api/auth/login", Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(r.getBody());
        assertThat(body.get("username").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(body.get("role").asText()).isEqualTo("ADMIN");
        assertThat(body.get("preferredLanguage").asText()).isEqualTo("hy");
        assertThat(body.has("mustChangePassword")).isTrue();
        assertThat(r.getHeaders().get(HttpHeaders.SET_COOKIE)).anySatisfy(c -> assertThat(c).contains("JSESSIONID").contains("HttpOnly"));

        ResponseEntity<String> me = b.get("/api/auth/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(me.getBody()).get("username").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("LOGIN_SUCCESS")).isNotEmpty();
    }

    @Test
    @DisplayName("Wrong password → 401 auth.badCredentials, lock after repeated failures")
    void badCredentialsThenRateLimited() throws Exception {
        resetLimiter();
        Browser b = new Browser();
        b.get("/api/auth/csrf");
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> r = b.post("/api/auth/login", Map.of("username", "locked-user", "password", "wrong-password-x"));
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(json.readTree(r.getBody()).get("code").asText()).isEqualTo("auth.badCredentials");
        }
        ResponseEntity<String> locked = b.post("/api/auth/login", Map.of("username", "locked-user", "password", "wrong-password-x"));
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        JsonNode body = json.readTree(locked.getBody());
        assertThat(body.get("code").asText()).isEqualTo("auth.rateLimited");
        assertThat(body.get("details").get("retryAfterSeconds").asLong()).isPositive();
        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("LOGIN_FAILED")).isNotEmpty();
        resetLimiter();
    }

    @Test
    @DisplayName("Mutating request without CSRF header is rejected")
    void csrfIsEnforced() {
        Browser b = new Browser();
        b.get("/api/auth/csrf");
        b.sendCsrfHeader = false;
        ResponseEntity<String> r = b.post("/api/auth/login", Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Language preference is stored on the admin profile and validated")
    void languagePreferenceStored() throws Exception {
        Browser b = loginAsAdmin();
        ResponseEntity<String> r = b.put("/api/auth/me/language", Map.of("language", "en"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(r.getBody()).get("preferredLanguage").asText()).isEqualTo("en");
        assertThat(users.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().getPreferredLanguage()).isEqualTo("en");

        ResponseEntity<String> bad = b.put("/api/auth/me/language", Map.of("language", "ru"));
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(bad.getBody()).get("code").asText()).isEqualTo("validation.failed");

        b.put("/api/auth/me/language", Map.of("language", "hy"));
    }

    @Test
    @DisplayName("Password change requires the current password and clears mustChangePassword")
    void changePassword() throws Exception {
        Browser b = loginAsAdmin();
        ResponseEntity<String> wrong = b.put("/api/auth/me/password", Map.of("currentPassword", "nope-nope-nope", "newPassword", "another-strong-pass"));
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(wrong.getBody()).get("code").asText()).isEqualTo("auth.passwordMismatch");

        ResponseEntity<String> ok = b.put("/api/auth/me/password", Map.of("currentPassword", ADMIN_PASSWORD, "newPassword", "another-strong-pass"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(users.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().isMustChangePassword()).isFalse();
        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("PASSWORD_CHANGED")).isNotEmpty();

        // revert so other tests keep working (password and the bootstrap flag)
        ResponseEntity<String> back = b.put("/api/auth/me/password", Map.of("currentPassword", "another-strong-pass", "newPassword", ADMIN_PASSWORD));
        assertThat(back.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        AdminUser admin = users.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();
        admin.setMustChangePassword(true);
        users.save(admin);
    }

    @Test
    @DisplayName("Logout invalidates the session")
    void logout() {
        Browser b = loginAsAdmin();
        assertThat(b.post("/api/auth/logout", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(b.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- helpers ----

    private void resetLimiter() {
        rateLimiter.recordSuccess("user:" + ADMIN_USERNAME);
        rateLimiter.recordSuccess("user:locked-user");
        rateLimiter.recordSuccess("ip:127.0.0.1");
        rateLimiter.recordSuccess("ip:0:0:0:0:0:0:0:1");
    }

    private Browser loginAsAdmin() {
        resetLimiter();
        Browser b = new Browser();
        b.get("/api/auth/csrf");
        ResponseEntity<String> r = b.post("/api/auth/login", Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return b;
    }

    /** Minimal cookie-jar client mimicking the SPA's fetch wrapper. */
    final class Browser {
        final Map<String, String> cookies = new HashMap<>();
        boolean sendCsrfHeader = true;

        ResponseEntity<String> get(String path) { return exchange(HttpMethod.GET, path, null); }
        ResponseEntity<String> post(String path, Object body) { return exchange(HttpMethod.POST, path, body); }
        ResponseEntity<String> put(String path, Object body) { return exchange(HttpMethod.PUT, path, body); }

        private ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!cookies.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
            }
            if (sendCsrfHeader && cookies.containsKey("XSRF-TOKEN") && method != HttpMethod.GET) {
                headers.set("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            }
            ResponseEntity<String> r = rest.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), String.class);
            List<String> setCookies = r.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (setCookies != null) {
                for (String c : setCookies) {
                    String[] nv = c.split(";", 2)[0].split("=", 2);
                    cookies.put(nv[0].trim(), nv.length > 1 ? nv[1].trim() : "");
                }
            }
            return r;
        }
    }
}
