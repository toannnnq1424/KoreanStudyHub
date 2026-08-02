package com.ksh.entities;

import java.time.LocalDateTime;

public record CleanupProcessingSnapshot(
        Long taskId,
        Long lockVersion,
        Long mediaId,
        PracticeSpeakingStorageProvider storageProvider,
        String storageProfileCode,
        String storageKey,
        PracticeSpeakingMediaCleanupStatus status,
        String claimToken,
        LocalDateTime leaseExpiresAt,
        Long attemptCount,
        LocalDateTime nextAttemptAt
) {
    public CleanupProcessingSnapshot(
            Long taskId,
            Long lockVersion,
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            PracticeSpeakingMediaCleanupStatus status,
            String claimToken,
            LocalDateTime leaseExpiresAt,
            Long attemptCount,
            LocalDateTime nextAttemptAt) {
        this(taskId, lockVersion, null, storageProvider, null, storageKey,
                status, claimToken, leaseExpiresAt, attemptCount, nextAttemptAt);
    }

    @Override
    public String toString() {
        return "CleanupProcessingSnapshot{taskId=" + taskId
                + ", lockVersion=" + lockVersion
                + ", storageProvider=" + storageProvider
                + ", storageProfile=" + storageProfileCode
                + ", status=" + status
                + ", claimed=" + (claimToken != null)
                + ", leaseExpiresAt=" + leaseExpiresAt
                + ", attemptCount=" + attemptCount
                + ", nextAttemptAt=" + nextAttemptAt
                + '}';
    }
}
