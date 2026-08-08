package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSpeakingMediaCleanupTask;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface PracticeSpeakingMediaCleanupTaskRepository
        extends JpaRepository<PracticeSpeakingMediaCleanupTask, Long> {

    Optional<PracticeSpeakingMediaCleanupTask> findByStorageProfileCodeAndStorageKey(
            String storageProfileCode, String storageKey);

    @Query("""
            select t.id
            from PracticeSpeakingMediaCleanupTask t
            where ((t.status in (com.ksh.entities.PracticeSpeakingMediaCleanupStatus.PENDING,
                                 com.ksh.entities.PracticeSpeakingMediaCleanupStatus.RETRY)
                    and t.nextAttemptAt <= :now
                    and t.dueAt <= :now)
                or (t.status = com.ksh.entities.PracticeSpeakingMediaCleanupStatus.PROCESSING
                    and t.leaseExpiresAt <= :now))
            order by t.dueAt asc, t.nextAttemptAt asc, t.id asc
            """)
    List<Long> findDueTaskIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PracticeSpeakingMediaCleanupTask t where t.id = :taskId")
    Optional<PracticeSpeakingMediaCleanupTask> findByIdForUpdate(
            @Param("taskId") Long taskId);

    @Modifying
    @Query(value = """
            INSERT INTO practice_speaking_media_cleanup_tasks
                (cleanup_reason, authorization_evidence_id, media_id,
                 storage_provider, storage_profile_code,
                 storage_key, due_at, next_attempt_at, status, attempt_count)
            VALUES
                (:reason, :authorizationEvidenceId, :mediaId, :provider, :profileCode,
                 :storageKey, :dueAt, :nextAttemptAt, 'PENDING', 0)
            ON DUPLICATE KEY UPDATE
                media_id = CASE
                    WHEN VALUES(cleanup_reason) = 'ACTIVATION_COMPENSATION' THEN NULL
                    ELSE COALESCE(media_id, VALUES(media_id)) END,
                due_at = CASE
                    WHEN status IN ('PENDING','RETRY') AND due_at > VALUES(due_at)
                    THEN VALUES(due_at) ELSE due_at END,
                next_attempt_at = CASE
                    WHEN status IN ('PENDING','RETRY')
                         AND VALUES(cleanup_reason) = 'CONSENT_WITHDRAWAL'
                         AND next_attempt_at > VALUES(next_attempt_at)
                    THEN VALUES(next_attempt_at)
                    WHEN status IN ('PENDING','RETRY')
                         AND cleanup_reason IN ('SUPERSEDED_RETENTION','TEMPORARY_EXPIRY')
                         AND VALUES(cleanup_reason) IN
                             ('LOGICAL_DELETE','DISCARD_ATTEMPT','ACTIVATION_COMPENSATION')
                         AND next_attempt_at > VALUES(next_attempt_at)
                    THEN VALUES(next_attempt_at) ELSE next_attempt_at END,
                cleanup_reason = CASE
                    WHEN status IN ('PENDING','RETRY')
                         AND VALUES(cleanup_reason) = 'CONSENT_WITHDRAWAL'
                    THEN VALUES(cleanup_reason)
                    WHEN status IN ('PENDING','RETRY')
                         AND cleanup_reason IN ('SUPERSEDED_RETENTION','TEMPORARY_EXPIRY')
                         AND VALUES(cleanup_reason) IN
                             ('LOGICAL_DELETE','DISCARD_ATTEMPT','ACTIVATION_COMPENSATION')
                    THEN VALUES(cleanup_reason)
                    WHEN status IN ('PENDING','RETRY')
                         AND cleanup_reason IN ('LOGICAL_DELETE','DISCARD_ATTEMPT')
                         AND VALUES(cleanup_reason) = 'ACTIVATION_COMPENSATION'
                    THEN VALUES(cleanup_reason)
                    ELSE cleanup_reason END,
                authorization_evidence_id = CASE
                    WHEN status IN ('PENDING','RETRY')
                         AND VALUES(cleanup_reason) = 'CONSENT_WITHDRAWAL'
                    THEN VALUES(authorization_evidence_id)
                    ELSE authorization_evidence_id END
            """, nativeQuery = true)
    int insertOrKeepExistingExact(
            @Param("reason") String reason,
            @Param("authorizationEvidenceId") String authorizationEvidenceId,
            @Param("mediaId") Long mediaId,
            @Param("provider") String provider,
            @Param("profileCode") String profileCode,
            @Param("storageKey") String storageKey,
            @Param("dueAt") LocalDateTime dueAt,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
