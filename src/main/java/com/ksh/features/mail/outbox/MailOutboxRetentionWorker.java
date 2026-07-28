package com.ksh.features.mail.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs terminal outbox retention on an executor isolated from delivery and
 * every application/Practice scheduler.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.mail.outbox.retention",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MailOutboxRetentionWorker implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(MailOutboxRetentionWorker.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_CONFIGURED_BATCHES_PER_RUN = 100;

    private final MailOutboxOperationsService operations;
    private final int batchSize;
    private final int maxBatchesPerRun;
    private final Duration sentRetention;
    private final Duration failedRetention;
    private final Duration initialDelay;
    private final Duration fixedDelay;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile ScheduledThreadPoolExecutor scheduler;

    public MailOutboxRetentionWorker(
            MailOutboxOperationsService operations,
            @Value("${app.mail.outbox.retention.batch-size:500}") int batchSize,
            @Value("${app.mail.outbox.retention.max-batches-per-run:10}")
            int maxBatchesPerRun,
            @Value("${app.mail.outbox.retention.sent-age:P30D}") Duration sentRetention,
            @Value("${app.mail.outbox.retention.failed-age:P90D}") Duration failedRetention,
            @Value("${app.mail.outbox.retention.initial-delay:PT30S}") Duration initialDelay,
            @Value("${app.mail.outbox.retention.fixed-delay:PT5M}") Duration fixedDelay) {
        this.operations = operations;
        this.batchSize = Math.max(
                1,
                Math.min(batchSize, MailOutboxOperationsService.MAX_RETENTION_BATCH_SIZE));
        this.maxBatchesPerRun = Math.max(
                1,
                Math.min(maxBatchesPerRun, MAX_CONFIGURED_BATCHES_PER_RUN));
        this.sentRetention = requirePositive(sentRetention, "sentRetention");
        this.failedRetention = requirePositive(failedRetention, "failedRetention");
        this.initialDelay = requireNonNegative(initialDelay, "initialDelay");
        this.fixedDelay = requirePositive(fixedDelay, "fixedDelay");
    }

    public void retainTerminalJobs() {
        int completedBatches = 0;
        long sentDeleted = 0;
        long failedDeleted = 0;
        try {
            MailOutboxOperationalSnapshot snapshot = null;
            for (int batch = 0; batch < maxBatchesPerRun; batch++) {
                MailOutboxRetentionSummary summary =
                        operations.retainTerminalJobs(
                                sentRetention,
                                failedRetention,
                                batchSize);
                completedBatches++;
                sentDeleted += summary.sentDeleted();
                failedDeleted += summary.failedDeleted();
                snapshot = summary.snapshotAfter();
                operations.publishCommittedRetention(summary);
                if (summary.totalDeleted() < batchSize) {
                    break;
                }
            }
            log.info(
                    "mail_outbox_retention_summary batches={} max_batches={} "
                            + "sent_deleted={} failed_deleted={} batch_size={} "
                            + "total_jobs={} claimable={} expired_leases={}",
                    completedBatches,
                    maxBatchesPerRun,
                    sentDeleted,
                    failedDeleted,
                    batchSize,
                    snapshot != null ? snapshot.totalJobs() : 0,
                    snapshot != null ? snapshot.totalClaimable() : 0,
                    snapshot != null ? snapshot.expiredProcessingLeases() : 0);
        } catch (RuntimeException exception) {
            log.error(
                    "mail_outbox_retention_failed completed_batches={} "
                            + "sent_deleted={} failed_deleted={} error_type={}",
                    completedBatches,
                    sentDeleted,
                    failedDeleted,
                    exception.getClass().getSimpleName(),
                    exception);
        }
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ScheduledThreadPoolExecutor executor = newExecutor();
        scheduler = executor;
        try {
            executor.scheduleWithFixedDelay(
                    this::retainTerminalJobs,
                    initialDelay.toMillis(),
                    fixedDelay.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (RuntimeException schedulingFailure) {
            scheduler = null;
            running.set(false);
            executor.shutdownNow();
            throw schedulingFailure;
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
        // Start after delivery; stop before the delivery worker and persistence.
        return Integer.MAX_VALUE - 90;
    }

    private ScheduledThreadPoolExecutor newExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "ksh-mail-outbox-retention-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static Duration requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative.");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()
                || value.toMillis() < 1) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return value;
    }
}
