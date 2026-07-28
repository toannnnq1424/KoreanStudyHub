package com.ksh.features.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetRequestThrottleTest {

    @Test
    void normalizesEmailAndLimitsByEmailWithoutDisclosingOutcome() {
        PasswordResetRequestThrottle throttle = new PasswordResetRequestThrottle(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC));

        assertThat(throttle.allow(" Student@Example.Test ", "192.0.2.1")).isTrue();
        assertThat(throttle.allow("student@example.test", "192.0.2.2")).isTrue();
        assertThat(throttle.allow("STUDENT@example.test", "192.0.2.3")).isTrue();
        assertThat(throttle.allow("student@example.test", "192.0.2.4")).isFalse();
    }

    @Test
    void limitsOneClientIpAcrossDifferentAddresses() {
        PasswordResetRequestThrottle throttle = new PasswordResetRequestThrottle(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC));

        assertThat(throttle.allow("a@example.test", "192.0.2.9")).isTrue();
        assertThat(throttle.allow("b@example.test", "192.0.2.9")).isTrue();
        assertThat(throttle.allow("c@example.test", "192.0.2.9")).isTrue();
        assertThat(throttle.allow("d@example.test", "192.0.2.9")).isFalse();
    }

    @Test
    void attackerControlledKeysCannotGrowStatePastHardCap() {
        PasswordResetRequestThrottle throttle = new PasswordResetRequestThrottle(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC));

        for (int i = 0; i < 5_100; i++) {
            throttle.allow("user-" + i + "@example.test", "198.51.100." + i);
        }

        assertThat(throttle.trackedKeyCount()).isLessThanOrEqualTo(
                PasswordResetRequestThrottle.MAX_KEYS);
    }
}
