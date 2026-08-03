package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Resolves only an active direct-audio reviewer grant bound to the exact
 * captured observation and its private learner media object. It never returns
 * a URL, token, consent evidence or storage key to a web caller.
 */
@Repository
public class DirectAudioReviewerPlaybackStore {

    private final JdbcTemplate jdbc;

    public DirectAudioReviewerPlaybackStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PlaybackDescriptor> findAuthorized(
            Long reviewerId, Long attemptId, Long questionId, Long mediaId, Instant now) {
        return jdbc.query("""
                SELECT m.storage_provider, m.storage_profile_code, m.storage_key,
                       m.mime_type, m.byte_size
                FROM practice_speaking_media m
                JOIN practice_attempts a ON a.id = m.attempt_id
                WHERE m.id = ? AND m.attempt_id = ? AND m.question_id = ?
                  AND m.status = 'READY' AND a.skill = 'SPEAKING'
                  AND EXISTS (
                    SELECT 1
                    FROM practice_speaking_audio_consent_events c
                    WHERE c.learner_id = a.user_id AND c.attempt_id = a.id
                      AND c.purpose_code = 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
                      AND c.id = (
                        SELECT c2.id
                        FROM practice_speaking_audio_consent_events c2
                        WHERE c2.learner_id = a.user_id AND c2.attempt_id = a.id
                          AND c2.purpose_code = 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
                        ORDER BY c2.occurred_at DESC, c2.id DESC
                        LIMIT 1)
                      AND c.event_type = 'GRANTED')
                  AND EXISTS (
                    SELECT 1
                    FROM practice_speaking_audio_reviewer_grants g
                    WHERE g.reviewer_id = ? AND g.attempt_id = a.id
                      AND g.purpose_code = 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
                      AND g.revoked_at IS NULL AND g.expires_at > ?)
                  AND EXISTS (
                    SELECT 1
                    FROM practice_speaking_direct_audio_dark_observations o
                    WHERE o.attempt_id = a.id AND o.question_id = m.question_id
                      AND o.media_id = m.id AND o.deleted_at IS NULL
                      AND o.delete_after > ?)
                """, DirectAudioReviewerPlaybackStore::descriptor,
                mediaId, attemptId, questionId, reviewerId,
                Timestamp.from(now), Timestamp.from(now))
                .stream().findFirst();
    }

    private static PlaybackDescriptor descriptor(ResultSet rs, int row) throws SQLException {
        return new PlaybackDescriptor(
                PracticeSpeakingStorageProvider.valueOf(rs.getString("storage_provider")),
                rs.getString("storage_profile_code"), rs.getString("storage_key"),
                rs.getString("mime_type"), rs.getLong("byte_size"));
    }

    public record PlaybackDescriptor(
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey,
            String mimeType,
            long byteSize) {
    }
}
