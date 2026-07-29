package com.ksh.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAttemptDeadlineReconciliationTest {

    @Test
    void persistentFailuresBackOffThenQuarantineAtBound() {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 29, 10, 0);
        PracticeAttempt attempt = new PracticeAttempt();
        attempt.setDeadlineAt(now.minusMinutes(1));

        LocalDateTime dueAt = now;
        for (int index = 0;
             index < PracticeAttempt.MAX_DEADLINE_RECONCILE_ATTEMPTS;
             index++) {
            attempt.recordDeadlineReconcileFailure(
                    "MalformedSnapshotException", dueAt);
            if (attempt.getDeadlineReconcileNextAt() != null) {
                dueAt = attempt.getDeadlineReconcileNextAt();
            }
        }

        assertThat(attempt.getDeadlineReconcileAttempts())
                .isEqualTo(
                        PracticeAttempt.MAX_DEADLINE_RECONCILE_ATTEMPTS);
        assertThat(attempt.getDeadlineReconcileErrorCode())
                .isEqualTo("MalformedSnapshotException");
        assertThat(attempt.getDeadlineReconcileNextAt()).isNull();
        assertThat(attempt.getDeadlineReconcileQuarantinedAt())
                .isEqualTo(dueAt);
        assertThat(attempt.isDeadlineReconcileQuarantined()).isTrue();
    }
}
