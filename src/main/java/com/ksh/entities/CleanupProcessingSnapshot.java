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
    public CleanupProcessingSnapshot {
        if (!"PRACTICE_SPEAKING".equals(storageProfileCode)) {
            throw new IllegalArgumentException("storageProfileCode is invalid.");
        }
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
