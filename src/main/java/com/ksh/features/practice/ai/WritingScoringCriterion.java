package com.ksh.features.practice.ai;

public record WritingScoringCriterion(
        String criterionId,
        String displayName,
        int maxScore,
        int order
) {
    public WritingScoringCriterion {
        if (criterionId == null || criterionId.isBlank()) {
            throw new IllegalArgumentException("criterionId is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (maxScore <= 0) {
            throw new IllegalArgumentException("maxScore must be positive");
        }
        if (order <= 0) {
            throw new IllegalArgumentException("order must be positive");
        }
    }
}
