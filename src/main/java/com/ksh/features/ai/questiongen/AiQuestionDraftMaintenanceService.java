package com.ksh.features.ai.questiongen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Runs a bounded, observable retention sweep for durable AI question drafts.
 */
@Service
public class AiQuestionDraftMaintenanceService {

    static final int MAX_BATCH_SIZE = 1_000;
    static final int MAX_BATCHES_PER_SWEEP = 100;

    private static final Logger log =
            LoggerFactory.getLogger(AiQuestionDraftMaintenanceService.class);

    private final AiQuestionDraftCleanupBatch cleanupBatch;
    private final AiQuestionDraftSessionRepository repository;
    private final AiQuestionDraftRetentionMetrics metrics;

    AiQuestionDraftMaintenanceService(
            AiQuestionDraftCleanupBatch cleanupBatch,
            AiQuestionDraftSessionRepository repository,
            AiQuestionDraftRetentionMetrics metrics) {
        this.cleanupBatch = cleanupBatch;
        this.repository = repository;
        this.metrics = metrics;
    }

    public CleanupResult cleanupExpired(
            LocalDateTime cutoff,
            int requestedBatchSize,
            int requestedMaxBatches) {
        Objects.requireNonNull(cutoff, "cutoff");
        int batchSize = boundedPositive(
                requestedBatchSize, MAX_BATCH_SIZE, "batchSize");
        int maxBatches = boundedPositive(
                requestedMaxBatches, MAX_BATCHES_PER_SWEEP, "maxBatches");

        int batches = 0;
        int deleted = 0;
        int lastBatch = 0;
        try {
            while (batches < maxBatches) {
                lastBatch = cleanupBatch.deleteExpired(cutoff, batchSize);
                if (lastBatch < 0 || lastBatch > batchSize) {
                    throw new IllegalStateException(
                            "AI question-draft cleanup returned an invalid batch size.");
                }
                batches++;
                deleted += lastBatch;
                if (lastBatch < batchSize) {
                    break;
                }
            }
        } catch (RuntimeException failure) {
            if (deleted > 0) {
                metrics.recordCommittedDeletes(deleted);
                log.warn(
                        "event=ai_question_draft_retention_partial_failure "
                                + "committed_batches={} committed_deleted={}",
                        batches,
                        deleted);
            }
            throw failure;
        }

        boolean capped = batches == maxBatches && lastBatch == batchSize;
        RetentionSnapshot snapshot = retentionSnapshot(cutoff);
        metrics.recordSuccess(
                deleted,
                snapshot.expiredRemaining(),
                snapshot.oldestExpiredAgeSeconds());
        log.info(
                "event=ai_question_draft_retention_complete batches={} deleted={} "
                        + "expired_remaining={} oldest_expired_age_seconds={} capped={}",
                batches,
                deleted,
                snapshot.expiredRemaining(),
                snapshot.oldestExpiredAgeSeconds(),
                capped);
        return new CleanupResult(
                batches,
                deleted,
                snapshot.expiredRemaining(),
                snapshot.oldestExpiredAgeSeconds(),
                capped);
    }

    void recordFailure() {
        metrics.recordFailure();
    }

    private RetentionSnapshot retentionSnapshot(LocalDateTime cutoff) {
        try {
            long remaining = repository.countByExpiresAtLessThanEqual(cutoff);
            long oldestAgeSeconds = repository.findOldestExpiredAt(cutoff)
                    .map(oldest -> Math.max(
                            0L, Duration.between(oldest, cutoff).getSeconds()))
                    .orElse(0L);
            return new RetentionSnapshot(remaining, oldestAgeSeconds);
        } catch (RuntimeException ignored) {
            // Observability is best effort and must not undo already-committed batches.
            log.warn("event=ai_question_draft_retention_snapshot_failed");
            return RetentionSnapshot.unavailable();
        }
    }

    private static int boundedPositive(int value, int maximum, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return Math.min(value, maximum);
    }

    public record CleanupResult(
            int batches,
            int deleted,
            long expiredRemaining,
            long oldestExpiredAgeSeconds,
            boolean capped) {
    }

    private record RetentionSnapshot(
            long expiredRemaining,
            long oldestExpiredAgeSeconds) {

        private static RetentionSnapshot unavailable() {
            return new RetentionSnapshot(-1L, -1L);
        }
    }
}
