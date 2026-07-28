package com.ksh.features.mail.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxRetentionWorkerTest {

    @Test
    void retention_worker_uses_private_thread_distinct_from_delivery_and_stops() throws Exception {
        MailOutboxOperationsService operations =
                mock(MailOutboxOperationsService.class);
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        when(operations.retainTerminalJobs(any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    threadName.set(Thread.currentThread().getName());
                    invoked.countDown();
                    return summary();
                });
        MailOutboxRetentionWorker worker = new MailOutboxRetentionWorker(
                operations,
                50,
                10,
                Duration.ofDays(30),
                Duration.ofDays(90),
                Duration.ZERO,
                Duration.ofHours(1));

        try {
            worker.start();
            assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get())
                    .startsWith("ksh-mail-outbox-retention-")
                    .doesNotStartWith("ksh-mail-outbox-1");
            assertThat(worker.isRunning()).isTrue();
        } finally {
            worker.stop();
        }
        assertThat(worker.isRunning()).isFalse();
    }

    @Test
    void run_drains_full_batches_until_first_short_batch() {
        MailOutboxOperationsService operations =
                mock(MailOutboxOperationsService.class);
        when(operations.retainTerminalJobs(any(), any(), anyInt()))
                .thenReturn(summary(50, 25, 25))
                .thenReturn(summary(50, 20, 30))
                .thenReturn(summary(50, 3, 4));
        MailOutboxRetentionWorker worker = worker(operations, 50, 10);

        worker.retainTerminalJobs();

        verify(operations, times(3))
                .retainTerminalJobs(
                        Duration.ofDays(30),
                        Duration.ofDays(90),
                        50);
        verify(operations, times(3))
                .publishCommittedRetention(any(MailOutboxRetentionSummary.class));
    }

    @Test
    void run_stops_at_configured_max_batches_when_backlog_remains_full() {
        MailOutboxOperationsService operations =
                mock(MailOutboxOperationsService.class);
        when(operations.retainTerminalJobs(any(), any(), anyInt()))
                .thenReturn(summary(50, 25, 25));
        MailOutboxRetentionWorker worker = worker(operations, 50, 3);

        worker.retainTerminalJobs();

        verify(operations, times(3))
                .retainTerminalJobs(
                        Duration.ofDays(30),
                        Duration.ofDays(90),
                        50);
        verify(operations, times(3))
                .publishCommittedRetention(any(MailOutboxRetentionSummary.class));
    }

    @Test
    void retention_worker_can_be_disabled_without_starting_a_scheduler() {
        MailOutboxOperationsService operations =
                mock(MailOutboxOperationsService.class);

        new ApplicationContextRunner()
                .withBean(MailOutboxOperationsService.class, () -> operations)
                .withUserConfiguration(MailOutboxRetentionWorker.class)
                .withPropertyValues(
                        "app.mail.outbox.retention.worker-enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(MailOutboxRetentionWorker.class));
    }

    private static MailOutboxRetentionWorker worker(
            MailOutboxOperationsService operations,
            int batchSize,
            int maxBatches) {
        return new MailOutboxRetentionWorker(
                operations,
                batchSize,
                maxBatches,
                Duration.ofDays(30),
                Duration.ofDays(90),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5));
    }

    private static MailOutboxRetentionSummary summary() {
        return summary(50, 0, 0);
    }

    private static MailOutboxRetentionSummary summary(
            int batchLimit,
            int sentDeleted,
            int failedDeleted) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 8, 0);
        MailOutboxOperationalSnapshot snapshot =
                new MailOutboxOperationalSnapshot(
                        now,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0);
        return new MailOutboxRetentionSummary(
                now,
                now.minusDays(30),
                now.minusDays(90),
                batchLimit,
                sentDeleted,
                failedDeleted,
                snapshot);
    }
}
