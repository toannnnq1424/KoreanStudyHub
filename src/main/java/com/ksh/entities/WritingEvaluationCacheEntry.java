package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_writing_evaluation_cache")
public class WritingEvaluationCacheEntry {

    @Id
    @Column(name = "cache_key", nullable = false, columnDefinition = "CHAR(64)")
    private String cacheKey;

    @Column(name = "user_scope_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String userScopeHash;

    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "rubric_version", nullable = false, length = 32)
    private String rubricVersion;

    @Column(name = "evaluation_schema_version", nullable = false, length = 32)
    private String evaluationSchemaVersion;

    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected WritingEvaluationCacheEntry() {
    }

    public WritingEvaluationCacheEntry(String cacheKey, String userScopeHash, String taskType, String model,
                                       String promptVersion, String rubricVersion,
                                       String evaluationSchemaVersion, String resultJson,
                                       LocalDateTime expiresAt) {
        this.cacheKey = cacheKey;
        this.userScopeHash = userScopeHash;
        this.taskType = taskType;
        this.model = model;
        this.promptVersion = promptVersion;
        this.rubricVersion = rubricVersion;
        this.evaluationSchemaVersion = evaluationSchemaVersion;
        this.resultJson = resultJson;
        this.expiresAt = expiresAt;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public String getUserScopeHash() {
        return userScopeHash;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getRubricVersion() {
        return rubricVersion;
    }

    public String getEvaluationSchemaVersion() {
        return evaluationSchemaVersion;
    }

    public String getResultJson() {
        return resultJson;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
