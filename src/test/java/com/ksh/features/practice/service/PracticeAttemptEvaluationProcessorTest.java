package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.repository.PracticeAttemptEvaluationJobRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAttemptEvaluationProcessorTest {

    @Test
    void processDueClaimsWithPersistableOwnerAndCompletesOnBoundedExecutor()
            throws Exception {
        PracticeAttemptEvaluationJobRepository repository =
                mock(PracticeAttemptEvaluationJobRepository.class);
        PracticeAttemptEvaluationJobTransactions transactions =
                mock(PracticeAttemptEvaluationJobTransactions.class);
        PracticeService practiceService = mock(PracticeService.class);
        ExecutorService evaluationExecutor =
                Executors.newSingleThreadExecutor();
        ScheduledExecutorService leaseScheduler =
                Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService timeoutScheduler =
                Executors.newSingleThreadScheduledExecutor();
        PracticeAttemptEvaluationProcessor processor =
                new PracticeAttemptEvaluationProcessor(
                        repository,
                        transactions,
                        practiceService,
                        new ObjectMapper(),
                        evaluationExecutor,
                        leaseScheduler,
                        timeoutScheduler,
                        1);
        try {
            LocalDateTime expiresAt =
                    LocalDateTime.now().plusMinutes(30);
            when(repository.findClaimableIds(
                    any(LocalDateTime.class),
                    any(Pageable.class)))
                    .thenReturn(List.of(42L));
            when(transactions.claim(
                    eq(42L),
                    anyString(),
                    any(LocalDateTime.class)))
                    .thenAnswer(invocation -> {
                        String owner = invocation.getArgument(1);
                        return Optional.of(
                                new PracticeAttemptEvaluationJobTransactions
                                        .ClaimedEvaluationJob(
                                        42L,
                                        101L,
                                        7L,
                                        "SUBMIT",
                                        null,
                                        "a".repeat(64),
                                        "ksh-writing-evaluation-v1",
                                        expiresAt,
                                        owner));
                    });
            PracticeAttemptEvaluationOutcome outcome =
                    new PracticeAttemptEvaluationOutcome(
                            PracticeAttemptEvaluationOutcome.SUCCEEDED,
                            "a".repeat(64),
                            BigDecimal.TEN,
                            BigDecimal.TEN,
                            "{}",
                            "{}",
                            "TEST",
                            null,
                            false);
            when(practiceService.evaluateClaimedAttempt(any()))
                    .thenReturn(outcome);
            when(transactions.complete(
                    any(),
                    eq(outcome),
                    anyString(),
                    any(LocalDateTime.class)))
                    .thenReturn(true);

            assertThat(processor.processDue(1)).isEqualTo(1);

            verify(transactions, timeout(2_000)).complete(
                    any(),
                    eq(outcome),
                    anyString(),
                    any(LocalDateTime.class));
            ArgumentCaptor<String> owner =
                    ArgumentCaptor.forClass(String.class);
            verify(transactions).claim(
                    eq(42L),
                    owner.capture(),
                    any(LocalDateTime.class));
            assertThat(owner.getValue())
                    .startsWith("pae-")
                    .hasSizeLessThanOrEqualTo(100);
        } finally {
            evaluationExecutor.shutdownNow();
            leaseScheduler.shutdownNow();
            timeoutScheduler.shutdownNow();
        }
    }

    @Test
    void executionTimeoutFencesJobWithoutWaitingForProviderReturn()
            throws Exception {
        PracticeAttemptEvaluationJobRepository repository =
                mock(PracticeAttemptEvaluationJobRepository.class);
        PracticeAttemptEvaluationJobTransactions transactions =
                mock(PracticeAttemptEvaluationJobTransactions.class);
        PracticeService practiceService = mock(PracticeService.class);
        ExecutorService evaluationExecutor =
                Executors.newSingleThreadExecutor();
        ScheduledExecutorService leaseScheduler =
                Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService timeoutScheduler =
                Executors.newSingleThreadScheduledExecutor();
        PracticeAttemptEvaluationProcessor processor =
                new PracticeAttemptEvaluationProcessor(
                        repository,
                        transactions,
                        practiceService,
                        new ObjectMapper(),
                        evaluationExecutor,
                        leaseScheduler,
                        timeoutScheduler,
                        1,
                        Duration.ofMinutes(1),
                        Duration.ofMillis(50));
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        try {
            LocalDateTime expiresAt =
                    LocalDateTime.now().plusMinutes(30);
            when(repository.findClaimableIds(
                    any(LocalDateTime.class),
                    any(Pageable.class)))
                    .thenReturn(List.of(91L));
            when(transactions.claim(
                    eq(91L),
                    anyString(),
                    any(LocalDateTime.class)))
                    .thenAnswer(invocation -> Optional.of(
                            new PracticeAttemptEvaluationJobTransactions
                                    .ClaimedEvaluationJob(
                                    91L,
                                    901L,
                                    7L,
                                    "SUBMIT",
                                    null,
                                    "b".repeat(64),
                                    "ksh-writing-evaluation-v1",
                                    expiresAt,
                                    invocation.getArgument(1))));
            when(practiceService.evaluateClaimedAttempt(any()))
                    .thenAnswer(invocation -> {
                        providerStarted.countDown();
                        boolean released = false;
                        while (!released) {
                            try {
                                released = releaseProvider.await(
                                        10, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException ignored) {
                                // Simulate a transport that ignores interruption.
                            }
                        }
                        return new PracticeAttemptEvaluationOutcome(
                                PracticeAttemptEvaluationOutcome.SUCCEEDED,
                                "b".repeat(64),
                                BigDecimal.TEN,
                                BigDecimal.TEN,
                                "{}",
                                "{}",
                                "TEST",
                                null,
                                false);
                    });
            when(transactions.fail(
                    any(),
                    eq("EVALUATION_EXECUTION_TIMEOUT"),
                    anyString(),
                    eq(false),
                    any(LocalDateTime.class)))
                    .thenReturn(true);

            assertThat(processor.processDue(1)).isEqualTo(1);
            assertThat(providerStarted.await(
                    1, TimeUnit.SECONDS)).isTrue();

            verify(transactions, timeout(2_000)).fail(
                    any(),
                    eq("EVALUATION_EXECUTION_TIMEOUT"),
                    anyString(),
                    eq(false),
                    any(LocalDateTime.class));
            verify(transactions,
                    org.mockito.Mockito.never()).complete(
                    any(), any(), anyString(), any(LocalDateTime.class));
        } finally {
            releaseProvider.countDown();
            evaluationExecutor.shutdownNow();
            leaseScheduler.shutdownNow();
            timeoutScheduler.shutdownNow();
        }
    }

    @Test
    void gracefulExecutorInterruptionLeavesLeaseForRestartReclaim()
            throws Exception {
        PracticeAttemptEvaluationJobRepository repository =
                mock(PracticeAttemptEvaluationJobRepository.class);
        PracticeAttemptEvaluationJobTransactions transactions =
                mock(PracticeAttemptEvaluationJobTransactions.class);
        PracticeService practiceService = mock(PracticeService.class);
        PracticeAttemptEvaluationProcessor processor =
                new PracticeAttemptEvaluationProcessor(
                        repository,
                        transactions,
                        practiceService,
                        new ObjectMapper());
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch providerReturned = new CountDownLatch(1);
        try {
            LocalDateTime expiresAt =
                    LocalDateTime.now().plusMinutes(30);
            when(repository.findClaimableIds(
                    any(LocalDateTime.class),
                    any(Pageable.class)))
                    .thenReturn(List.of(92L));
            when(transactions.claim(
                    eq(92L),
                    anyString(),
                    any(LocalDateTime.class)))
                    .thenAnswer(invocation -> Optional.of(
                            new PracticeAttemptEvaluationJobTransactions
                                    .ClaimedEvaluationJob(
                                    92L,
                                    902L,
                                    7L,
                                    "SUBMIT",
                                    null,
                                    "c".repeat(64),
                                    "ksh-writing-evaluation-v2",
                                    expiresAt,
                                    invocation.getArgument(1))));
            when(practiceService.evaluateClaimedAttempt(any()))
                    .thenAnswer(invocation -> {
                        providerStarted.countDown();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException ignored) {
                            // Simulate a transport that consumes the shutdown
                            // interrupt and still returns an outcome.
                        }
                        providerReturned.countDown();
                        return new PracticeAttemptEvaluationOutcome(
                                PracticeAttemptEvaluationOutcome.SUCCEEDED,
                                "c".repeat(64),
                                BigDecimal.TEN,
                                BigDecimal.TEN,
                                "{}",
                                "{}",
                                "TEST",
                                null,
                                false);
                    });

            assertThat(processor.processDue(1)).isEqualTo(1);
            assertThat(providerStarted.await(
                    1, TimeUnit.SECONDS)).isTrue();
            processor.shutdown();
            assertThat(providerReturned.await(
                    2, TimeUnit.SECONDS)).isTrue();

            verify(transactions, after(500).never()).complete(
                    any(), any(), anyString(), any(LocalDateTime.class));
            verify(transactions, never()).fail(
                    any(), anyString(), anyString(),
                    anyBoolean(), any(LocalDateTime.class));
        } finally {
            processor.shutdown();
        }
    }
}
