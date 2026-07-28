package com.ksh.features.ai.questiongen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sweeps expired AI question drafts on a private fixed-delay executor. It does
 * not register a Spring scheduler or share Practice scheduling infrastructure.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.ai.question-draft.retention",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AiQuestionDraftRetentionWorker implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(AiQuestionDraftRetentionWorker.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    private final AiQuestionDraftMaintenanceService maintenance;
    private final int batchSize;
    private final int maxBatches;
    private final Duration initialDelay;
    private final Duration fixedDelay;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile ScheduledThreadPoolExecutor scheduler;

    public AiQuestionDraftRetentionWorker(
            AiQuestionDraftMaintenanceService maintenance,
            @Value("${app.ai.question-draft.retention.batch-size:500}") int batchSize,
            @Value("${app.ai.question-draft.retention.max-batches-per-sweep:20}")
            int maxBatches,
            @Value("${app.ai.question-draft.retention.initial-delay:PT5M}")
            Duration initialDelay,
            @Value("${app.ai.question-draft.retention.fixed-delay:PT1H}")
            Duration fixedDelay) {
        this.maintenance = maintenance;
        this.batchSize = boundedPositive(
                batchSize,
                AiQuestionDraftMaintenanceService.MAX_BATCH_SIZE,
                "batchSize");
        this.maxBatches = boundedPositive(
                maxBatches,
                AiQuestionDraftMaintenanceService.MAX_BATCHES_PER_SWEEP,
                "maxBatches");
        this.initialDelay = requireNonNegative(initialDelay, "initialDelay");
        this.fixedDelay = requirePositive(fixedDelay, "fixedDelay");
    }

    public void sweepExpired() {
        try {
            maintenance.cleanupExpired(
                    LocalDateTime.now(ZoneOffset.UTC), batchSize, maxBatches);
        } catch (RuntimeException ignored) {
            maintenance.recordFailure();
            log.error("event=ai_question_draft_retention_failed");
        }
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ScheduledThreadPoolExecutor executor = null;
        try {
            executor = newExecutor();
            scheduler = executor;
            executor.scheduleWithFixedDelay(
                    this::sweepExpired,
                    initialDelay.toMillis(),
                    fixedDelay.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            scheduler = null;
            running.set(false);
            if (executor != null) {
                executor.shutdownNow();
            }
            throw failure;
        }
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledThreadPoolExecutor executor = scheduler;
        scheduler = null;
        if (executor == null) {
            return;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                    SHUTDOWN_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 110;
    }

    private ScheduledThreadPoolExecutor newExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "ksh-ai-question-draft-retention-"
                                    + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static int boundedPositive(int value, int maximum, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return Math.min(value, maximum);
    }

    private static Duration requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative.");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()
                || value.toMillis() < 1L) {
            throw new IllegalArgumentException(name + " must be at least one millisecond.");
        }
        return value;
    }
}
