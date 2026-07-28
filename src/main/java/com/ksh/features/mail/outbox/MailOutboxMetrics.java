package com.ksh.features.mail.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional Micrometer bridge for non-PII outbox health measurements.
 */
@Component
public class MailOutboxMetrics {

    private static final Logger log =
            LoggerFactory.getLogger(MailOutboxMetrics.class);

    static final String JOBS_METRIC = "ksh.mail.outbox.jobs";
    static final String CLAIMABLE_METRIC = "ksh.mail.outbox.claimable";
    static final String EXPIRED_LEASES_METRIC = "ksh.mail.outbox.expired.leases";
    static final String OLDEST_CLAIMABLE_AGE_METRIC =
            "ksh.mail.outbox.oldest.claimable.age.seconds";
    static final String RETENTION_DELETED_METRIC =
            "ksh.mail.outbox.retention.deleted";

    private final Map<MailOutboxStatus, AtomicLong> statusCounts =
            new EnumMap<>(MailOutboxStatus.class);
    private final AtomicLong claimable = new AtomicLong();
    private final AtomicLong expiredLeases = new AtomicLong();
    private final AtomicLong oldestClaimableAgeSeconds = new AtomicLong();
    private final Counter sentRetentionDeleted;
    private final Counter failedRetentionDeleted;

    public MailOutboxMetrics(ObjectProvider<MeterRegistry> registries) {
        for (MailOutboxStatus status : MailOutboxStatus.values()) {
            statusCounts.put(status, new AtomicLong());
        }

        Counter registeredSentCounter = null;
        Counter registeredFailedCounter = null;
        try {
            MeterRegistry registry =
                    registries.orderedStream().findFirst().orElse(null);
            if (registry != null) {
                statusCounts.forEach((status, value) ->
                        Gauge.builder(JOBS_METRIC, value, AtomicLong::doubleValue)
                                .description("Current durable mail outbox jobs by lifecycle state")
                                .tag("status", status.name().toLowerCase())
                                .register(registry));
                Gauge.builder(CLAIMABLE_METRIC, claimable, AtomicLong::doubleValue)
                        .description("Current jobs eligible for claim, including expired leases")
                        .register(registry);
                Gauge.builder(EXPIRED_LEASES_METRIC, expiredLeases, AtomicLong::doubleValue)
                        .description("Current processing jobs whose delivery lease expired")
                        .register(registry);
                Gauge.builder(
                                OLDEST_CLAIMABLE_AGE_METRIC,
                                oldestClaimableAgeSeconds,
                                AtomicLong::doubleValue)
                        .description("Age in seconds of the oldest job eligible for claim")
                        .register(registry);
                registeredSentCounter = Counter.builder(RETENTION_DELETED_METRIC)
                        .description("Terminal outbox rows deleted by retention")
                        .tag("status", "sent")
                        .register(registry);
                registeredFailedCounter = Counter.builder(RETENTION_DELETED_METRIC)
                        .description("Terminal outbox rows deleted by retention")
                        .tag("status", "failed")
                        .register(registry);
            }
        } catch (RuntimeException exception) {
            // Metrics are optional: a broken exporter must not prevent startup.
            log.warn(
                    "mail_outbox_metrics_registration_failed error_type={}",
                    exception.getClass().getSimpleName());
        }
        sentRetentionDeleted = registeredSentCounter;
        failedRetentionDeleted = registeredFailedCounter;
    }

    void update(MailOutboxOperationalSnapshot snapshot) {
        try {
            statusCounts.get(MailOutboxStatus.PENDING).set(snapshot.pending());
            statusCounts.get(MailOutboxStatus.PROCESSING).set(snapshot.processing());
            statusCounts.get(MailOutboxStatus.RETRY).set(snapshot.retry());
            statusCounts.get(MailOutboxStatus.SENT).set(snapshot.sent());
            statusCounts.get(MailOutboxStatus.FAILED).set(snapshot.failed());
            claimable.set(snapshot.totalClaimable());
            expiredLeases.set(snapshot.expiredProcessingLeases());
            oldestClaimableAgeSeconds.set(snapshot.oldestClaimableAgeSeconds());
        } catch (RuntimeException exception) {
            // Snapshot/admin availability must never depend on instrumentation.
            log.warn(
                    "mail_outbox_metrics_update_failed error_type={}",
                    exception.getClass().getSimpleName());
        }
    }

    void recordRetention(MailOutboxRetentionSummary summary) {
        try {
            if (sentRetentionDeleted != null) {
                sentRetentionDeleted.increment(summary.sentDeleted());
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "mail_outbox_metrics_counter_failed status=sent error_type={}",
                    exception.getClass().getSimpleName());
        }
        try {
            if (failedRetentionDeleted != null) {
                failedRetentionDeleted.increment(summary.failedDeleted());
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "mail_outbox_metrics_counter_failed status=failed error_type={}",
                    exception.getClass().getSimpleName());
        }
    }
}
