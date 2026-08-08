package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "practice_speaking_media_cleanup_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_psm_cleanup_profile_storage",
                columnNames = {"storage_profile_code", "storage_key"})
)
public class PracticeSpeakingMediaCleanupTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanup_reason", nullable = false, length = 40)
    private PracticeSpeakingMediaCleanupReason cleanupReason;

    @Column(name = "authorization_evidence_id", length = 160)
    private String authorizationEvidenceId;

    @Column(name = "media_id")
    private Long mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 32)
    private PracticeSpeakingStorageProvider storageProvider;

    @Column(name = "storage_profile_code", nullable = false, length = 40)
    private String storageProfileCode;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PracticeSpeakingMediaCleanupStatus status;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "attempt_count", nullable = false)
    private Long attemptCount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_code", length = 40)
    private PracticeSpeakingMediaCleanupErrorCode lastErrorCode;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PracticeSpeakingMediaCleanupTask() {
    }

    public static PracticeSpeakingMediaCleanupTask pendingExact(
            PracticeSpeakingMediaCleanupReason cleanupReason,
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey,
            LocalDateTime dueAt,
            LocalDateTime nextAttemptAt) {
        return pendingExact(cleanupReason, mediaId, storageProvider,
                storageProfileCode, storageKey, dueAt, nextAttemptAt, null);
    }

    public static PracticeSpeakingMediaCleanupTask pendingExact(
            PracticeSpeakingMediaCleanupReason cleanupReason,
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey,
            LocalDateTime dueAt,
            LocalDateTime nextAttemptAt,
            String authorizationEvidenceId) {
        PracticeSpeakingMediaCleanupTask task = new PracticeSpeakingMediaCleanupTask();
        task.cleanupReason = require(cleanupReason, "cleanupReason");
        task.authorizationEvidenceId = authorizationEvidence(
                cleanupReason, authorizationEvidenceId);
        task.storageProvider = require(storageProvider, "storageProvider");
        task.storageKey = canonicalStorageKey(storageKey);
        task.dueAt = require(dueAt, "dueAt");
        task.nextAttemptAt = require(nextAttemptAt, "nextAttemptAt");
        task.status = PracticeSpeakingMediaCleanupStatus.PENDING;
        task.attemptCount = 0L;
        if (!"PRACTICE_SPEAKING".equals(storageProfileCode)) {
            throw new IllegalArgumentException("storageProfileCode is invalid.");
        }
        task.mediaId = mediaId;
        task.storageProfileCode = storageProfileCode;
        return task;
    }

    private static String authorizationEvidence(
            PracticeSpeakingMediaCleanupReason reason, String value) {
        if (reason != PracticeSpeakingMediaCleanupReason.CONSENT_WITHDRAWAL) {
            if (value != null) {
                throw new IllegalArgumentException(
                        "authorizationEvidenceId is only valid for consent withdrawal.");
            }
            return null;
        }
        if (value == null || value.isBlank() || value.length() > 160
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("authorizationEvidenceId is invalid.");
        }
        return value.trim();
    }

    public void claim(Long expectedLockVersion,
                      String claimToken,
                      LocalDateTime leaseExpiresAt) {
        requireExpectedVersion(expectedLockVersion);
        if (status == PracticeSpeakingMediaCleanupStatus.COMPLETED
                || status == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            throw new IllegalStateException("Cleanup task is already final.");
        }
        this.status = PracticeSpeakingMediaCleanupStatus.PROCESSING;
        this.claimToken = requireClaimToken(claimToken);
        this.leaseExpiresAt = require(leaseExpiresAt, "leaseExpiresAt");
    }

    public boolean isClaimable(LocalDateTime now) {
        if (now == null) {
            return false;
        }
        if (status == PracticeSpeakingMediaCleanupStatus.PENDING
                || status == PracticeSpeakingMediaCleanupStatus.RETRY) {
            return dueAt != null && !dueAt.isAfter(now)
                    && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
        }
        return status == PracticeSpeakingMediaCleanupStatus.PROCESSING
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
    }

    public void markCompleted(Long expectedLockVersion,
                              String expectedClaimToken,
                              LocalDateTime completedAt) {
        requireOwnedClaim(expectedLockVersion, expectedClaimToken);
        if (status == PracticeSpeakingMediaCleanupStatus.COMPLETED) {
            return;
        }
        if (status == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            return;
        }
        status = PracticeSpeakingMediaCleanupStatus.COMPLETED;
        this.completedAt = require(completedAt, "completedAt");
        lastErrorCode = null;
        clearClaim();
    }

    public void markRetry(Long expectedLockVersion,
                          String expectedClaimToken,
                          PracticeSpeakingMediaCleanupErrorCode errorCode,
                          LocalDateTime nextAttemptAt) {
        requireOwnedClaim(expectedLockVersion, expectedClaimToken);
        if (status == PracticeSpeakingMediaCleanupStatus.COMPLETED
                || status == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            return;
        }
        status = PracticeSpeakingMediaCleanupStatus.RETRY;
        attemptCount = incrementAttemptCount();
        lastErrorCode = require(errorCode, "errorCode");
        this.nextAttemptAt = require(nextAttemptAt, "nextAttemptAt");
        clearClaim();
    }

    public void markTerminal(Long expectedLockVersion,
                             String expectedClaimToken,
                             PracticeSpeakingMediaCleanupErrorCode errorCode,
                             LocalDateTime completedAt) {
        requireOwnedClaim(expectedLockVersion, expectedClaimToken);
        if (status == PracticeSpeakingMediaCleanupStatus.COMPLETED
                || status == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            return;
        }
        status = PracticeSpeakingMediaCleanupStatus.TERMINAL;
        attemptCount = incrementAttemptCount();
        lastErrorCode = require(errorCode, "errorCode");
        this.completedAt = require(completedAt, "completedAt");
        clearClaim();
    }

    public CleanupProcessingSnapshot toProcessingSnapshot() {
        return new CleanupProcessingSnapshot(
                id,
                lockVersion,
                mediaId,
                storageProvider,
                storageProfileCode,
                storageKey,
                status,
                claimToken,
                leaseExpiresAt,
                attemptCount,
                nextAttemptAt);
    }

    private void requireOwnedClaim(Long expectedLockVersion,
                                   String expectedClaimToken) {
        requireExpectedVersion(expectedLockVersion);
        if (status != PracticeSpeakingMediaCleanupStatus.PROCESSING
                || claimToken == null
                || expectedClaimToken == null
                || !claimToken.equals(expectedClaimToken)) {
            throw new IllegalStateException("Cleanup task claim mismatch.");
        }
    }

    private static String requireClaimToken(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException("claimToken is invalid.");
        }
        return value;
    }

    private void clearClaim() {
        claimToken = null;
        leaseExpiresAt = null;
    }

    private void requireExpectedVersion(Long expectedLockVersion) {
        if (expectedLockVersion == null || !expectedLockVersion.equals(lockVersion)) {
            throw new IllegalStateException("Cleanup task version mismatch.");
        }
    }

    private Long incrementAttemptCount() {
        if (attemptCount == null) {
            return 1L;
        }
        if (attemptCount == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return attemptCount + 1L;
    }

    private static String canonicalStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required.");
        }
        String trimmed = storageKey.trim();
        if (trimmed.length() > 512) {
            throw new IllegalArgumentException("storageKey is too long.");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("storageKey is invalid.");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    public Long getId() { return id; }
    public PracticeSpeakingMediaCleanupReason getCleanupReason() { return cleanupReason; }
    public String getAuthorizationEvidenceId() { return authorizationEvidenceId; }
    public Long getMediaId() { return mediaId; }
    public PracticeSpeakingStorageProvider getStorageProvider() { return storageProvider; }
    public String getStorageProfileCode() { return storageProfileCode; }
    public String getStorageKey() { return storageKey; }
    public LocalDateTime getDueAt() { return dueAt; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public PracticeSpeakingMediaCleanupStatus getStatus() { return status; }
    public String getClaimToken() { return claimToken; }
    public LocalDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public Long getAttemptCount() { return attemptCount; }
    public PracticeSpeakingMediaCleanupErrorCode getLastErrorCode() { return lastErrorCode; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Long getLockVersion() { return lockVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "PracticeSpeakingMediaCleanupTask{id=" + id
                + ", cleanupReason=" + cleanupReason
                + ", storageProvider=" + storageProvider
                + ", status=" + status
                + ", attemptCount=" + attemptCount
                + ", lastErrorCode=" + lastErrorCode
                + '}';
    }
}
