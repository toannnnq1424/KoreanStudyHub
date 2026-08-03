package com.ksh.features.practice.ai.speaking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class DirectAudioAuthorizationJdbcStore
        implements DirectAudioAuthorizationLifecycleService.AuthorizationStore {

    private final JdbcTemplate jdbc;

    public DirectAudioAuthorizationJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DirectAudioAuthorizationLifecycleService.ConsentEvent appendConsent(
            DirectAudioAuthorizationLifecycleService.ConsentEvent event) {
        jdbc.update("""
                INSERT INTO practice_speaking_audio_consent_events
                    (event_key, consent_chain_key, learner_id, attempt_id,
                     purpose_code, event_type, disclosure_version, evidence_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.eventKey(), event.consentChainKey(), event.learnerId(),
                event.attemptId(), DirectAudioAuthorizationLifecycleService.PURPOSE,
                event.eventType().name(), event.disclosureVersion(), event.evidenceId(),
                Timestamp.from(event.occurredAt()));
        return event;
    }

    @Override
    public Optional<DirectAudioAuthorizationLifecycleService.ConsentEvent> latestConsent(
            Long learnerId, Long attemptId, String purpose) {
        return jdbc.query("""
                SELECT event_key, consent_chain_key, learner_id, attempt_id,
                       event_type, disclosure_version, evidence_id, occurred_at
                FROM practice_speaking_audio_consent_events
                WHERE learner_id = ? AND attempt_id = ? AND purpose_code = ?
                ORDER BY occurred_at DESC, id DESC
                LIMIT 1
                """, DirectAudioAuthorizationJdbcStore::consent,
                learnerId, attemptId, purpose).stream().findFirst();
    }

    @Override
    public DirectAudioAuthorizationLifecycleService.ReviewerGrant createReviewerGrant(
            DirectAudioAuthorizationLifecycleService.ReviewerGrant grant) {
        jdbc.update("""
                INSERT INTO practice_speaking_audio_reviewer_grants
                    (grant_key, attempt_id, reviewer_id, granted_by, purpose_code,
                     evidence_id, granted_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, grant.grantKey(), grant.attemptId(), grant.reviewerId(),
                grant.grantedBy(), DirectAudioAuthorizationLifecycleService.PURPOSE,
                grant.evidenceId(), Timestamp.from(grant.grantedAt()),
                Timestamp.from(grant.expiresAt()));
        return grant;
    }

    @Override
    public Optional<DirectAudioAuthorizationLifecycleService.ReviewerGrant>
    reviewerGrantForUpdate(String grantKey) {
        requireTransaction();
        return jdbc.query("""
                SELECT grant_key, attempt_id, reviewer_id, granted_by, evidence_id,
                       granted_at, expires_at, revoked_at, revoked_by, revoke_evidence_id
                FROM practice_speaking_audio_reviewer_grants
                WHERE grant_key = ?
                FOR UPDATE
                """, DirectAudioAuthorizationJdbcStore::grant, grantKey)
                .stream().findFirst();
    }

    @Override
    public DirectAudioAuthorizationLifecycleService.ReviewerGrant revokeReviewerGrant(
            String grantKey, Long actorId, String evidenceId, Instant revokedAt) {
        requireTransaction();
        jdbc.update("""
                UPDATE practice_speaking_audio_reviewer_grants
                SET revoked_at = ?, revoked_by = ?, revoke_evidence_id = ?
                WHERE grant_key = ? AND revoked_at IS NULL
                """, Timestamp.from(revokedAt), actorId, evidenceId, grantKey);
        return reviewerGrantForUpdate(grantKey)
                .orElseThrow(() -> new IllegalStateException("Reviewer grant not found."));
    }

    @Override
    public Optional<DirectAudioAuthorizationLifecycleService.ReviewerGrant>
    activeReviewerGrant(Long reviewerId, Long attemptId, String purpose, Instant now) {
        return jdbc.query("""
                SELECT grant_key, attempt_id, reviewer_id, granted_by, evidence_id,
                       granted_at, expires_at, revoked_at, revoked_by, revoke_evidence_id
                FROM practice_speaking_audio_reviewer_grants
                WHERE reviewer_id = ? AND attempt_id = ? AND purpose_code = ?
                  AND revoked_at IS NULL AND expires_at > ?
                ORDER BY expires_at ASC, id ASC
                LIMIT 1
                """, DirectAudioAuthorizationJdbcStore::grant,
                reviewerId, attemptId, purpose, Timestamp.from(now))
                .stream().findFirst();
    }

    private static DirectAudioAuthorizationLifecycleService.ConsentEvent consent(
            ResultSet rs, int row) throws SQLException {
        return new DirectAudioAuthorizationLifecycleService.ConsentEvent(
                rs.getString("event_key"), rs.getString("consent_chain_key"),
                rs.getLong("learner_id"), rs.getLong("attempt_id"),
                DirectAudioAuthorizationLifecycleService.EventType.valueOf(
                        rs.getString("event_type")),
                rs.getString("disclosure_version"), rs.getString("evidence_id"),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private static DirectAudioAuthorizationLifecycleService.ReviewerGrant grant(
            ResultSet rs, int row) throws SQLException {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        long revokedBy = rs.getLong("revoked_by");
        boolean revokedByMissing = rs.wasNull();
        return new DirectAudioAuthorizationLifecycleService.ReviewerGrant(
                rs.getString("grant_key"), rs.getLong("attempt_id"),
                rs.getLong("reviewer_id"), rs.getLong("granted_by"),
                rs.getString("evidence_id"), rs.getTimestamp("granted_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                revokedByMissing ? null : revokedBy, rs.getString("revoke_evidence_id"));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Reviewer grant mutation requires an active transaction.");
        }
    }
}
