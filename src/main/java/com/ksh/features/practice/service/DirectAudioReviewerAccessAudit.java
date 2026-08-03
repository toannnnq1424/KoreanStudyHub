package com.ksh.features.practice.service;

import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable metadata-only audit. A failed write prevents the authorized read. */
@Service
public class DirectAudioReviewerAccessAudit {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public DirectAudioReviewerAccessAudit(
            JdbcTemplate jdbc, ObjectProvider<Clock> clockProvider) {
        this(jdbc, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    DirectAudioReviewerAccessAudit(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
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
        int rows = jdbc.update("""
                INSERT INTO practice_speaking_audio_reviewer_access_events
                    (event_key, reviewer_id, attempt_id, question_id, media_id,
                     observation_key, purpose_code, action_code, outcome_code, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AUTHORIZED', ?)
                """, UUID.randomUUID().toString(), reviewerId, attemptId, questionId,
                mediaId, observationKey, DirectAudioSpeakingEvaluationService.PURPOSE,
                action.name(), Timestamp.from(now));
        if (rows != 1) {
            throw new IllegalStateException("DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_FAILED");
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
