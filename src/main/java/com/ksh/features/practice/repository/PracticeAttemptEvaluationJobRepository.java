package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAttemptEvaluationJob;
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

public interface PracticeAttemptEvaluationJobRepository
        extends JpaRepository<PracticeAttemptEvaluationJob, Long> {

    Optional<PracticeAttemptEvaluationJob> findByAttemptId(Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PracticeAttemptEvaluationJob j where j.id = :id")
    Optional<PracticeAttemptEvaluationJob> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PracticeAttemptEvaluationJob j where j.attemptId = :attemptId")
    Optional<PracticeAttemptEvaluationJob> findByAttemptIdForUpdate(
            @Param("attemptId") Long attemptId);

    @Query("""
            select j.id from PracticeAttemptEvaluationJob j
            where (
                (j.jobStatus in ('QUEUED', 'RETRY_WAIT')
                    and (j.nextAttemptAt is null or j.nextAttemptAt <= :now))
                or (j.jobStatus = 'PROCESSING'
                    and j.leaseExpiresAt is not null
                    and j.leaseExpiresAt <= :now)
            )
            order by j.id asc
            """)
    List<Long> findClaimableIds(
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO practice_attempt_evaluation_jobs (
                attempt_id, operation, target_question_id, input_fingerprint,
                evaluation_contract_identity, job_status, attempt_count,
                max_attempts, next_attempt_at,
                expires_at, retryable, requested_by, manual_retry_count,
                completed_at, error_code
            ) VALUES (
                :attemptId, :operation, :targetQuestionId, :inputFingerprint,
                :evaluationContractIdentity, :status, 0, :maxAttempts,
                CASE WHEN :status IN ('FAILED', 'UNAVAILABLE', 'SUCCEEDED')
                     THEN NULL ELSE :nextAttemptAt END,
                :expiresAt, FALSE, :requestedBy, 0,
                CASE WHEN :status IN ('FAILED', 'UNAVAILABLE', 'SUCCEEDED')
                     THEN CURRENT_TIMESTAMP ELSE NULL END,
                :errorCode
            )
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("attemptId") Long attemptId,
            @Param("operation") String operation,
            @Param("targetQuestionId") Long targetQuestionId,
            @Param("inputFingerprint") String inputFingerprint,
            @Param("evaluationContractIdentity")
                    String evaluationContractIdentity,
            @Param("status") String status,
            @Param("maxAttempts") int maxAttempts,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("requestedBy") Long requestedBy,
            @Param("errorCode") String errorCode);
}
