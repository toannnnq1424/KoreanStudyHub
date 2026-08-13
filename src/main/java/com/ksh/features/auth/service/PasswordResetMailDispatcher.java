package com.ksh.features.auth.service;

import com.ksh.features.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hands password-reset mail to an isolated, bounded executor.
 *
 * <p>Password-reset messages deliberately do not use the durable notification
 * outbox: its payload is persisted in plaintext and a reset link contains a
 * live bearer credential. Keeping this queue process-local preserves the
 * token table's digest-only guarantee while ensuring slow SMTP never occupies
 * the HTTP request thread. Saturation and transport failures remain
 * best-effort and are logged without recipient, message, token, or provider
 * exception detail.
 */
@Component
public class PasswordResetMailDispatcher implements DisposableBean {

    static final String SUBJECT = "KSH Password Reset";

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetMailDispatcher.class);
    private static final int MAX_WORKERS = 8;
    private static final int MAX_QUEUE_CAPACITY = 1_000;
    private static final Duration MAX_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private final MailService mailService;
    private final ThreadPoolExecutor executor;
    private final Duration shutdownTimeout;

    public PasswordResetMailDispatcher(
            MailService mailService,
            @Value("${app.auth.password-reset.mail-workers:2}") int workers,
            @Value("${app.auth.password-reset.mail-queue-capacity:128}") int queueCapacity,
            @Value("${app.auth.password-reset.mail-shutdown-timeout:PT5S}")
            Duration shutdownTimeout) {
        this.mailService = mailService;
        this.shutdownTimeout = boundedShutdownTimeout(shutdownTimeout);
        this.executor = new ThreadPoolExecutor(
                boundedPositive(workers, MAX_WORKERS),
                boundedPositive(workers, MAX_WORKERS),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(
                        boundedPositive(queueCapacity, MAX_QUEUE_CAPACITY)),
                new PasswordResetMailThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Enqueues one mail without waiting for SMTP. This method never propagates
     * queue lifecycle or saturation failures back into the neutral HTTP flow.
     */
    public void dispatch(String recipient, String body) {
        if (recipient == null || recipient.isBlank() || body == null || body.isBlank()) {
            log.warn("Password-reset email dispatch rejected an invalid internal payload");
            return;
        }
        try {
            executor.execute(() -> deliver(recipient, body));
        } catch (RejectedExecutionException ignored) {
            log.warn("Password-reset email dispatch skipped because the bounded queue is unavailable");
        }
    }

    private void deliver(String recipient, String body) {
        try {
            if (!mailService.send(recipient, SUBJECT, body)) {
                log.warn("Password-reset email was not sent");
            }
        } catch (RuntimeException ignored) {
            // Provider exception messages can contain recipient or message data.
            log.warn("Password-reset email delivery failed");
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                    shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static int boundedPositive(int value, int maximum) {
        return Math.max(1, Math.min(value, maximum));
    }

    private static Duration boundedShutdownTimeout(Duration value) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException("mailShutdownTimeout must not be negative");
        }
        return value.compareTo(MAX_SHUTDOWN_TIMEOUT) > 0
                ? MAX_SHUTDOWN_TIMEOUT
                : value;
    }

    private static final class PasswordResetMailThreadFactory
            implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(
                    runnable,
                    "ksh-password-reset-mail-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
