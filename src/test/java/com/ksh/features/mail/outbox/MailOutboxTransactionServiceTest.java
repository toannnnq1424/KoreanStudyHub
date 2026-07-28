package com.ksh.features.mail.outbox;

import com.ksh.features.notifications.entity.Notification;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxTransactionServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 29, 8, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC);
    private static final String WORKER_ID = "worker-a";

    private MailOutboxRepository outboxRepository;
    private NotificationRepository notificationRepository;
    private MailOutboxTransactionService service;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(MailOutboxRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        service = new MailOutboxTransactionService(
                outboxRepository,
                notificationRepository,
                FIXED_CLOCK);
    }

    @Test
    void claim_locks_due_job_and_returns_transport_snapshot() {
        MailOutboxJob job = pendingJob();
        when(outboxRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));

        Optional<MailOutboxDelivery> result = service.claim(7L, WORKER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().jobId()).isEqualTo(7L);
        assertThat(result.get().recipientEmail()).isEqualTo("student@ksh.edu.vn");
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.PROCESSING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLeaseExpiresAt()).isEqualTo(
                NOW.plus(MailOutboxTransactionService.LEASE_DURATION));
        verify(outboxRepository).save(job);
    }

    @Test
    void restarted_processor_reclaims_an_expired_processing_lease() {
        MailOutboxJob job = pendingJob();
        job.claim(
                "dead-worker",
                NOW.minusMinutes(5),
                Duration.ofMinutes(1));
        when(outboxRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        MailOutboxTransactionService restartedService =
                new MailOutboxTransactionService(
                        outboxRepository,
                        notificationRepository,
                        FIXED_CLOCK);

        Optional<MailOutboxDelivery> result =
                restartedService.claim(7L, WORKER_ID);

        assertThat(result).isPresent();
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.PROCESSING);
    }

    @Test
    void recordSuccess_marks_job_and_originating_notification() {
        MailOutboxJob job = claimedJob();
        Notification notification = new Notification(
                9L,
                "Title",
                "Body",
                NotificationType.LESSON_PUBLISHED,
                NotificationType.REF_LESSON,
                2L);
        when(outboxRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        when(notificationRepository.findById(42L)).thenReturn(Optional.of(notification));

        boolean recorded = service.recordSuccess(7L, WORKER_ID);

        assertThat(recorded).isTrue();
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.SENT);
        assertThat(job.getSentAt()).isEqualTo(NOW);
        assertThat(notification.isEmailSent()).isTrue();
        verify(outboxRepository).save(job);
        verify(notificationRepository).save(notification);
    }

    @Test
    void recordFailure_schedules_exponential_retry_before_attempt_limit() {
        MailOutboxJob job = claimedJob();
        when(outboxRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));

        boolean recorded = service.recordFailure(
                7L,
                WORKER_ID,
                MailOutboxTransactionService.ERROR_DELIVERY_FAILED);

        assertThat(recorded).isTrue();
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.RETRY);
        assertThat(job.getAvailableAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(job.getLastErrorCode()).isEqualTo("delivery_failed");
    }

    @Test
    void recordFailure_moves_exhausted_job_to_failed_dead_letter_state() {
        MailOutboxJob job = pendingJob();
        setField(job, "maxAttempts", 1);
        job.claim(WORKER_ID, NOW.minusSeconds(1), Duration.ofMinutes(2));
        when(outboxRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));

        boolean recorded = service.recordFailure(
                7L,
                WORKER_ID,
                MailOutboxTransactionService.ERROR_DELIVERY_FAILED);

        assertThat(recorded).isTrue();
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo("delivery_failed");
        assertThat(job.getLeaseExpiresAt()).isNull();
    }

    @Test
    void retryDelay_is_capped_at_one_hour() {
        assertThat(MailOutboxTransactionService.retryDelay(1))
                .isEqualTo(Duration.ofMinutes(1));
        assertThat(MailOutboxTransactionService.retryDelay(2))
                .isEqualTo(Duration.ofMinutes(2));
        assertThat(MailOutboxTransactionService.retryDelay(20))
                .isEqualTo(Duration.ofHours(1));
    }

    private MailOutboxJob claimedJob() {
        MailOutboxJob job = pendingJob();
        job.claim(WORKER_ID, NOW.minusSeconds(1), Duration.ofMinutes(2));
        return job;
    }

    private MailOutboxJob pendingJob() {
        MailOutboxJob job = MailOutboxJob.pending(
                42L,
                "student@ksh.edu.vn",
                "Subject",
                "Body",
                MailOutboxService.SOURCE_NOTIFICATION,
                NOW.minusMinutes(10));
        setField(job, "id", 7L);
        return job;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
