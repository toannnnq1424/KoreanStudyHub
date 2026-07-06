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
                name = "uk_psm_cleanup_storage",
                columnNames = {"storage_provider", "storage_key"})
)
public class PracticeSpeakingMediaCleanupTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanup_reason", nullable = false, length = 40)
    private PracticeSpeakingMediaCleanupReason cleanupReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 32)
    private PracticeSpeakingStorageProvider storageProvider;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PracticeSpeakingMediaCleanupStatus status;

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

    public static PracticeSpeakingMediaCleanupTask pending(
            PracticeSpeakingMediaCleanupReason cleanupReason,
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            LocalDateTime dueAt) {
        return pending(cleanupReason, storageProvider, storageKey, dueAt, dueAt);
    }

    public static PracticeSpeakingMediaCleanupTask pending(
            PracticeSpeakingMediaCleanupReason cleanupReason,
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            LocalDateTime dueAt,
            LocalDateTime nextAttemptAt) {
        PracticeSpeakingMediaCleanupTask task = new PracticeSpeakingMediaCleanupTask();
        task.cleanupReason = require(cleanupReason, "cleanupReason");
        task.storageProvider = require(storageProvider, "storageProvider");
        task.storageKey = canonicalStorageKey(storageKey);
        task.dueAt = require(dueAt, "dueAt");
        task.nextAttemptAt = require(nextAttemptAt, "nextAttemptAt");
        task.status = PracticeSpeakingMediaCleanupStatus.PENDING;
        task.attemptCount = 0L;
        return task;
    }

    public void markCompleted(Long expectedLockVersion, LocalDateTime completedAt) {
        requireExpectedVersion(expectedLockVersion);
        if (status == PracticeSpeakingMediaCleanupStatus.COMPLETED) {
            return;
        }
        if (status == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            return;
        }
        status = PracticeSpeakingMediaCleanupStatus.COMPLETED;
        this.completedAt = require(completedAt, "completedAt");
        lastErrorCode = null;
    }

    public void markRetry(Long expectedLockVersion,
                          PracticeSpeakingMediaCleanupErrorCode errorCode,
                          LocalDateTime nextAttemptAt) {
        requireExpectedVersion(expectedLockVersion);
        if (status == PracticeSpeakingMediaCleanupStatus.COMPLETED
                || status == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            return;
        }
        status = PracticeSpeakingMediaCleanupStatus.RETRY;
        attemptCount = incrementAttemptCount();
        lastErrorCode = require(errorCode, "errorCode");
        this.nextAttemptAt = require(nextAttemptAt, "nextAttemptAt");
    }

    public void markTerminal(Long expectedLockVersion,
                             PracticeSpeakingMediaCleanupErrorCode errorCode,
                             LocalDateTime completedAt) {
        requireExpectedVersion(expectedLockVersion);
        if (status == PracticeSpeakingMediaCleanupStatus.COMPLETED
                || status == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            return;
        }
        status = PracticeSpeakingMediaCleanupStatus.TERMINAL;
        attemptCount = incrementAttemptCount();
        lastErrorCode = require(errorCode, "errorCode");
        this.completedAt = require(completedAt, "completedAt");
    }

    public CleanupProcessingSnapshot toProcessingSnapshot() {
        return new CleanupProcessingSnapshot(
                id,
                lockVersion,
                storageProvider,
                storageKey,
                status,
                attemptCount,
                nextAttemptAt);
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
    public PracticeSpeakingStorageProvider getStorageProvider() { return storageProvider; }
    public String getStorageKey() { return storageKey; }
    public LocalDateTime getDueAt() { return dueAt; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public PracticeSpeakingMediaCleanupStatus getStatus() { return status; }
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
