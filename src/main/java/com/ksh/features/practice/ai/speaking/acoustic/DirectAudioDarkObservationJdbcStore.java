package com.ksh.features.practice.ai.speaking.acoustic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** JDBC boundary whose read query embeds the active named-reviewer grant. */
@Repository
public class DirectAudioDarkObservationJdbcStore
        implements DirectAudioDarkObservationService.Store {

    private final JdbcTemplate jdbc;

    public DirectAudioDarkObservationJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DirectAudioDarkObservationService.StoredObservation insert(
            DirectAudioDarkObservationService.StoredObservation observation) {
        int rows = jdbc.update("""
                INSERT INTO practice_speaking_direct_audio_dark_observations
                    (observation_key, attempt_id, contract_version, language_code,
                     evaluator_id, model_name, calibration_profile_id,
                     calibration_version, receipt_fingerprint,
                     provider_cache_fingerprint, provider_observation_total,
                     provider_confidence, observation_payload, retention_policy_id,
                     captured_at, delete_after)
                SELECT ?, a.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?
                FROM practice_attempts a
                WHERE a.id = ? AND a.skill = 'SPEAKING'
                """, observation.observationKey(), observation.contractVersion(),
                observation.language(), observation.evaluatorId(), observation.model(),
                observation.calibrationProfileId(), observation.calibrationVersion(),
                observation.receiptFingerprint(), observation.providerCacheFingerprint(),
                observation.providerObservationTotal(), observation.providerConfidence(),
                observation.payloadJson(), observation.retentionPolicyId(),
                Timestamp.from(observation.capturedAt()),
                Timestamp.from(observation.deleteAfter()), observation.attemptId());
        if (rows != 1) {
            throw new IllegalStateException("DIRECT_AUDIO_DARK_SPEAKING_ATTEMPT_REQUIRED");
        }
        return observation;
    }

    @Override
    public Optional<DirectAudioDarkObservationService.StoredObservation> findInspectable(
            Long reviewerId, Long attemptId, Instant now) {
        return jdbc.query("""
                SELECT o.observation_key, o.attempt_id, o.contract_version,
                       o.language_code, o.evaluator_id, o.model_name,
                       o.calibration_profile_id, o.calibration_version,
                       o.receipt_fingerprint, o.provider_cache_fingerprint,
                       o.provider_observation_total, o.provider_confidence,
                       o.observation_payload, o.retention_policy_id,
                       o.captured_at, o.delete_after
                FROM practice_speaking_direct_audio_dark_observations o
                WHERE o.attempt_id = ?
                  AND o.deleted_at IS NULL AND o.delete_after > ?
                  AND EXISTS (
                    SELECT 1
                    FROM practice_speaking_audio_reviewer_grants g
                    WHERE g.reviewer_id = ?
                      AND g.attempt_id = o.attempt_id
                      AND g.purpose_code = 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
                      AND g.revoked_at IS NULL AND g.expires_at > ?)
                ORDER BY o.captured_at DESC, o.id DESC
                LIMIT 1
                """, DirectAudioDarkObservationJdbcStore::stored,
                attemptId, Timestamp.from(now), reviewerId, Timestamp.from(now))
                .stream().findFirst();
    }

    private static DirectAudioDarkObservationService.StoredObservation stored(
            ResultSet rs, int row) throws SQLException {
        return new DirectAudioDarkObservationService.StoredObservation(
                rs.getString("observation_key"), rs.getLong("attempt_id"),
                rs.getString("contract_version"), rs.getString("language_code"),
                rs.getString("evaluator_id"), rs.getString("model_name"),
                rs.getString("calibration_profile_id"),
                rs.getString("calibration_version"),
                rs.getString("receipt_fingerprint"),
                rs.getString("provider_cache_fingerprint"),
                rs.getBigDecimal("provider_observation_total"),
                rs.getBigDecimal("provider_confidence"),
                rs.getString("observation_payload"),
                rs.getString("retention_policy_id"),
                rs.getTimestamp("captured_at").toInstant(),
                rs.getTimestamp("delete_after").toInstant());
    }
}
