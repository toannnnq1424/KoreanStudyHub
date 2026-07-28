package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpeakingPromptAiTaskRepository
        extends JpaRepository<SpeakingPromptAiTask, Long> {

    @Query("""
            select t.id from SpeakingPromptAiTask t
            where (
                (t.taskStatus in ('queued', 'retry_wait')
                    and (t.nextAttemptAt is null or t.nextAttemptAt <= :now))
                or (t.taskStatus = 'processing'
                    and t.leaseExpiresAt is not null
                    and t.leaseExpiresAt <= :now)
            )
            order by t.id asc
            """)
    List<Long> findClaimableIds(
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SpeakingPromptAiTask t where t.id = :id")
    Optional<SpeakingPromptAiTask> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select t from SpeakingPromptAiTask t
            where t.ownerLecturerId = :ownerId
              and t.operation = :operation
              and t.operationFingerprint = :fingerprint
              and t.taskStatus in ('queued', 'processing', 'retry_wait')
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SpeakingPromptAiTask> findActiveByFingerprint(
            @Param("ownerId") Long ownerId,
            @Param("operation") String operation,
            @Param("fingerprint") String fingerprint);

    @Query("""
            select t from SpeakingPromptAiTask t
            where t.ownerLecturerId = :ownerId
              and t.operation = :operation
              and t.operationFingerprint = :fingerprint
            order by t.id desc
            """)
    List<SpeakingPromptAiTask> findLatestByFingerprint(
            @Param("ownerId") Long ownerId,
            @Param("operation") String operation,
            @Param("fingerprint") String fingerprint,
            Pageable pageable);

    @Query("""
            select count(t) from SpeakingPromptAiTask t
            where t.ownerLecturerId = :ownerId
              and t.taskStatus = 'processing'
              and t.id <> :taskId
            """)
    long countProcessingByOwnerExcluding(
            @Param("ownerId") Long ownerId,
            @Param("taskId") Long taskId);

    @Query("""
            select count(t) from SpeakingPromptAiTask t
            where t.taskStatus = 'processing'
              and t.id <> :taskId
              and exists (
                  select s.id from SpeakingPromptSource s
                  where s.draftId = :draftId
                    and s.ownerLecturerId = t.ownerLecturerId
                    and (
                        (t.operation = 'stt'
                            and s.inputType = 'audio_upload'
                            and s.currentSttArtifactId = t.artifactId)
                        or
                        (t.operation = 'tts'
                            and s.inputType = 'manual_text'
                            and s.ttsEnabled = true
                            and s.currentTtsArtifactId = t.artifactId)
                    )
              )
            """)
    /*
     * A deduplicated task may execute on behalf of a source other than its
     * historical source_id. Charge every draft with a still-current
     * operation-specific attachment, not only the task's origin draft.
     */
    long countProcessingByDraftExcluding(
            @Param("draftId") Long draftId,
            @Param("taskId") Long taskId);

    @Query("""
            select count(t) from SpeakingPromptAiTask t
            where t.ownerLecturerId = :ownerId
              and t.startedAt >= :windowStart
            """)
    /*
     * Exact under the one-claimed-row/one-provider-call model. Successor rows
     * are not counted until claimed, regardless of cumulative attempt_count.
     */
    long countProviderAttemptsSince(
            @Param("ownerId") Long ownerId,
            @Param("windowStart") LocalDateTime windowStart);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO practice_speaking_prompt_ai_tasks (
                artifact_id, source_id, owner_lecturer_id, operation,
                source_input_type, operation_fingerprint,
                expected_source_revision, task_status, attempt_count,
                max_attempts, next_attempt_at, retryable, requested_by
            ) VALUES (
                :artifactId, :sourceId, :ownerId, :operation,
                :sourceInputType, :fingerprint,
                :sourceRevision, 'queued', 0,
                :maxAttempts, CURRENT_TIMESTAMP, 0, :requestedBy
            )
            """, nativeQuery = true)
    int insertQueuedIfNoActive(
            @Param("artifactId") Long artifactId,
            @Param("sourceId") Long sourceId,
            @Param("ownerId") Long ownerId,
            @Param("operation") String operation,
            @Param("sourceInputType") String sourceInputType,
            @Param("fingerprint") String fingerprint,
            @Param("sourceRevision") long sourceRevision,
            @Param("maxAttempts") int maxAttempts,
            @Param("requestedBy") Long requestedBy);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO practice_speaking_prompt_ai_tasks (
                artifact_id, source_id, owner_lecturer_id, operation,
                source_input_type, operation_fingerprint,
                expected_source_revision, task_status, attempt_count,
                max_attempts, next_attempt_at, retryable, requested_by
            ) VALUES (
                :artifactId, :sourceId, :ownerId, :operation,
                :sourceInputType, :fingerprint,
                :sourceRevision, 'queued', :attemptCount,
                :maxAttempts, :nextAttemptAt, 0, :requestedBy
            )
            """, nativeQuery = true)
    int insertRetrySuccessor(
            @Param("artifactId") Long artifactId,
            @Param("sourceId") Long sourceId,
            @Param("ownerId") Long ownerId,
            @Param("operation") String operation,
            @Param("sourceInputType") String sourceInputType,
            @Param("fingerprint") String fingerprint,
            @Param("sourceRevision") long sourceRevision,
            @Param("attemptCount") int attemptCount,
            @Param("maxAttempts") int maxAttempts,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("requestedBy") Long requestedBy);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO practice_speaking_prompt_ai_tasks (
                artifact_id, source_id, owner_lecturer_id, operation,
                source_input_type, operation_fingerprint,
                expected_source_revision, task_status, attempt_count,
                max_attempts, next_attempt_at, retryable,
                public_error_category, requested_by
            ) VALUES (
                :artifactId, :sourceId, :ownerId, :operation,
                :sourceInputType, :fingerprint,
                :sourceRevision, 'queued', :maxAttempts,
                :maxAttempts, :nextAttemptAt, 0,
                :publicErrorCategory, :requestedBy
            )
            """, nativeQuery = true)
    int insertOutcomeReconciler(
            @Param("artifactId") Long artifactId,
            @Param("sourceId") Long sourceId,
            @Param("ownerId") Long ownerId,
            @Param("operation") String operation,
            @Param("sourceInputType") String sourceInputType,
            @Param("fingerprint") String fingerprint,
            @Param("sourceRevision") long sourceRevision,
            @Param("maxAttempts") int maxAttempts,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("publicErrorCategory") String publicErrorCategory,
            @Param("requestedBy") Long requestedBy);

    boolean existsByArtifactIdAndTaskStatusIn(
            Long artifactId, java.util.Collection<String> taskStatuses);

    void deleteByArtifactId(Long artifactId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from SpeakingPromptAiTask t
            where t.sourceId in :sourceIds
            order by t.id asc
            """)
    List<SpeakingPromptAiTask> findBySourceIdsForUpdate(
            @Param("sourceIds") List<Long> sourceIds);
}
