package com.ksh.features.practice.repository;

import com.ksh.entities.WritingEvaluationCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface WritingEvaluationCacheRepository extends JpaRepository<WritingEvaluationCacheEntry, String> {

    @Modifying
    @Query("delete from WritingEvaluationCacheEntry e where e.cacheKey = :cacheKey")
    int deleteByCacheKey(@Param("cacheKey") String cacheKey);

    @Modifying
    @Query("delete from WritingEvaluationCacheEntry e where e.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            INSERT INTO practice_writing_evaluation_cache
                (cache_key, user_scope_hash, task_type, model, prompt_version, rubric_version,
                 evaluation_schema_version, result_json, expires_at)
            VALUES
                (:cacheKey, :userScopeHash, :taskType, :model, :promptVersion, :rubricVersion,
                 :evaluationSchemaVersion, :resultJson, :expiresAt)
            ON DUPLICATE KEY UPDATE
                user_scope_hash = VALUES(user_scope_hash),
                task_type = VALUES(task_type),
                model = VALUES(model),
                prompt_version = VALUES(prompt_version),
                rubric_version = VALUES(rubric_version),
                evaluation_schema_version = VALUES(evaluation_schema_version),
                result_json = VALUES(result_json),
                expires_at = VALUES(expires_at),
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(@Param("cacheKey") String cacheKey,
               @Param("userScopeHash") String userScopeHash,
               @Param("taskType") String taskType,
               @Param("model") String model,
               @Param("promptVersion") String promptVersion,
               @Param("rubricVersion") String rubricVersion,
               @Param("evaluationSchemaVersion") String evaluationSchemaVersion,
               @Param("resultJson") String resultJson,
               @Param("expiresAt") LocalDateTime expiresAt);
}
