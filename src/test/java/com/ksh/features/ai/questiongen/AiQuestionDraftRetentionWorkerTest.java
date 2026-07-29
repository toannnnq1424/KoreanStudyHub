package com.ksh.features.ai.questiongen;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiQuestionDraftRetentionWorkerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AiQuestionDraftMaintenanceService.class,
                    () -> mock(AiQuestionDraftMaintenanceService.class))
            .withUserConfiguration(AiQuestionDraftRetentionWorker.class);

    @Test
    void worker_is_absent_when_explicitly_disabled() {
        contextRunner
                .withPropertyValues(
                        "app.ai.question-draft.retention.worker-enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AiQuestionDraftRetentionWorker.class));
    }

    @Test
    void worker_uses_a_private_non_practice_thread_and_stops_cleanly()
            throws Exception {
        AiQuestionDraftMaintenanceService maintenance =
                mock(AiQuestionDraftMaintenanceService.class);
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicBoolean daemonThread = new AtomicBoolean();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            daemonThread.set(Thread.currentThread().isDaemon());
            invoked.countDown();
            return new AiQuestionDraftMaintenanceService.CleanupResult(
                    1, 0, 0L, 0L, false);
        }).when(maintenance).cleanupExpired(any(LocalDateTime.class), anyInt(), anyInt());
        AiQuestionDraftRetentionWorker worker = new AiQuestionDraftRetentionWorker(
                maintenance,
                10,
                2,
                Duration.ZERO,
                Duration.ofHours(1));

        try {
            worker.start();
            assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get())
                    .startsWith("ksh-ai-question-draft-retention-")
                    .doesNotContain("practice");
            assertThat(daemonThread).isTrue();
            assertThat(worker.isRunning()).isTrue();
        } finally {
            worker.stop();
        }
        assertThat(worker.isRunning()).isFalse();
    }

    @Test
    void failed_sweep_is_contained_and_counted() {
        AiQuestionDraftMaintenanceService maintenance =
                mock(AiQuestionDraftMaintenanceService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(maintenance)
                .cleanupExpired(any(LocalDateTime.class), anyInt(), anyInt());
        AiQuestionDraftRetentionWorker worker = new AiQuestionDraftRetentionWorker(
                maintenance,
                10,
                2,
                Duration.ofMinutes(1),
                Duration.ofHours(1));

        worker.sweepExpired();

        verify(maintenance).recordFailure();
    }

    @Test
    void sub_millisecond_fixed_delay_is_rejected() {
        AiQuestionDraftMaintenanceService maintenance =
                mock(AiQuestionDraftMaintenanceService.class);

        assertThatThrownBy(() -> new AiQuestionDraftRetentionWorker(
                maintenance,
                10,
                2,
                Duration.ZERO,
                Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one millisecond");
    }

    @Test
    void failed_start_rolls_back_running_state_and_closes_the_executor() {
        AiQuestionDraftMaintenanceService maintenance =
                mock(AiQuestionDraftMaintenanceService.class);
        AiQuestionDraftRetentionWorker worker = new AiQuestionDraftRetentionWorker(
                maintenance,
                10,
                2,
                Duration.ofSeconds(Long.MAX_VALUE),
                Duration.ofHours(1));

        assertThatThrownBy(worker::start).isInstanceOf(ArithmeticException.class);
        assertThat(worker.isRunning()).isFalse();
        worker.stop();
    }
}
