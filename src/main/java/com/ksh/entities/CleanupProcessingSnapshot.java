package com.ksh.entities;

import java.time.LocalDateTime;

public record CleanupProcessingSnapshot(
        Long taskId,
        Long lockVersion,
        PracticeSpeakingStorageProvider storageProvider,
        String storageKey,
        PracticeSpeakingMediaCleanupStatus status,
        Long attemptCount,
        LocalDateTime nextAttemptAt
) {
    @Override
    public String toString() {
        return "CleanupProcessingSnapshot{taskId=" + taskId
                + ", lockVersion=" + lockVersion
                + ", storageProvider=" + storageProvider
                + ", status=" + status
                + ", attemptCount=" + attemptCount
                + ", nextAttemptAt=" + nextAttemptAt
                + '}';
    }
}
