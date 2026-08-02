package com.ksh.entities;

import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileResolver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "practice_storage_migration_jobs")
public class PracticeStorageMigrationJob {
    public static final int MAX_ATTEMPTS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "logical_type", nullable = false, length = 40)
    private PracticeStorageMigrationLogicalType logicalType;

    @Column(name = "logical_id", nullable = false)
    private Long logicalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_profile_code", length = 40)
    private StorageProfileCode sourceProfileCode;

    @Column(name = "source_storage_key", nullable = false, length = 512)
    private String sourceStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_profile_code", nullable = false, length = 40)
    private StorageProfileCode targetProfileCode;

    @Column(name = "target_storage_key", nullable = false, length = 512)
    private String targetStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_storage_provider", length = 32)
    private StorageBackend targetStorageProvider;

    @Column(name = "expected_size", nullable = false)
    private Long expectedSize;

    @Column(name = "expected_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String expectedSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PracticeStorageMigrationStatus status;

    @Column(name = "copy_attempt_count", nullable = false)
    private Integer copyAttemptCount;

    @Column(name = "cleanup_attempt_count", nullable = false)
    private Integer cleanupAttemptCount;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "logical_updated_at")
    private LocalDateTime logicalUpdatedAt;

    @Column(name = "cleanup_not_before")
    private LocalDateTime cleanupNotBefore;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(nullable = false)
    private Long revision;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PracticeStorageMigrationJob() {
    }

    public static PracticeStorageMigrationJob planned(
            PracticeStorageMigrationLogicalType logicalType,
            Long logicalId,
            StorageProfileCode sourceProfileCode,
            String sourceStorageKey,
            StorageProfileCode targetProfileCode,
            String targetStorageKey,
            long expectedSize,
            String expectedSha256,
            LocalDateTime now) {
        Objects.requireNonNull(logicalType, "logicalType");
        if (logicalId == null || logicalId <= 0L || expectedSize < 0L) {
            throw new IllegalArgumentException("STORAGE_MIGRATION_PLAN_INVALID");
        }
        if (targetProfileCode != logicalType.requiredProfile()
                || (sourceProfileCode != null && sourceProfileCode != targetProfileCode)) {
            throw new IllegalArgumentException("STORAGE_MIGRATION_PROFILE_INVALID");
        }
        if (sourceProfileCode != null) {
            StorageProfileResolver.requireSafeObjectKey(sourceStorageKey);
        } else if (sourceStorageKey == null || sourceStorageKey.isBlank()) {
            throw new IllegalArgumentException("STORAGE_MIGRATION_PLAN_INVALID");
        }
        PracticeStorageMigrationJob job = new PracticeStorageMigrationJob();
        job.logicalType = logicalType;
        job.logicalId = logicalId;
        job.sourceProfileCode = sourceProfileCode;
        job.sourceStorageKey = sourceStorageKey;
        job.targetProfileCode = targetProfileCode;
        job.targetStorageKey = StorageProfileResolver.requireSafeObjectKey(targetStorageKey);
        if (sourceProfileCode != null && sourceStorageKey.equals(job.targetStorageKey)) {
            throw new IllegalArgumentException("STORAGE_MIGRATION_KEYS_COLLIDE");
        }
        job.expectedSize = expectedSize;
        job.expectedSha256 = requireSha256(expectedSha256);
        job.status = PracticeStorageMigrationStatus.PLANNED;
        job.copyAttemptCount = 0;
        job.cleanupAttemptCount = 0;
        job.nextAttemptAt = Objects.requireNonNull(now, "now");
        return job;
    }

    public boolean claimCopy(LocalDateTime now, LocalDateTime leaseUntil, String token) {
        if ((status != PracticeStorageMigrationStatus.PLANNED
                && status != PracticeStorageMigrationStatus.COPYING)
                || nextAttemptAt == null || nextAttemptAt.isAfter(now)
                || copyAttemptCount >= MAX_ATTEMPTS) {
            return false;
        }
        status = PracticeStorageMigrationStatus.COPYING;
        copyAttemptCount++;
        nextAttemptAt = Objects.requireNonNull(leaseUntil, "leaseUntil");
        leaseExpiresAt = leaseUntil;
        claimToken = requireClaimToken(token);
        lastErrorCode = null;
        return true;
    }

    public void markCopiedVerified(String token, StorageBackend provider, LocalDateTime now) {
        requireStatus(PracticeStorageMigrationStatus.COPYING);
        requireClaim(token);
        targetStorageProvider = Objects.requireNonNull(provider, "provider");
        status = PracticeStorageMigrationStatus.COPIED_VERIFIED;
        verifiedAt = Objects.requireNonNull(now, "now");
        nextAttemptAt = null;
        clearClaim();
        lastErrorCode = null;
    }

    public void markLogicalUpdatedAndCleanupPending(LocalDateTime now,
                                                    LocalDateTime cleanupAt) {
        requireStatus(PracticeStorageMigrationStatus.COPIED_VERIFIED);
        logicalUpdatedAt = Objects.requireNonNull(now, "now");
        status = PracticeStorageMigrationStatus.LOGICAL_UPDATED;
        cleanupNotBefore = Objects.requireNonNull(cleanupAt, "cleanupAt");
        nextAttemptAt = cleanupAt;
        status = PracticeStorageMigrationStatus.CLEANUP_PENDING;
    }

    public boolean claimCleanup(LocalDateTime now, LocalDateTime leaseUntil, String token) {
        if ((status != PracticeStorageMigrationStatus.CLEANUP_PENDING
                && status != PracticeStorageMigrationStatus.DELETING_SOURCE)
                || cleanupNotBefore == null || cleanupNotBefore.isAfter(now)
                || nextAttemptAt == null || nextAttemptAt.isAfter(now)
                || cleanupAttemptCount >= MAX_ATTEMPTS) {
            return false;
        }
        status = PracticeStorageMigrationStatus.DELETING_SOURCE;
        cleanupAttemptCount++;
        nextAttemptAt = Objects.requireNonNull(leaseUntil, "leaseUntil");
        leaseExpiresAt = leaseUntil;
        claimToken = requireClaimToken(token);
        lastErrorCode = null;
        return true;
    }

    public void retryCopy(String token, String errorCode, LocalDateTime nextAttempt) {
        requireStatus(PracticeStorageMigrationStatus.COPYING);
        requireClaim(token);
        lastErrorCode = requireErrorCode(errorCode);
        nextAttemptAt = Objects.requireNonNull(nextAttempt, "nextAttempt");
        status = copyAttemptCount >= MAX_ATTEMPTS
                ? PracticeStorageMigrationStatus.FAILED
                : PracticeStorageMigrationStatus.PLANNED;
        clearClaim();
    }

    public void retryCleanup(String token, String errorCode, LocalDateTime nextAttempt) {
        requireStatus(PracticeStorageMigrationStatus.DELETING_SOURCE);
        requireClaim(token);
        lastErrorCode = requireErrorCode(errorCode);
        nextAttemptAt = Objects.requireNonNull(nextAttempt, "nextAttempt");
        status = cleanupAttemptCount >= MAX_ATTEMPTS
                ? PracticeStorageMigrationStatus.FAILED
                : PracticeStorageMigrationStatus.CLEANUP_PENDING;
        clearClaim();
    }

    public void complete(String token, LocalDateTime now) {
        requireStatus(PracticeStorageMigrationStatus.DELETING_SOURCE);
        requireClaim(token);
        status = PracticeStorageMigrationStatus.COMPLETED;
        completedAt = Objects.requireNonNull(now, "now");
        nextAttemptAt = null;
        lastErrorCode = null;
        clearClaim();
    }

    private void requireStatus(PracticeStorageMigrationStatus required) {
        if (status != required) throw new IllegalStateException("STORAGE_MIGRATION_STATE_CONFLICT");
    }

    private void requireClaim(String token) {
        if (claimToken == null || !claimToken.equals(token)) {
            throw new IllegalStateException("STORAGE_MIGRATION_CLAIM_CONFLICT");
        }
    }

    private static String requireClaimToken(String token) {
        if (token == null || !token.matches("[a-z0-9]{16,64}")) {
            throw new IllegalArgumentException("STORAGE_MIGRATION_CLAIM_INVALID");
        }
        return token;
    }

    private void clearClaim() {
        claimToken = null;
        leaseExpiresAt = null;
    }

    private static String requireSha256(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("STORAGE_MIGRATION_HASH_INVALID");
        }
        return normalized;
    }

    private static String requireErrorCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{3,64}")) {
            return "STORAGE_MIGRATION_FAILED";
        }
        return value;
    }

    public Long getId() { return id; }
    public PracticeStorageMigrationLogicalType getLogicalType() { return logicalType; }
    public Long getLogicalId() { return logicalId; }
    public StorageProfileCode getSourceProfileCode() { return sourceProfileCode; }
    public String getSourceStorageKey() { return sourceStorageKey; }
    public StorageProfileCode getTargetProfileCode() { return targetProfileCode; }
    public String getTargetStorageKey() { return targetStorageKey; }
    public StorageBackend getTargetStorageProvider() { return targetStorageProvider; }
    public Long getExpectedSize() { return expectedSize; }
    public String getExpectedSha256() { return expectedSha256; }
    public PracticeStorageMigrationStatus getStatus() { return status; }
    public Integer getCopyAttemptCount() { return copyAttemptCount; }
    public Integer getCleanupAttemptCount() { return cleanupAttemptCount; }
    public String getLastErrorCode() { return lastErrorCode; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getClaimToken() { return claimToken; }
    public LocalDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getLogicalUpdatedAt() { return logicalUpdatedAt; }
    public LocalDateTime getCleanupNotBefore() { return cleanupNotBefore; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Long getRevision() { return revision; }
}
