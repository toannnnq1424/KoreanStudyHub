package com.ksh.features.practice.ai.speaking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public final class SpeakingScorePolicy {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SpeakingScorePolicy() {
    }

    public static BigDecimal earnedQuestionPoints(BigDecimal questionPoints, BigDecimal overallScore) {
        requireNonNegative(questionPoints, "questionPoints");
        requirePercentage(overallScore, "overallScore");
        return questionPoints.multiply(overallScore)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal attemptScore(Collection<BigDecimal> earnedQuestionPoints) {
        if (earnedQuestionPoints == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return earnedQuestionPoints.stream()
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal attemptPercentage(BigDecimal attemptScore, BigDecimal totalPoints) {
        requireNonNegative(attemptScore, "attemptScore");
        if (totalPoints == null || totalPoints.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return attemptScore.multiply(HUNDRED)
                .divide(totalPoints, 2, RoundingMode.HALF_UP);
    }

    private static void requirePercentage(BigDecimal value, String field) {
        requireNonNegative(value, field);
        if (value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(field + " must be at most 100.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " must be non-negative.");
        }
    }
}
