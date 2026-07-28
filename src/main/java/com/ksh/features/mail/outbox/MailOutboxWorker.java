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
 * Polls the durable mail outbox on its isolated scheduler.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.mail.outbox",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MailOutboxWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxWorker.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    private final MailOutboxProcessor processor;
    private final int batchSize;
    private final Duration initialDelay;
    private final Duration fixedDelay;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile ScheduledThreadPoolExecutor scheduler;

    public MailOutboxWorker(
            MailOutboxProcessor processor,
            @Value("${app.mail.outbox.worker-batch-size:10}") int batchSize,
            @Value("${app.mail.outbox.worker-initial-delay:PT30S}") Duration initialDelay,
            @Value("${app.mail.outbox.worker-fixed-delay:PT10S}") Duration fixedDelay) {
        this.processor = processor;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.initialDelay = requireNonNegative(initialDelay, "initialDelay");
        this.fixedDelay = requirePositive(fixedDelay, "fixedDelay");
    }

    public void processDue() {
        try {
            int processed = processor.processDue(batchSize);
            if (processed > 0) {
                log.info("Processed {} durable mail outbox jobs", processed);
            }
        } catch (RuntimeException ignored) {
            log.error("Durable mail outbox polling failed");
        }
    }

    /**
     * Starts a private scheduler that is not registered as a Spring scheduler
     * bean. Existing scheduled tasks therefore retain their current executor.
     */
    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ScheduledThreadPoolExecutor executor = newExecutor();
        scheduler = executor;
        executor.scheduleWithFixedDelay(
                this::processDue,
                initialDelay.toMillis(),
                fixedDelay.toMillis(),
                TimeUnit.MILLISECONDS);
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
        // Start after ordinary infrastructure and stop before it is torn down.
        return Integer.MAX_VALUE - 100;
    }

    private ScheduledThreadPoolExecutor newExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "ksh-mail-outbox-" + sequence.incrementAndGet());
                    thread.setDaemon(false);
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
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return value;
    }
}
