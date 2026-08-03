package com.ksh.features.practice.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DirectAudioReviewerAccessAuditRetentionTest {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @Test
    void purgeUsesPersistedDeadlineAndBoundsBatchSize() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(7);
        DirectAudioReviewerAccessAuditRetention retention =
                new DirectAudioReviewerAccessAuditRetention(
                        jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(retention.purgeExpired(10_000)).isEqualTo(7);

        verify(jdbc).update(anyString(), eq(Timestamp.from(NOW)), eq(1_000));
    }

    @Test
    void nonPositiveBatchDoesNothing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DirectAudioReviewerAccessAuditRetention retention =
                new DirectAudioReviewerAccessAuditRetention(
                        jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(retention.purgeExpired(0)).isZero();
        verifyNoInteractions(jdbc);
    }
}
