package com.ksh.features.mail.outbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Retention and read-only operational queries for the durable mail outbox.
 */
@Service
public class MailOutboxOperationsService {

    static final int MAX_RETENTION_BATCH_SIZE = 1_000;

    private static final List<MailOutboxStatus> READY_STATUSES =
            List.of(MailOutboxStatus.PENDING, MailOutboxStatus.RETRY);

    private final MailOutboxRepository repository;
    private final MailOutboxMetrics metrics;
    private final Clock clock;
    private final AtomicLong retentionSequence = new AtomicLong();

    @Autowired
    public MailOutboxOperationsService(
            MailOutboxRepository repository,
            MailOutboxMetrics metrics) {
        this(repository, metrics, Clock.systemUTC());
    }

    MailOutboxOperationsService(
            MailOutboxRepository repository,
            MailOutboxMetrics metrics,
            Clock clock) {
        this.repository = repository;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Loads and publishes an approximate point-in-time, non-PII snapshot.
     */
    @Transactional(readOnly = true, timeout = 5)
    public MailOutboxOperationalSnapshot snapshot() {
        MailOutboxOperationalSnapshot snapshot = loadSnapshot(now());
        metrics.update(snapshot);
        return snapshot;
    }

    /**
     * Deletes a bounded number of old terminal rows.
     *
     * <p>Each run reserves capacity for both terminal states. The state given
     * the odd row alternates, so a batch size of one cannot permanently starve
     * either SENT or FAILED rows. Unused capacity is offered to the other
     * terminal state while the total remains capped.
     */
    @Transactional(timeout = 10)
    public MailOutboxRetentionSummary retainTerminalJobs(
            Duration sentRetention,
            Duration failedRetention,
            int requestedBatchSize) {
        Duration validatedSentRetention =
                requirePositive(sentRetention, "sentRetention");
        Duration validatedFailedRetention =
                requirePositive(failedRetention, "failedRetention");
        int batchLimit = Math.max(
                1,
                Math.min(requestedBatchSize, MAX_RETENTION_BATCH_SIZE));
        LocalDateTime observedAt = now();
        LocalDateTime sentCutoff = observedAt.minus(validatedSentRetention);
        LocalDateTime failedCutoff = observedAt.minus(validatedFailedRetention);

        boolean startsWithSent = retentionSequence.getAndIncrement() % 2 == 0;
        int firstBudget = (batchLimit + 1) / 2;
        int secondBudget = batchLimit - firstBudget;

        int sentDeleted;
        int failedDeleted;
        if (startsWithSent) {
            sentDeleted = deleteSent(sentCutoff, firstBudget);
            failedDeleted = deleteFailed(failedCutoff, secondBudget);
        } else {
            failedDeleted = deleteFailed(failedCutoff, firstBudget);
            sentDeleted = deleteSent(sentCutoff, secondBudget);
        }

        int remaining = batchLimit - sentDeleted - failedDeleted;
        if (remaining > 0) {
            if (startsWithSent) {
                failedDeleted += deleteFailed(failedCutoff, remaining);
                remaining = batchLimit - sentDeleted - failedDeleted;
                sentDeleted += deleteSent(sentCutoff, remaining);
            } else {
                sentDeleted += deleteSent(sentCutoff, remaining);
                remaining = batchLimit - sentDeleted - failedDeleted;
                failedDeleted += deleteFailed(failedCutoff, remaining);
            }
        }

        MailOutboxOperationalSnapshot snapshotAfter = loadSnapshot(observedAt);
        MailOutboxRetentionSummary summary = new MailOutboxRetentionSummary(
                observedAt,
                sentCutoff,
                failedCutoff,
                batchLimit,
                sentDeleted,
                failedDeleted,
                snapshotAfter);
        return summary;
    }

    /**
     * Publishes telemetry only after the proxied retention transaction has
     * returned successfully, which means its database commit completed.
     */
    void publishCommittedRetention(MailOutboxRetentionSummary summary) {
        metrics.update(summary.snapshotAfter());
        metrics.recordRetention(summary);
    }

    private int deleteSent(LocalDateTime cutoff, int limit) {
        return limit > 0 ? repository.deleteSentBefore(cutoff, limit) : 0;
    }

    private int deleteFailed(LocalDateTime cutoff, int limit) {
        return limit > 0 ? repository.deleteFailedBefore(cutoff, limit) : 0;
    }

    private MailOutboxOperationalSnapshot loadSnapshot(LocalDateTime observedAt) {
        long readyClaimable = repository.countReadyClaimable(
                READY_STATUSES,
                observedAt);
        long expiredProcessingLeases = repository.countExpiredProcessingLeases(
                MailOutboxStatus.PROCESSING,
                observedAt);
        Optional<LocalDateTime> oldestClaimable = oldest(
                repository.findOldestReadyAvailableAt(READY_STATUSES, observedAt),
                repository.findOldestExpiredLeaseAt(
                        MailOutboxStatus.PROCESSING,
                        observedAt));
        long oldestAgeSeconds = oldestClaimable
                .map(timestamp -> Math.max(
                        0L,
                        Duration.between(timestamp, observedAt).getSeconds()))
                .orElse(0L);

        return new MailOutboxOperationalSnapshot(
                observedAt,
                repository.countByStatus(MailOutboxStatus.PENDING),
                repository.countByStatus(MailOutboxStatus.PROCESSING),
                repository.countByStatus(MailOutboxStatus.RETRY),
                repository.countByStatus(MailOutboxStatus.SENT),
                repository.countByStatus(MailOutboxStatus.FAILED),
                readyClaimable,
                expiredProcessingLeases,
                oldestAgeSeconds);
    }

    private static Optional<LocalDateTime> oldest(
            Optional<LocalDateTime> first,
            Optional<LocalDateTime> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return Optional.of(first.get().isBefore(second.get())
                ? first.get()
                : second.get());
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
