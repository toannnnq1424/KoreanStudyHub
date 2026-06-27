package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks each AI analysis quota usage. One row per analysis request.
 * Used to enforce the daily 10-request limit per user.
 */
@Entity
@Table(name = "practice_ai_analysis_usage")
public class PracticeAiAnalysisUsage {

    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED_BEFORE_PROVIDER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(nullable = false, length = 20)
    private String skill;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 50)
    private String provider;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PracticeAiAnalysisUsage() {
    }

    public PracticeAiAnalysisUsage(Long userId, Long attemptId, String skill,
                                    LocalDate usageDate, String requestKey) {
        this.userId = userId;
        this.attemptId = attemptId;
        this.skill = skill;
        this.usageDate = usageDate;
        this.requestKey = requestKey;
        this.status = STATUS_RESERVED;
        this.startedAt = LocalDateTime.now();
    }

    public void markSucceeded(String provider, String modelName) {
        this.status = STATUS_SUCCEEDED;
        this.provider = provider;
        this.modelName = modelName;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String failureCode, String failureMessage) {
        this.status = STATUS_FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = STATUS_CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    // --- Getters ---
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAttemptId() { return attemptId; }
    public String getSkill() { return skill; }
    public LocalDate getUsageDate() { return usageDate; }
    public String getRequestKey() { return requestKey; }
    public String getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getModelName() { return modelName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
