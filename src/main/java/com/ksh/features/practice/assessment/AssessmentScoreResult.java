package com.ksh.features.practice.assessment;

import java.math.BigDecimal;

public record AssessmentScoreResult(
        AssessmentScoreStatus status,
        BigDecimal earnedPoints,
        BigDecimal possiblePoints,
        ScoringPolicyCode scoringPolicyCode,
        int correctUnits,
        int totalUnits
) {
    public AssessmentScoreResult {
        if (status == null || earnedPoints == null || possiblePoints == null || scoringPolicyCode == null) {
            throw new IllegalArgumentException("Score result fields must not be null");
        }
        if (earnedPoints.signum() < 0 || possiblePoints.signum() < 0) {
            throw new IllegalArgumentException("Score points must not be negative");
        }
        if (earnedPoints.compareTo(possiblePoints) > 0) {
            throw new IllegalArgumentException("Earned points must not exceed possible points");
        }
        if (correctUnits < 0 || totalUnits < 0 || correctUnits > totalUnits) {
            throw new IllegalArgumentException("Score units are outside the valid range");
        }
    }

    public boolean fullyCorrect() {
        return status == AssessmentScoreStatus.CORRECT;
    }
}
