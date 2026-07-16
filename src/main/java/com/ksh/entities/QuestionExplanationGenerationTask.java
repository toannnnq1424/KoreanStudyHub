package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_explanation_generation_tasks")
public class QuestionExplanationGenerationTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_RETRY_WAIT = "RETRY_WAIT";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    @Column(name = "source_question_version_id", nullable = false)
    private Long sourceQuestionVersionId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "error_category", length = 64)
    private String errorCategory;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "manual_retry_count", nullable = false)
    private Integer manualRetryCount;

    @Column(name = "last_retry_requested_by")
    private Long lastRetryRequestedBy;

    @Column(name = "last_retry_requested_at")
    private LocalDateTime lastRetryRequestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected QuestionExplanationGenerationTask() {
    }

    public boolean canClaim(LocalDateTime now) {
        boolean due = nextAttemptAt == null || !nextAttemptAt.isAfter(now);
        boolean available = STATUS_PENDING.equals(status) || STATUS_RETRY_WAIT.equals(status);
        boolean expired = STATUS_PROCESSING.equals(status)
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
        return due && (available || expired);
    }

    public void claim(String owner, LocalDateTime now, LocalDateTime leaseExpiresAt) {
        this.status = STATUS_PROCESSING;
        this.leaseOwner = owner;
        this.leaseExpiresAt = leaseExpiresAt;
        this.attemptCount = attemptCount == null ? 1 : attemptCount + 1;
        this.startedAt = now;
        this.completedAt = null;
    }

    public boolean isOwnedBy(String owner) {
        return STATUS_PROCESSING.equals(status)
                && owner != null
                && owner.equals(leaseOwner);
    }

    public void markSucceeded(LocalDateTime now) {
        this.status = STATUS_SUCCEEDED;
        this.nextAttemptAt = null;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
        this.errorCategory = null;
        this.lastErrorMessage = null;
        this.completedAt = now;
    }

    public void markFailure(String category, String message, boolean retryable,
                            LocalDateTime nextAttemptAt, LocalDateTime now) {
        boolean exhausted = attemptCount != null && maxAttempts != null && attemptCount >= maxAttempts;
        this.status = retryable && !exhausted ? STATUS_RETRY_WAIT : STATUS_FAILED;
        this.nextAttemptAt = retryable && !exhausted ? nextAttemptAt : null;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
        this.errorCategory = category;
        this.lastErrorMessage = truncate(message);
        this.completedAt = STATUS_FAILED.equals(status) ? now : null;
    }

    public void requestManualRetry(Long requestedBy, LocalDateTime now) {
        this.status = STATUS_PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
        this.errorCategory = null;
        this.lastErrorMessage = null;
        this.manualRetryCount = manualRetryCount == null ? 1 : manualRetryCount + 1;
        this.lastRetryRequestedBy = requestedBy;
        this.lastRetryRequestedAt = now;
        this.completedAt = null;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public Long getId() { return id; }
    public Long getArtifactId() { return artifactId; }
    public Long getSourceQuestionVersionId() { return sourceQuestionVersionId; }
    public String getStatus() { return status; }
    public Integer getAttemptCount() { return attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public LocalDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public String getErrorCategory() { return errorCategory; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Integer getManualRetryCount() { return manualRetryCount; }
    public Long getLastRetryRequestedBy() { return lastRetryRequestedBy; }
    public LocalDateTime getLastRetryRequestedAt() { return lastRetryRequestedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
