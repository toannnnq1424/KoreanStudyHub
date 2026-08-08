package com.ksh.features.practice.service;

import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable metadata-only audit. A failed write prevents the authorized read. */
@Service
public class DirectAudioReviewerAccessAudit {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String retentionPolicyId;
    private final Duration retention;

    @Autowired
    public DirectAudioReviewerAccessAudit(
            JdbcTemplate jdbc,
            ObjectProvider<Clock> clockProvider,
            @Value("${app.practice.speaking-direct-audio.reviewer-access-audit.retention-policy-id:}")
            String retentionPolicyId,
            @Value("${app.practice.speaking-direct-audio.reviewer-access-audit.retention:PT0S}")
            Duration retention) {
        this(jdbc, clockProvider.getIfAvailable(Clock::systemUTC),
                retentionPolicyId, retention);
    }

    DirectAudioReviewerAccessAudit(
            JdbcTemplate jdbc,
            Clock clock,
            String retentionPolicyId,
            Duration retention) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
        this.retentionPolicyId = retentionPolicyId == null
                ? "" : retentionPolicyId.trim();
        this.retention = retention;
    }

    public void recordAuthorized(
            Action action,
            Long reviewerId,
            Long attemptId,
            Long questionId,
            Long mediaId,
            String observationKey) {
        requireIdentity(reviewerId);
        requireIdentity(attemptId);
        requireIdentity(questionId);
        requireIdentity(mediaId);
        if (action == null || observationKey == null || observationKey.isBlank()
                || observationKey.length() > 80) {
            throw new IllegalArgumentException("Reviewer access audit identity is invalid.");
        }
        Instant now = clock.instant();
        Instant deleteAfter = retentionDeadline(now);
        int rows = jdbc.update("""
                INSERT INTO practice_speaking_audio_reviewer_access_events
                    (event_key, reviewer_id, attempt_id, question_id, media_id,
                     observation_key, purpose_code, action_code, outcome_code,
                     retention_policy_id, occurred_at, delete_after)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AUTHORIZED', ?, ?, ?)
                """, UUID.randomUUID().toString(), reviewerId, attemptId, questionId,
                mediaId, observationKey, DirectAudioSpeakingEvaluationService.PURPOSE,
                action.name(), retentionPolicyId, Timestamp.from(now),
                Timestamp.from(deleteAfter));
        if (rows != 1) {
            throw new IllegalStateException("DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_FAILED");
        }
    }

    private Instant retentionDeadline(Instant now) {
        if (!retentionPolicyId.matches("[A-Z0-9][A-Z0-9._-]{2,79}")
                || retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalStateException(
                    "DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_RETENTION_NOT_READY");
        }
        try {
            return now.plus(retention);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_RETENTION_NOT_READY",
                    exception);
        }
    }

    private static void requireIdentity(Long value) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("Reviewer access audit identity is invalid.");
        }
    }

    public enum Action {
        INSPECTION_METADATA,
        PLAYBACK_OPEN
    }
}
