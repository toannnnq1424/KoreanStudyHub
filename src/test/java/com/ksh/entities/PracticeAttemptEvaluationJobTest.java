package com.ksh.entities;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeAttemptEvaluationJobTest {

    @Test
    void leaseAndRetryLifecycleIsBoundedAndFenced() {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 29, 9, 0);
        PracticeAttemptEvaluationJob job =
                new PracticeAttemptEvaluationJob();
        ReflectionTestUtils.setField(
                job, "jobStatus",
                PracticeAttemptEvaluationJob.STATUS_QUEUED);
        ReflectionTestUtils.setField(job, "attemptCount", 0);
        ReflectionTestUtils.setField(job, "maxAttempts", 2);
        ReflectionTestUtils.setField(
                job, "nextAttemptAt", now);
        ReflectionTestUtils.setField(
                job, "expiresAt", now.plusMinutes(30));

        assertThat(job.canClaim(now)).isTrue();
        job.claim("worker:one", now, now.plusMinutes(15));
        assertThat(job.ownsLease("worker:one")).isTrue();
        assertThat(job.ownsActiveLease(
                "worker:one", now.plusMinutes(14))).isTrue();
        assertThat(job.ownsActiveLease(
                "worker:one", now.plusMinutes(15))).isFalse();
        assertThat(job.ownsLease("worker:two")).isFalse();
        assertThat(job.getAttemptCount()).isEqualTo(1);
        job.renewLease(
                "worker:one",
                now.plusMinutes(14),
                now.plusMinutes(20));
        assertThat(job.ownsActiveLease(
                "worker:one", now.plusMinutes(19))).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                job.renewLease(
                        "worker:stale",
                        now.plusMinutes(19),
                        now.plusMinutes(21)))
                .isInstanceOf(IllegalStateException.class);

        job.markFailure(
                "TEMPORARY",
                "retry",
                true,
                now.plusSeconds(15),
                now.plusSeconds(1));
        assertThat(job.getJobStatus())
                .isEqualTo(
                        PracticeAttemptEvaluationJob.STATUS_RETRY_WAIT);

        job.claim(
                "worker:two",
                now.plusSeconds(15),
                now.plusMinutes(16));
        job.markFailure(
                "TEMPORARY",
                "exhausted",
                true,
                now.plusMinutes(1),
                now.plusSeconds(16));
        assertThat(job.getJobStatus())
                .isEqualTo(
                        PracticeAttemptEvaluationJob.STATUS_FAILED);
        assertThat(job.isRetryable()).isFalse();
        assertThat(job.canClaim(now.plusSeconds(17))).isFalse();
    }

    @Test
    void expiredJobCannotBeClaimed() {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 29, 9, 0);
        PracticeAttemptEvaluationJob job =
                new PracticeAttemptEvaluationJob();
        ReflectionTestUtils.setField(
                job, "jobStatus",
                PracticeAttemptEvaluationJob.STATUS_QUEUED);
        ReflectionTestUtils.setField(job, "attemptCount", 0);
        ReflectionTestUtils.setField(job, "maxAttempts", 3);
        ReflectionTestUtils.setField(
                job, "expiresAt", now);

        assertThat(job.expired(now)).isTrue();
        assertThat(job.canClaim(now)).isFalse();
    }

    @Test
    void manualRetryQuotaAndContractIdentityAreBounded() {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 29, 9, 0);
        PracticeAttemptEvaluationJob job =
                new PracticeAttemptEvaluationJob();
        ReflectionTestUtils.setField(job, "attemptCount", 0);
        ReflectionTestUtils.setField(job, "maxAttempts", 3);
        ReflectionTestUtils.setField(job, "manualRetryCount", 0);

        job.requestManualRetry(
                PracticeAttemptEvaluationJob.OPERATION_FULL_REEVALUATE,
                null,
                "a".repeat(64),
                "ksh-writing-evaluation-v1|model|prompt|rubric|schema",
                7L,
                now,
                now.plusMinutes(30));
        job.markTerminal(
                PracticeAttemptEvaluationJob.STATUS_SUCCEEDED,
                "{}",
                null,
                false,
                now.plusSeconds(1));
        job.requestManualRetry(
                PracticeAttemptEvaluationJob.OPERATION_FULL_REEVALUATE,
                null,
                "b".repeat(64),
                "ksh-writing-evaluation-v1|model|prompt|rubric|schema",
                7L,
                now.plusMinutes(2),
                now.plusMinutes(32));

        assertThat(job.getManualRetryCount()).isEqualTo(2);
        assertThat(job.manualRetryLimitReached()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                job.requestManualRetry(
                        PracticeAttemptEvaluationJob
                                .OPERATION_FULL_REEVALUATE,
                        null,
                        "c".repeat(64),
                        "ksh-writing-evaluation-v1|model|prompt|rubric|schema",
                        7L,
                        now.plusMinutes(4),
                        now.plusMinutes(34)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void componentCompleteContractIdentityFitsPersistenceBoundary() {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 30, 5, 47);
        PracticeAttemptEvaluationJob job =
                new PracticeAttemptEvaluationJob();
        ReflectionTestUtils.setField(job, "attemptCount", 0);
        ReflectionTestUtils.setField(job, "maxAttempts", 3);

        String maximumIdentity = "i".repeat(
                PracticeAttemptEvaluationJob
                        .MAX_EVALUATION_CONTRACT_IDENTITY_LENGTH);
        job.request(
                PracticeAttemptEvaluationJob.OPERATION_SUBMIT,
                null,
                "a".repeat(64),
                maximumIdentity,
                7L,
                now,
                now.plusMinutes(30));

        assertThat(job.getEvaluationContractIdentity())
                .isEqualTo(maximumIdentity);
        assertThatThrownBy(() -> job.request(
                PracticeAttemptEvaluationJob.OPERATION_SUBMIT,
                null,
                "b".repeat(64),
                maximumIdentity + "x",
                7L,
                now,
                now.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
