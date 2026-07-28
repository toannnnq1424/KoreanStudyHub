package com.ksh.entities;

import java.time.LocalDateTime;

public record CleanupProcessingSnapshot(
        Long taskId,
        Long lockVersion,
        PracticeSpeakingStorageProvider storageProvider,
        String storageKey,
        PracticeSpeakingMediaCleanupStatus status,
        String claimToken,
        LocalDateTime leaseExpiresAt,
        Long attemptCount,
        LocalDateTime nextAttemptAt
) {
    @Override
    public String toString() {
        return "CleanupProcessingSnapshot{taskId=" + taskId
                + ", lockVersion=" + lockVersion
                + ", storageProvider=" + storageProvider
                + ", status=" + status
                + ", claimed=" + (claimToken != null)
                + ", leaseExpiresAt=" + leaseExpiresAt
                + ", attemptCount=" + attemptCount
                + ", nextAttemptAt=" + nextAttemptAt
                + '}';
    }
}
