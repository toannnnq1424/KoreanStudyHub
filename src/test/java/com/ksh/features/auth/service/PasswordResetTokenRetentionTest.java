package com.ksh.features.auth.service;

import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetTokenRetentionTest {

    @Test
    void springContextUsesTheValueBackedRuntimeConstructor() {
        PasswordResetTokenRepository repository = mock(PasswordResetTokenRepository.class);

        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withBean(PasswordResetTokenRepository.class, () -> repository)
                .withUserConfiguration(PasswordResetTokenRetention.class)
                .withPropertyValues(
                        "app.auth.password-reset.retention.batch-size=250",
                        "app.auth.password-reset.retention.age=P7D")
                .run(context -> assertThat(context)
                        .hasSingleBean(PasswordResetTokenRetention.class));
    }

    @Test
    void cleanupDeletesOnlyOneConfiguredBoundedBatch() {
        PasswordResetTokenRepository repository = mock(PasswordResetTokenRepository.class);
        when(repository.findRetentionIds(any(), any())).thenReturn(List.of(3L, 4L));
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);
        PasswordResetTokenRetention retention =
                new PasswordResetTokenRetention(repository, clock, 250, Duration.ofDays(7));

        assertThat(retention.cleanup()).isEqualTo(2);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findRetentionIds(cutoff.capture(), page.capture());
        assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 7, 22, 0, 0));
        assertThat(page.getValue().getPageSize()).isEqualTo(250);
        verify(repository).deleteAllByIdInBatch(List.of(3L, 4L));
    }

    @Test
    void cleanupCapsMisconfiguredBatchSize() {
        PasswordResetTokenRepository repository = mock(PasswordResetTokenRepository.class);
        PasswordResetTokenRetention retention = new PasswordResetTokenRetention(
                repository, Clock.systemUTC(), Integer.MAX_VALUE, Duration.ofDays(7));

        retention.cleanup();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findRetentionIds(any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(1_000);
        verify(repository, never()).deleteAllByIdInBatch(any());
    }
}
