package com.ksh.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptThrottleTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void blocksNormalizedAccountAfterBoundedFailuresAcrossAddresses() {
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(FIXED_CLOCK);

        for (int i = 0; i < LoginAttemptThrottle.MAX_ACCOUNT_FAILURES; i++) {
            assertThat(throttle.isBlocked(
                    " Student@Example.Test ", "192.0.2." + i)).isFalse();
            throttle.recordFailure(
                    i % 2 == 0 ? " Student@Example.Test " : "student@example.test",
                    "192.0.2." + i);
        }

        assertThat(throttle.isBlocked(
                "STUDENT@example.test", "198.51.100.7")).isTrue();
    }

    @Test
    void successfulLoginClearsOnlyThatAccountBucket() {
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(FIXED_CLOCK);
        for (int i = 0; i < LoginAttemptThrottle.MAX_ACCOUNT_FAILURES; i++) {
            throttle.recordFailure("student@example.test", "192.0.2." + i);
        }

        throttle.recordSuccess(" STUDENT@example.test ");

        assertThat(throttle.isBlocked(
                "student@example.test", "198.51.100.7")).isFalse();
    }

    @Test
    void blocksOneClientAddressAcrossDifferentAccounts() {
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(FIXED_CLOCK);
        for (int i = 0; i < LoginAttemptThrottle.MAX_IP_FAILURES; i++) {
            throttle.recordFailure("user-" + i + "@example.test", "192.0.2.9");
        }

        assertThat(throttle.isBlocked(
                "another@example.test", "192.0.2.9")).isTrue();
    }

    @Test
    void attackerControlledKeysCannotGrowStatePastHardCap() {
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(FIXED_CLOCK);
        for (int i = 0; i < 10_100; i++) {
            throttle.recordFailure(
                    "user-" + i + "@example.test", "198.51." + (i / 256) + "." + (i % 256));
        }

        assertThat(throttle.trackedKeyCount())
                .isLessThanOrEqualTo(LoginAttemptThrottle.MAX_KEYS);
    }
}
