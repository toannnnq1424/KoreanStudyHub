package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "practice_attempt_evaluation_jobs")
public class PracticeAttemptEvaluationJob {

    public static final int MAX_MANUAL_RETRY_REQUESTS = 2;
    public static final int MAX_EVALUATION_CONTRACT_IDENTITY_LENGTH = 1024;

    public static final String OPERATION_SUBMIT = "SUBMIT";
    public static final String OPERATION_FULL_REEVALUATE = "FULL_REEVALUATE";
    public static final String OPERATION_QUESTION_REEVALUATE = "QUESTION_REEVALUATE";

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_RETRY_WAIT = "RETRY_WAIT";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(name = "target_question_id")
    private Long targetQuestionId;

    @Column(name = "input_fingerprint", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String inputFingerprint;

    @Column(name = "evaluation_contract_identity", nullable = false,
            length = MAX_EVALUATION_CONTRACT_IDENTITY_LENGTH)
    private String evaluationContractIdentity;

    @Column(name = "job_status", nullable = false, length = 24)
    private String jobStatus;

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

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "manual_retry_count", nullable = false)
    private Integer manualRetryCount;

    @Column(name = "last_retry_requested_at")
    private LocalDateTime lastRetryRequestedAt;

    @Column(name = "result_json", columnDefinition = "JSON")
    private String resultJson;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PracticeAttemptEvaluationJob() {
    }

    public boolean canClaim(LocalDateTime now) {
        if (now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            return false;
        }
        boolean due = nextAttemptAt == null || !nextAttemptAt.isAfter(now);
        boolean available = STATUS_QUEUED.equals(jobStatus)
                || STATUS_RETRY_WAIT.equals(jobStatus);
        boolean expiredLease = STATUS_PROCESSING.equals(jobStatus)
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
        return due && (available || expiredLease) && !attemptsExhausted();
    }

    public boolean attemptsExhausted() {
        return attemptCount != null && maxAttempts != null
                && attemptCount >= maxAttempts;
    }

    public boolean expired(LocalDateTime now) {
        return now != null && expiresAt != null && !expiresAt.isAfter(now);
    }

    public void claim(String owner, LocalDateTime now, LocalDateTime leaseUntil) {
        if (!canClaim(now)) {
            throw new IllegalStateException("Evaluation job is not claimable.");
        }
        if (owner == null || owner.isBlank() || owner.length() > 100) {
            throw new IllegalArgumentException("Evaluation lease owner is invalid.");
        }
        jobStatus = STATUS_PROCESSING;
        leaseOwner = owner;
        leaseExpiresAt = Objects.requireNonNull(leaseUntil, "leaseUntil");
        attemptCount = attemptCount == null ? 1 : attemptCount + 1;
        startedAt = Objects.requireNonNull(now, "now");
        nextAttemptAt = null;
        completedAt = null;
        retryable = false;
        errorCode = null;
        lastErrorMessage = null;
    }

    public boolean ownsLease(String owner) {
        return STATUS_PROCESSING.equals(jobStatus)
                && owner != null && owner.equals(leaseOwner);
    }

    public boolean ownsActiveLease(
            String owner, LocalDateTime now) {
        return ownsLease(owner)
                && now != null
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }

    public void renewLease(
            String owner,
            LocalDateTime now,
            LocalDateTime leaseUntil) {
        if (!ownsActiveLease(owner, now) || expired(now)) {
            throw new IllegalStateException(
                    "Evaluation lease is no longer renewable.");
        }
        LocalDateTime boundedLeaseUntil =
                Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!boundedLeaseUntil.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Evaluation lease renewal must extend beyond now.");
        }
        if (expiresAt != null && boundedLeaseUntil.isAfter(expiresAt)) {
            boundedLeaseUntil = expiresAt;
        }
        leaseExpiresAt = boundedLeaseUntil;
    }

    public void markTerminal(String terminalStatus, String resultJson,
                             String errorCode, boolean retryable,
                             LocalDateTime now) {
        if (!STATUS_SUCCEEDED.equals(terminalStatus)
                && !STATUS_FAILED.equals(terminalStatus)
                && !STATUS_UNAVAILABLE.equals(terminalStatus)) {
            throw new IllegalArgumentException("Evaluation terminal status is invalid.");
        }
        jobStatus = terminalStatus;
        this.resultJson = resultJson;
        this.errorCode = truncate(errorCode, 100);
        this.retryable = retryable && !attemptsExhausted();
        nextAttemptAt = null;
        leaseOwner = null;
        leaseExpiresAt = null;
        completedAt = Objects.requireNonNull(now, "now");
    }

    public void markFailure(String code, String message, boolean retryable,
                            LocalDateTime retryAt, LocalDateTime now) {
        boolean mayRetry = retryable && !attemptsExhausted() && !expired(now);
        jobStatus = mayRetry ? STATUS_RETRY_WAIT : STATUS_FAILED;
        errorCode = truncate(code, 100);
        lastErrorMessage = truncate(message, 500);
        this.retryable = mayRetry;
        nextAttemptAt = mayRetry ? retryAt : null;
        leaseOwner = null;
        leaseExpiresAt = null;
        completedAt = mayRetry ? null : now;
    }

    public void request(String operation, Long targetQuestionId,
                        String inputFingerprint,
                        String evaluationContractIdentity,
                        Long requestedBy,
                        LocalDateTime now, LocalDateTime expiresAt) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.targetQuestionId = targetQuestionId;
        this.inputFingerprint = Objects.requireNonNull(
                inputFingerprint, "inputFingerprint");
        this.evaluationContractIdentity = requireContractIdentity(
                evaluationContractIdentity);
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.jobStatus = STATUS_QUEUED;
        this.attemptCount = 0;
        this.maxAttempts = maxAttempts == null ? 3 : maxAttempts;
        this.nextAttemptAt = now;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.retryable = false;
        this.errorCode = null;
        this.lastErrorMessage = null;
        this.resultJson = null;
        this.startedAt = null;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
        this.completedAt = null;
    }

    public void requestManualRetry(String operation, Long targetQuestionId,
                                   String inputFingerprint,
                                   String evaluationContractIdentity,
                                   Long requestedBy,
                                   LocalDateTime now, LocalDateTime expiresAt) {
        if (manualRetryLimitReached()) {
            throw new IllegalStateException(
                    "Evaluation manual retry limit is exhausted.");
        }
        request(operation, targetQuestionId, inputFingerprint,
                evaluationContractIdentity, requestedBy, now, expiresAt);
        manualRetryCount = manualRetryCount == null ? 1 : manualRetryCount + 1;
        lastRetryRequestedAt = now;
    }

    public boolean manualRetryLimitReached() {
        return manualRetryCount != null
                && manualRetryCount >= MAX_MANUAL_RETRY_REQUESTS;
    }

    private static String requireContractIdentity(String value) {
        if (value == null
                || value.isBlank()
                || value.length()
                > MAX_EVALUATION_CONTRACT_IDENTITY_LENGTH) {
            throw new IllegalArgumentException(
                    "Evaluation contract identity is invalid.");
        }
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public String getOperation() { return operation; }
    public Long getTargetQuestionId() { return targetQuestionId; }
    public String getInputFingerprint() { return inputFingerprint; }
    public String getEvaluationContractIdentity() {
        return evaluationContractIdentity;
    }
    public String getJobStatus() { return jobStatus; }
    public Integer getAttemptCount() { return attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public LocalDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isRetryable() { return retryable; }
    public String getErrorCode() { return errorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Long getRequestedBy() { return requestedBy; }
    public Integer getManualRetryCount() { return manualRetryCount; }
    public LocalDateTime getLastRetryRequestedAt() { return lastRetryRequestedAt; }
    public String getResultJson() { return resultJson; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
