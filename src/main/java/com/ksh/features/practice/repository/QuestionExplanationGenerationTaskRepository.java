package com.ksh.features.practice.repository;

import com.ksh.entities.QuestionExplanationGenerationTask;
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

public interface QuestionExplanationGenerationTaskRepository
        extends JpaRepository<QuestionExplanationGenerationTask, Long> {

    Optional<QuestionExplanationGenerationTask> findByArtifactId(Long artifactId);

    @Query("""
            select t.id from QuestionExplanationGenerationTask t
            where (
                (t.status in ('PENDING', 'RETRY_WAIT')
                    and (t.nextAttemptAt is null or t.nextAttemptAt <= :now))
                or (t.status = 'PROCESSING'
                    and t.leaseExpiresAt is not null and t.leaseExpiresAt <= :now)
            )
            order by t.id asc
            """)
    List<Long> findClaimableIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from QuestionExplanationGenerationTask t where t.id = :id")
    Optional<QuestionExplanationGenerationTask> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from QuestionExplanationGenerationTask t where t.artifactId = :artifactId")
    Optional<QuestionExplanationGenerationTask> findByArtifactIdForUpdate(@Param("artifactId") Long artifactId);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO question_explanation_generation_tasks (
                artifact_id, source_question_version_id, status, attempt_count,
                max_attempts, next_attempt_at, manual_retry_count
            ) VALUES (:artifactId, :sourceQuestionVersionId, 'PENDING', 0, :maxAttempts,
                      CURRENT_TIMESTAMP, 0)
            """, nativeQuery = true)
    int insertPendingIfAbsent(@Param("artifactId") Long artifactId,
                              @Param("sourceQuestionVersionId") Long sourceQuestionVersionId,
                              @Param("maxAttempts") int maxAttempts);
}
