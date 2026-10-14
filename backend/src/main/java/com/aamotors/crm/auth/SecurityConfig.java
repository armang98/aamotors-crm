package com.aamotors.crm.auth;

import com.aamotors.crm.common.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AdminUserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return provider::authenticate;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(csrfHandler)
                // Webhooks are authenticated by provider signatures, not by a browser session.
                .ignoringRequestMatchers("/api/webhooks/**"))
            .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().migrateSession())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> writeError(mapper, res, 401, "auth.unauthenticated", "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> writeError(mapper, res, 403, "auth.forbidden", "Access denied")))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/csrf").permitAll()
                .requestMatchers("/api/public/**", "/api/webhooks/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").hasRole("ADMIN")
                .anyRequest().denyAll());

        return http.build();
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse res, int status, String code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ApiError.of(status, code, message));
    }
}
