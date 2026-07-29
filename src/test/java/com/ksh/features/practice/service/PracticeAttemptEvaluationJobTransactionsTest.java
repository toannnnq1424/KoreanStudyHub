package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeAttemptEvaluationJob;
import com.ksh.features.practice.repository.PracticeAttemptEvaluationJobRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAttemptEvaluationJobTransactionsTest {

    @Test
    void truncatedOutputAtRetryExhaustionIsTerminalAndNeverFabricatesScore()
            throws Exception {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 29, 14, 0);
        PracticeAttempt attempt = new PracticeAttempt(
                7L,
                1L,
                2L,
                "WRITING",
                3L);
        ReflectionTestUtils.setField(attempt, "id", 10L);
        attempt.markSubmittedForAnalysis(
                BigDecimal.valueOf(30),
                "{\"answer\":\"locked\"}",
                now.minusMinutes(2));
        attempt.markAnalysisProcessing();

        PracticeAttemptEvaluationJob job = newJob();
        ReflectionTestUtils.setField(job, "id", 99L);
        ReflectionTestUtils.setField(job, "attemptId", 10L);
        ReflectionTestUtils.setField(
                job,
                "operation",
                PracticeAttemptEvaluationJob.OPERATION_SUBMIT);
        ReflectionTestUtils.setField(
                job,
                "inputFingerprint",
                "a".repeat(64));
        ReflectionTestUtils.setField(
                job,
                "evaluationContractIdentity",
                "ksh-writing-evaluation-v2");
        ReflectionTestUtils.setField(job, "requestedBy", 7L);
        ReflectionTestUtils.setField(
                job,
                "jobStatus",
                PracticeAttemptEvaluationJob.STATUS_PROCESSING);
        ReflectionTestUtils.setField(job, "attemptCount", 3);
        ReflectionTestUtils.setField(job, "maxAttempts", 3);
        ReflectionTestUtils.setField(job, "leaseOwner", "worker:last");
        ReflectionTestUtils.setField(
                job,
                "leaseExpiresAt",
                now.plusMinutes(1));
        ReflectionTestUtils.setField(
                job,
                "expiresAt",
                now.plusMinutes(10));

        PracticeAttemptEvaluationJobRepository jobRepository =
                mock(PracticeAttemptEvaluationJobRepository.class);
        PracticeAttemptRepository attemptRepository =
                mock(PracticeAttemptRepository.class);
        when(jobRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findByIdAndUserIdForUpdate(10L, 7L))
                .thenReturn(Optional.of(attempt));
        PracticeAttemptEvaluationJobTransactions transactions =
                new PracticeAttemptEvaluationJobTransactions(
                        jobRepository,
                        attemptRepository);
        PracticeAttemptEvaluationJobTransactions.ClaimedEvaluationJob claim =
                new PracticeAttemptEvaluationJobTransactions
                        .ClaimedEvaluationJob(
                        99L,
                        10L,
                        7L,
                        PracticeAttemptEvaluationJob.OPERATION_SUBMIT,
                        null,
                        "a".repeat(64),
                        "ksh-writing-evaluation-v2",
                        now.plusMinutes(10),
                        "worker:last");
        PracticeAttemptEvaluationOutcome outcome =
                new PracticeAttemptEvaluationOutcome(
                        PracticeAttemptEvaluationOutcome.FAILED,
                        "a".repeat(64),
                        null,
                        BigDecimal.valueOf(30),
                        "{\"answer\":\"locked\"}",
                        """
                                {
                                  "evaluation_reason":"PROVIDER_OUTPUT_TRUNCATED",
                                  "score_available":false
                                }
                                """,
                        "KSH_WRITING_ASYNC",
                        "PROVIDER_OUTPUT_TRUNCATED",
                        true);

        assertThat(transactions.complete(
                claim,
                outcome,
                "{\"terminalStatus\":\"FAILED\"}",
                now)).isTrue();

        assertThat(job.getJobStatus())
                .isEqualTo(PracticeAttemptEvaluationJob.STATUS_FAILED);
        assertThat(job.isRetryable()).isFalse();
        assertThat(job.getNextAttemptAt()).isNull();
        assertThat(job.getErrorCode())
                .isEqualTo("PROVIDER_OUTPUT_TRUNCATED");
        assertThat(attempt.getAnalysisStatus())
                .isEqualTo(PracticeAttempt.ANALYSIS_FAILED);
        assertThat(attempt.getAnalysisErrorCode())
                .isEqualTo("PROVIDER_OUTPUT_TRUNCATED");
        assertThat(attempt.getScore()).isNull();
        assertThat(attempt.getAiFeedbackJson()).isNull();
        verify(jobRepository).save(job);
        verify(attemptRepository).save(attempt);
    }

    private static PracticeAttemptEvaluationJob newJob()
            throws Exception {
        Constructor<PracticeAttemptEvaluationJob> constructor =
                PracticeAttemptEvaluationJob.class
                        .getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
