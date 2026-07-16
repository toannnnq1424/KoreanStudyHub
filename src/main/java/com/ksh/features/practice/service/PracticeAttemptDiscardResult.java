package com.ksh.features.practice.service;

import java.time.LocalDateTime;
import java.util.List;

public record PracticeAttemptDiscardResult(
        Long attemptId,
        String status,
        LocalDateTime discardedAt,
        int cleanupTaskCount,
        List<Long> immediateCleanupTaskIds
) {
    public PracticeAttemptDiscardResult {
        immediateCleanupTaskIds = List.copyOf(immediateCleanupTaskIds);
    }
}
