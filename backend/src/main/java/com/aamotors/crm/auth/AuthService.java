package com.aamotors.crm.auth;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthService(AuthenticationManager authenticationManager, AdminUserRepository users, PasswordEncoder encoder,
                       LoginRateLimiter rateLimiter, AuditService audit, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AdminUser login(AuthDtos.LoginRequest request, HttpServletRequest http, jakarta.servlet.http.HttpServletResponse response) {
        String ip = clientIp(http);
        String userKey = "user:" + request.username();
        String ipKey = "ip:" + ip;

        rateLimiter.lockedFor(userKey).or(() -> rateLimiter.lockedFor(ipKey)).ifPresent(seconds -> {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "auth.rateLimited",
                    "Too many failed login attempts", Map.of("retryAfterSeconds", seconds));
        });

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            rateLimiter.recordFailure(userKey);
            rateLimiter.recordFailure(ipKey);
            audit.recordIndependently(request.username(), "LOGIN_FAILED", "user", null, Map.of("reason", "badCredentials"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.badCredentials", "Invalid username or password");
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(userKey);
            rateLimiter.recordFailure(ipKey);
            audit.recordIndependently(request.username(), "LOGIN_FAILED", "user", null, Map.of("reason", e.getClass().getSimpleName()));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.badCredentials", "Invalid username or password");
        }

        rateLimiter.recordSuccess(userKey);
        rateLimiter.recordSuccess(ipKey);

        // Rotate the session to prevent fixation, then persist the security context.
        var oldSession = http.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        http.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, http, response);

        AdminUser user = users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        user.setLastLoginAt(Instant.now(clock));
        audit.record(user.getUsername(), "LOGIN_SUCCESS", "user", user.getId().toString(), null);
        return user;
    }

    public void logout(HttpServletRequest http) {
        var session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public AdminUser currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.unauthenticated", "Authentication required");
        }
        return users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.unauthenticated", "Authentication required"));
    }

    @Transactional
    public AdminUser updateLanguage(Authentication authentication, String language) {
        AdminUser user = currentUser(authentication);
        user.setPreferredLanguage(language);
        return user;
    }

    @Transactional
    public void changePassword(Authentication authentication, AuthDtos.ChangePasswordRequest request) {
        AdminUser user = currentUser(authentication);
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("auth.passwordMismatch", "Current password is incorrect");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("auth.passwordUnchanged", "New password must differ from the current one");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        audit.record("PASSWORD_CHANGED", "user", user.getId().toString(), null);
    }

    private static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
