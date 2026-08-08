package com.ksh.features.practice.ai.writing;

import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;

import java.math.BigDecimal;

public record WritingEvaluationResult(
        BigDecimal rawScore,
        BigDecimal rawScoreMax,
        BigDecimal score,
        BigDecimal overallScore,
        String taskType,
        String engine,
        String scoringContract,
        String policyBundleId,
        String evaluationStatus,
        String evaluationSource,
        String evaluationReason,
        Boolean evaluationRetryable,
        Boolean scoreAvailable,
        PracticeAiResultCompleteness completeness
) {
    public WritingEvaluationResult {
        if (completeness == null) {
            throw new IllegalArgumentException(
                    "Writing completeness is required");
        }
        if (Boolean.TRUE.equals(scoreAvailable)
                != completeness.scoreBearingComplete()) {
            throw new IllegalArgumentException(
                    "Writing score availability and completeness disagree");
        }
    }

    public boolean scoreAvailableFlag() {
        return Boolean.TRUE.equals(scoreAvailable)
                && completeness.scoreBearingComplete();
    }
}
