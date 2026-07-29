package com.ksh.features.mail.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxOperationsServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 29, 8, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC);
    private static final List<MailOutboxStatus> READY_STATUSES =
            List.of(MailOutboxStatus.PENDING, MailOutboxStatus.RETRY);

    private MailOutboxRepository repository;
    private SimpleMeterRegistry registry;
    private MailOutboxOperationsService service;

    @BeforeEach
    void setUp() {
        repository = mock(MailOutboxRepository.class);
        registry = new SimpleMeterRegistry();
        service = new MailOutboxOperationsService(
                repository,
                metrics(registry),
                FIXED_CLOCK);
    }

    @Test
    void snapshot_reports_five_states_claimable_jobs_and_expired_leases_without_pii() {
        when(repository.countByStatus(MailOutboxStatus.PENDING)).thenReturn(3L);
        when(repository.countByStatus(MailOutboxStatus.PROCESSING)).thenReturn(2L);
        when(repository.countByStatus(MailOutboxStatus.RETRY)).thenReturn(4L);
        when(repository.countByStatus(MailOutboxStatus.SENT)).thenReturn(20L);
        when(repository.countByStatus(MailOutboxStatus.FAILED)).thenReturn(5L);
        when(repository.countReadyClaimable(READY_STATUSES, NOW)).thenReturn(6L);
        when(repository.countExpiredProcessingLeases(
                MailOutboxStatus.PROCESSING,
                NOW)).thenReturn(2L);
        when(repository.findOldestReadyAvailableAt(READY_STATUSES, NOW))
                .thenReturn(Optional.of(NOW.minusMinutes(10)));
        when(repository.findOldestExpiredLeaseAt(
                MailOutboxStatus.PROCESSING,
                NOW)).thenReturn(Optional.of(NOW.minusMinutes(3)));

        MailOutboxOperationalSnapshot snapshot = service.snapshot();

        assertThat(snapshot.pending()).isEqualTo(3);
        assertThat(snapshot.processing()).isEqualTo(2);
        assertThat(snapshot.retry()).isEqualTo(4);
        assertThat(snapshot.sent()).isEqualTo(20);
        assertThat(snapshot.failed()).isEqualTo(5);
        assertThat(snapshot.readyClaimable()).isEqualTo(6);
        assertThat(snapshot.expiredProcessingLeases()).isEqualTo(2);
        assertThat(snapshot.totalClaimable()).isEqualTo(8);
        assertThat(snapshot.oldestClaimableAgeSeconds()).isEqualTo(600);

        assertGauge(MailOutboxStatus.PENDING, 3);
        assertGauge(MailOutboxStatus.PROCESSING, 2);
        assertGauge(MailOutboxStatus.RETRY, 4);
        assertGauge(MailOutboxStatus.SENT, 20);
        assertGauge(MailOutboxStatus.FAILED, 5);
        assertThat(registry.get(MailOutboxMetrics.CLAIMABLE_METRIC)
                .gauge().value()).isEqualTo(8);
        assertThat(registry.get(MailOutboxMetrics.EXPIRED_LEASES_METRIC)
                .gauge().value()).isEqualTo(2);
        assertThat(registry.get(MailOutboxMetrics.OLDEST_CLAIMABLE_AGE_METRIC)
                .gauge().value()).isEqualTo(600);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .allSatisfy(tag -> {
                            assertThat(tag.getKey()).isEqualTo("status");
                            assertThat(tag.getValue())
                                    .isIn("pending", "processing", "retry", "sent", "failed");
                        }));
    }

    @Test
    void retention_uses_strict_age_cutoffs_and_never_exceeds_batch_cap() {
        when(repository.deleteSentBefore(NOW.minusDays(30), 500))
                .thenReturn(500);
        when(repository.deleteFailedBefore(NOW.minusDays(90), 500))
                .thenReturn(500);

        MailOutboxRetentionSummary summary = service.retainTerminalJobs(
                Duration.ofDays(30),
                Duration.ofDays(90),
                5_000);
        service.publishCommittedRetention(summary);

        assertThat(summary.batchLimit())
                .isEqualTo(MailOutboxOperationsService.MAX_RETENTION_BATCH_SIZE);
        assertThat(summary.totalDeleted()).isEqualTo(1_000);
        assertThat(summary.sentCutoff()).isEqualTo(NOW.minusDays(30));
        assertThat(summary.failedCutoff()).isEqualTo(NOW.minusDays(90));
        verify(repository).deleteSentBefore(NOW.minusDays(30), 500);
        verify(repository).deleteFailedBefore(NOW.minusDays(90), 500);
        assertThat(registry.get(MailOutboxMetrics.RETENTION_DELETED_METRIC)
                .tag("status", "sent").counter().count()).isEqualTo(500);
        assertThat(registry.get(MailOutboxMetrics.RETENTION_DELETED_METRIC)
                .tag("status", "failed").counter().count()).isEqualTo(500);
    }

    @Test
    void retention_reuses_empty_quota_without_deleting_live_states() {
        when(repository.deleteSentBefore(NOW.minusDays(30), 5)).thenReturn(2);
        when(repository.deleteFailedBefore(NOW.minusDays(90), 5)).thenReturn(5);
        when(repository.deleteFailedBefore(NOW.minusDays(90), 3)).thenReturn(3);

        MailOutboxRetentionSummary summary = service.retainTerminalJobs(
                Duration.ofDays(30),
                Duration.ofDays(90),
                10);

        assertThat(summary.sentDeleted()).isEqualTo(2);
        assertThat(summary.failedDeleted()).isEqualTo(8);
        assertThat(summary.totalDeleted()).isEqualTo(10);
        verify(repository, never()).deleteAllInBatch();
    }

    @Test
    void one_row_batches_alternate_terminal_state_to_prevent_starvation() {
        when(repository.deleteSentBefore(NOW.minusDays(30), 1)).thenReturn(1);
        when(repository.deleteFailedBefore(NOW.minusDays(90), 1)).thenReturn(1);

        MailOutboxRetentionSummary first = service.retainTerminalJobs(
                Duration.ofDays(30),
                Duration.ofDays(90),
                1);
        MailOutboxRetentionSummary second = service.retainTerminalJobs(
                Duration.ofDays(30),
                Duration.ofDays(90),
                1);

        assertThat(first.sentDeleted()).isEqualTo(1);
        assertThat(first.failedDeleted()).isZero();
        assertThat(second.sentDeleted()).isZero();
        assertThat(second.failedDeleted()).isEqualTo(1);
    }

    @Test
    void metrics_are_optional_when_no_registry_is_configured() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        MailOutboxOperationsService noRegistryService =
                new MailOutboxOperationsService(
                        repository,
                        new MailOutboxMetrics(
                                beans.getBeanProvider(MeterRegistry.class)),
                        FIXED_CLOCK);

        assertThat(noRegistryService.snapshot().totalJobs()).isZero();
    }

    private void assertGauge(MailOutboxStatus status, double expected) {
        assertThat(registry.get(MailOutboxMetrics.JOBS_METRIC)
                .tag("status", status.name().toLowerCase())
                .gauge()
                .value()).isEqualTo(expected);
    }

    private static MailOutboxMetrics metrics(MeterRegistry registry) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("meterRegistry", registry);
        return new MailOutboxMetrics(beans.getBeanProvider(MeterRegistry.class));
    }
}
