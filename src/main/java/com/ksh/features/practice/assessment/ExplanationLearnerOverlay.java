package com.ksh.features.practice.assessment;

import java.math.BigDecimal;

public record ExplanationLearnerOverlay(
        AssessmentScoreStatus status,
        BigDecimal earnedPoints,
        BigDecimal possiblePoints,
        int correctUnits,
        int totalUnits
) {
    public static ExplanationLearnerOverlay from(AssessmentScoreResult result) {
        return new ExplanationLearnerOverlay(
                result.status(),
                result.earnedPoints(),
                result.possiblePoints(),
                result.correctUnits(),
                result.totalUnits()
        );
    }
}
