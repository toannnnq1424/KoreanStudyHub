package com.ksh.features.ai.questiongen;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional, non-PII retention metrics. The cleanup path remains operational
 * when no meter registry exists or when a registry rejects an update.
 */
@Component
class AiQuestionDraftRetentionMetrics {

    static final String RUNS = "ksh.ai.question_draft.retention.cleanup.runs";
    static final String DELETED = "ksh.ai.question_draft.retention.cleanup.deleted";
    static final String FAILURES = "ksh.ai.question_draft.retention.cleanup.failures";
    static final String EXPIRED_COUNT = "ksh.ai.question_draft.retention.expired.count";
    static final String OLDEST_EXPIRED_AGE =
            "ksh.ai.question_draft.retention.expired.oldest.age";

    private static final Logger log =
            LoggerFactory.getLogger(AiQuestionDraftRetentionMetrics.class);

    private final Counter runs;
    private final Counter deleted;
    private final Counter failures;
    private final AtomicLong expiredCount;
    private final AtomicLong oldestExpiredAgeSeconds;

    AiQuestionDraftRetentionMetrics(ObjectProvider<MeterRegistry> registries) {
        Counter registeredRuns = null;
        Counter registeredDeleted = null;
        Counter registeredFailures = null;
        AtomicLong registeredExpiredCount = null;
        AtomicLong registeredOldestAge = null;
        try {
            MeterRegistry registry = registries.getIfAvailable();
            if (registry != null) {
                registeredRuns = Counter.builder(RUNS)
                        .description("Completed AI question-draft retention sweeps")
                        .register(registry);
                registeredDeleted = Counter.builder(DELETED)
                        .description("Expired AI question-draft sessions deleted")
                        .baseUnit("sessions")
                        .register(registry);
                registeredFailures = Counter.builder(FAILURES)
                        .description("Failed AI question-draft retention sweeps")
                        .register(registry);
                registeredExpiredCount = new AtomicLong();
                registeredOldestAge = new AtomicLong();
                Gauge.builder(EXPIRED_COUNT, registeredExpiredCount, AtomicLong::doubleValue)
                        .description("Expired AI question-draft sessions awaiting deletion")
                        .baseUnit("sessions")
                        .register(registry);
                Gauge.builder(
                                OLDEST_EXPIRED_AGE,
                                registeredOldestAge,
                                AtomicLong::doubleValue)
                        .description("Age of the oldest expired AI question-draft session")
                        .baseUnit("seconds")
                        .register(registry);
            }
        } catch (RuntimeException ignored) {
            log.warn("event=ai_question_draft_retention_metrics_unavailable");
            registeredRuns = null;
            registeredDeleted = null;
            registeredFailures = null;
            registeredExpiredCount = null;
            registeredOldestAge = null;
        }
        this.runs = registeredRuns;
        this.deleted = registeredDeleted;
        this.failures = registeredFailures;
        this.expiredCount = registeredExpiredCount;
        this.oldestExpiredAgeSeconds = registeredOldestAge;
    }

    void recordSuccess(int deletedCount, long remainingCount, long oldestAgeSeconds) {
        try {
            if (runs != null) {
                runs.increment();
                deleted.increment(deletedCount);
                expiredCount.set(remainingCount);
                oldestExpiredAgeSeconds.set(oldestAgeSeconds);
            }
        } catch (RuntimeException ignored) {
            log.warn("event=ai_question_draft_retention_metrics_update_failed outcome=success");
        }
    }

    void recordCommittedDeletes(int deletedCount) {
        try {
            if (deleted != null) {
                deleted.increment(deletedCount);
            }
        } catch (RuntimeException ignored) {
            log.warn(
                    "event=ai_question_draft_retention_metrics_update_failed "
                            + "outcome=partial");
        }
    }

    void recordFailure() {
        try {
            if (failures != null) {
                failures.increment();
            }
        } catch (RuntimeException ignored) {
            log.warn("event=ai_question_draft_retention_metrics_update_failed outcome=failure");
        }
    }
}
