package com.ksh.features.mail.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void enqueueNotification_snapshots_message_as_pending_job() {
        MailOutboxRepository repository = mock(MailOutboxRepository.class);
        MailOutboxService service = new MailOutboxService(repository, FIXED_CLOCK);

        service.enqueueNotification(
                42L,
                "student@ksh.edu.vn",
                "[KSH] Bài học mới",
                "Nội dung");

        ArgumentCaptor<MailOutboxJob> captor =
                ArgumentCaptor.forClass(MailOutboxJob.class);
        verify(repository).save(captor.capture());
        MailOutboxJob job = captor.getValue();
        assertThat(job.getNotificationId()).isEqualTo(42L);
        assertThat(job.getRecipientEmail()).isEqualTo("student@ksh.edu.vn");
        assertThat(job.getSubject()).isEqualTo("[KSH] Bài học mới");
        assertThat(job.getBody()).isEqualTo("Nội dung");
        assertThat(job.getSource()).isEqualTo(MailOutboxService.SOURCE_NOTIFICATION);
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();
    }

    @Test
    void enqueueNotification_is_idempotent_for_same_notification() {
        MailOutboxRepository repository = mock(MailOutboxRepository.class);
        when(repository.existsByNotificationId(42L)).thenReturn(true);
        MailOutboxService service = new MailOutboxService(repository, FIXED_CLOCK);

        service.enqueueNotification(42L, "student@ksh.edu.vn", "Subject", "Body");

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enqueueSystemMailPersistsAStandalonePendingJob() {
        MailOutboxRepository repository = mock(MailOutboxRepository.class);
        MailOutboxService service = new MailOutboxService(repository, FIXED_CLOCK);

        service.enqueueSystemMail(
                "owner@example.edu.vn",
                "[KSH] Kích hoạt tài khoản của bạn",
                "Activation body",
                "ACCOUNT_ACTIVATION");

        ArgumentCaptor<MailOutboxJob> captor =
                ArgumentCaptor.forClass(MailOutboxJob.class);
        verify(repository).save(captor.capture());
        MailOutboxJob job = captor.getValue();
        assertThat(job.getNotificationId()).isNull();
        assertThat(job.getRecipientEmail()).isEqualTo("owner@example.edu.vn");
        assertThat(job.getSource()).isEqualTo("ACCOUNT_ACTIVATION");
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
        assertThat(job.getAvailableAt()).isEqualTo(FIXED_CLOCK.instant().atZone(
                ZoneOffset.UTC).toLocalDateTime());
    }
}
