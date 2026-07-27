package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "practice_speaking_prompt_ai_tasks")
public class SpeakingPromptAiTask {

    /*
     * A row is claimed for at most one provider call. Any automatic, manual
     * or expired-lease retry is a successor row carrying cumulative
     * attemptCount; an attempted row is always terminal.
     */

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_RETRY_WAIT = "retry_wait";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SUPERSEDED = "superseded";
    public static final String STATUS_CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "owner_lecturer_id", nullable = false)
    private Long ownerLecturerId;

    @Column(nullable = false, length = 16)
    private String operation;

    @Column(name = "source_input_type", nullable = false, length = 32)
    private String sourceInputType;

    @Column(name = "operation_fingerprint", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String operationFingerprint;

    @Column(name = "expected_source_revision", nullable = false)
    private Long expectedSourceRevision;

    @Column(name = "task_status", nullable = false, length = 32)
    private String taskStatus;

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

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "public_error_category", length = 64)
    private String publicErrorCategory;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "active_fingerprint_key", insertable = false, updatable = false,
            length = 128)
    private String activeFingerprintKey;

    protected SpeakingPromptAiTask() {
    }

    public boolean canClaim(LocalDateTime now) {
        if (now == null) {
            return false;
        }
        boolean due = nextAttemptAt == null || !nextAttemptAt.isAfter(now);
        boolean available = STATUS_QUEUED.equals(taskStatus)
                || STATUS_RETRY_WAIT.equals(taskStatus);
        boolean expired = STATUS_PROCESSING.equals(taskStatus)
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
        return due && (available || expired);
    }

    public boolean attemptsExhausted() {
        return attemptCount != null
                && maxAttempts != null
                && attemptCount >= maxAttempts;
    }

    public boolean hasExpiredProcessingLease(LocalDateTime now) {
        return STATUS_PROCESSING.equals(taskStatus)
                && leaseExpiresAt != null
                && now != null
                && !leaseExpiresAt.isAfter(now);
    }

    public void defer(
            SpeakingPromptAiContract.PublicErrorCategory category,
            LocalDateTime nextAttempt) {
        taskStatus = STATUS_RETRY_WAIT;
        nextAttemptAt = Objects.requireNonNull(nextAttempt, "nextAttempt");
        leaseOwner = null;
        leaseExpiresAt = null;
        retryable = true;
        publicErrorCategory = Objects.requireNonNull(category, "category").name();
    }

    public void claim(
            String claimToken,
            LocalDateTime now,
            LocalDateTime expiresAt) {
        if ((!STATUS_QUEUED.equals(taskStatus)
                && !STATUS_RETRY_WAIT.equals(taskStatus))
                || attemptsExhausted()) {
            throw new IllegalStateException(
                    "An attempted Speaking prompt task row cannot be claimed again.");
        }
        if (claimToken == null
                || claimToken.isBlank()
                || claimToken.length() > 100) {
            throw new IllegalArgumentException("Task claim token is invalid.");
        }
        taskStatus = STATUS_PROCESSING;
        leaseOwner = claimToken;
        leaseExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        attemptCount = attemptCount == null ? 1 : attemptCount + 1;
        startedAt = Objects.requireNonNull(now, "now");
        nextAttemptAt = null;
        completedAt = null;
        retryable = false;
        publicErrorCategory = null;
    }

    public boolean ownsLiveLease(String claimToken, LocalDateTime now) {
        return STATUS_PROCESSING.equals(taskStatus)
                && claimToken != null
                && claimToken.equals(leaseOwner)
                && leaseExpiresAt != null
                && now != null
                && leaseExpiresAt.isAfter(now);
    }

    public void markSucceeded(LocalDateTime now) {
        taskStatus = STATUS_SUCCEEDED;
        clearLease();
        retryable = false;
        publicErrorCategory = null;
        completedAt = now;
    }

    public void markFailure(
            SpeakingPromptAiContract.PublicErrorCategory category,
            boolean successorAllowed,
            LocalDateTime now) {
        taskStatus = STATUS_FAILED;
        nextAttemptAt = null;
        leaseOwner = null;
        leaseExpiresAt = null;
        retryable = successorAllowed && !attemptsExhausted();
        publicErrorCategory = Objects.requireNonNull(category, "category").name();
        completedAt = Objects.requireNonNull(now, "now");
    }

    public void markSuperseded(LocalDateTime now) {
        taskStatus = STATUS_SUPERSEDED;
        clearLease();
        retryable = false;
        publicErrorCategory =
                SpeakingPromptAiContract.PublicErrorCategory.STALE_COMPLETION.name();
        completedAt = now;
    }

    public void markAbandonedBeforeProvider(LocalDateTime now) {
        markSuperseded(now);
        startedAt = null;
    }

    public void markCancelled(LocalDateTime now) {
        taskStatus = STATUS_CANCELLED;
        clearLease();
        retryable = false;
        publicErrorCategory = null;
        completedAt = Objects.requireNonNull(now, "now");
    }

    public void detachDeletedSource(Long expectedSourceId, Long expectedOwnerId) {
        if (!Objects.equals(sourceId, expectedSourceId)
                || !Objects.equals(ownerLecturerId, expectedOwnerId)) {
            throw new IllegalArgumentException(
                    "Task/source teardown identity does not match.");
        }
        sourceId = null;
    }

    private void clearLease() {
        nextAttemptAt = null;
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    public Long getId() { return id; }
    public Long getArtifactId() { return artifactId; }
    public Long getSourceId() { return sourceId; }
    public Long getOwnerLecturerId() { return ownerLecturerId; }
    public String getOperation() { return operation; }
    public String getSourceInputType() { return sourceInputType; }
    String getOperationFingerprint() { return operationFingerprint; }
    public Long getExpectedSourceRevision() { return expectedSourceRevision; }
    public String getTaskStatus() { return taskStatus; }
    public Integer getAttemptCount() { return attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    String getLeaseOwner() { return leaseOwner; }
    public LocalDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public boolean isRetryable() { return retryable; }
    public String getPublicErrorCategory() { return publicErrorCategory; }
    public Long getRequestedBy() { return requestedBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "SpeakingPromptAiTask{id=" + id
                + ", operation='" + operation + '\''
                + ", taskStatus='" + taskStatus + '\''
                + ", attemptCount=" + attemptCount
                + '}';
    }
}
