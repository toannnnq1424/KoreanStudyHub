package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_asset_lifecycle_tasks")
public class PracticeAssetLifecycleTask {

    public static final String DELETE = "DELETE";
    public static final String PROMOTE_CLEANUP = "PROMOTE_CLEANUP";
    public static final String ORPHAN_RECONCILE = "ORPHAN_RECONCILE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "storage_profile_code", length = 40)
    private String storageProfileCode;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(name = "source_storage_key", length = 512)
    private String sourceStorageKey;

    @Column(name = "target_storage_key", length = 512)
    private String targetStorageKey;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PracticeAssetLifecycleTask() {
    }

    public PracticeAssetLifecycleTask(Long assetId, String operation,
                                      String sourceStorageKey, String targetStorageKey) {
        this(assetId, null, operation, sourceStorageKey, targetStorageKey);
    }

    public PracticeAssetLifecycleTask(Long assetId, String storageProfileCode,
                                      String operation, String sourceStorageKey,
                                      String targetStorageKey) {
        this.assetId = assetId;
        this.storageProfileCode = storageProfileCode;
        this.operation = operation;
        this.sourceStorageKey = sourceStorageKey;
        this.targetStorageKey = targetStorageKey;
        this.status = "PENDING";
        this.nextAttemptAt = LocalDateTime.now();
    }

    public void markRunning(String claimToken, LocalDateTime leaseExpiresAt) {
        if (claimToken == null || claimToken.isBlank()) {
            throw new IllegalArgumentException("Lifecycle claim token is required.");
        }
        this.status = "RUNNING";
        this.claimToken = claimToken;
        this.nextAttemptAt = leaseExpiresAt;
    }
    public void markCompleted() {
        this.status = "COMPLETED";
        this.lastError = null;
        this.claimToken = null;
        this.nextAttemptAt = null;
    }
    public void markDeferred(String reason, LocalDateTime nextAttemptAt) {
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException(
                    "Lifecycle deferred retry time is required.");
        }
        this.status = "PENDING";
        this.lastError = truncate(reason);
        this.claimToken = null;
        this.nextAttemptAt = nextAttemptAt;
    }
    public void markRetry(String error, LocalDateTime nextAttemptAt, int maxAttempts) {
        this.attemptCount = this.attemptCount == null ? 1 : this.attemptCount + 1;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
        this.claimToken = null;
        this.status = this.attemptCount >= maxAttempts ? "FAILED" : "PENDING";
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public Long getId() { return id; }
    public Long getAssetId() { return assetId; }
    public String getStorageProfileCode() { return storageProfileCode; }
    public String getOperation() { return operation; }
    public String getSourceStorageKey() { return sourceStorageKey; }
    public String getTargetStorageKey() { return targetStorageKey; }
    public String getStatus() { return status; }
    public Integer getAttemptCount() { return attemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getClaimToken() { return claimToken; }
    public String getLastError() { return lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
