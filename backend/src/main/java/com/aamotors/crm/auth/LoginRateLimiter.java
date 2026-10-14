package com.aamotors.crm.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login protection: after {@code maxFailures} failed attempts for a key
 * (username or client IP) within the lock window, further attempts are rejected
 * until the window expires. Adequate for a single-instance monolith.
 */
@Component
public class LoginRateLimiter {

    private record Entry(int failures, Instant firstFailureAt, Instant lockedUntil) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public LoginRateLimiter(LoginSecurityProperties props, Clock clock) {
        this(props.maxFailures(), props.lockDuration(), clock);
    }

    LoginRateLimiter(int maxFailures, Duration lockDuration, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    /** @return seconds remaining in the lock when the key is currently locked. */
    public Optional<Long> lockedFor(String key) {
        Entry e = entries.get(normalize(key));
        if (e == null || e.lockedUntil() == null) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        if (now.isAfter(e.lockedUntil())) {
            entries.remove(normalize(key));
            return Optional.empty();
        }
        return Optional.of(Math.max(1, Duration.between(now, e.lockedUntil()).toSeconds()));
    }

    public void recordFailure(String key) {
        Instant now = Instant.now(clock);
        entries.compute(normalize(key), (k, e) -> {
            if (e == null || now.isAfter(e.firstFailureAt().plus(lockDuration))) {
                return new Entry(1, now, null);
            }
            int failures = e.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockDuration) : e.lockedUntil();
            return new Entry(failures, e.firstFailureAt(), lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        entries.remove(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }
}
