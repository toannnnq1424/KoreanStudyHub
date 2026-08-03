package com.ksh.features.practice.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectAudioReviewerAccessAuditTest {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @Test
    void writesOnlyBoundedAuthorizedMetadataForExactReviewerAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        DirectAudioReviewerAccessAudit audit = new DirectAudioReviewerAccessAudit(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        audit.recordAuthorized(DirectAudioReviewerAccessAudit.Action.PLAYBACK_OPEN,
                77L, 44L, 55L, 66L, "observation-0001");

        verify(jdbc).update(anyString(), anyString(), eq(77L), eq(44L), eq(55L),
                eq(66L), eq("observation-0001"),
                eq("PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION"),
                eq("PLAYBACK_OPEN"), eq(Timestamp.from(NOW)));
    }

    @Test
    void invalidIdentityOrFailedInsertIsRejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DirectAudioReviewerAccessAudit audit = new DirectAudioReviewerAccessAudit(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> audit.recordAuthorized(
                DirectAudioReviewerAccessAudit.Action.INSPECTION_METADATA,
                0L, 44L, 55L, 66L, "observation-0001"))
                .isInstanceOf(IllegalArgumentException.class);

        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> audit.recordAuthorized(
                DirectAudioReviewerAccessAudit.Action.INSPECTION_METADATA,
                77L, 44L, 55L, 66L, "observation-0001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_FAILED");
    }
}
