package com.aamotors.crm.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Append-only audit trail. Joins the caller's transaction when one exists so a
 * rolled back business operation does not leave a misleading audit row.
 * Callers are responsible for masking sensitive values before passing details.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, String entityId, Map<String, Object> details) {
        record(currentActor(), action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String actor, String action, String entityType, String entityId, Map<String, Object> details) {
        repository.save(new AuditLog(Instant.now(clock), actor, action, entityType, entityId, details, currentClientIp()));
    }

    /** Writes even when the surrounding transaction rolls back (e.g. failed logins). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(String actor, String action, String entityType, String entityId, Map<String, Object> details) {
        repository.save(new AuditLog(Instant.now(clock), actor, action, entityType, entityId, details, currentClientIp()));
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() ? "system" : auth.getName();
    }

    private static String currentClientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }
        return null;
    }
}
