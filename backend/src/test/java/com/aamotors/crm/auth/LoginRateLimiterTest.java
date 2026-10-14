package com.aamotors.crm.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void locksAfterMaxFailuresAndUnlocksWhenWindowExpires() {
        var start = Instant.parse("2026-10-12T10:00:00Z");
        var clock = new MutableClock(start);
        var limiter = new LoginRateLimiter(3, Duration.ofMinutes(15), clock);

        limiter.recordFailure("admin");
        limiter.recordFailure("admin");
        assertThat(limiter.lockedFor("admin")).isEmpty();

        limiter.recordFailure("admin");
        assertThat(limiter.lockedFor("admin")).isPresent().get().satisfies(s -> assertThat(s).isBetween(1L, 900L));

        clock.advance(Duration.ofMinutes(16));
        assertThat(limiter.lockedFor("admin")).isEmpty();
    }

    @Test
    void successClearsFailures() {
        var limiter = new LoginRateLimiter(2, Duration.ofMinutes(15), Clock.systemUTC());
        limiter.recordFailure("Admin ");
        limiter.recordSuccess("admin");
        limiter.recordFailure("admin");
        assertThat(limiter.lockedFor("ADMIN")).isEmpty();
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
