package com.ksh.features.practice.ai.writing;

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
        Boolean scoreAvailable
) {
    public WritingEvaluationResult(BigDecimal rawScore,
                                   BigDecimal rawScoreMax,
                                   BigDecimal score,
                                   BigDecimal overallScore,
                                   String taskType,
                                   String engine) {
        this(rawScore, rawScoreMax, score, overallScore, taskType, engine,
                null, null,
                "LEGACY_EVALUATED", "LEGACY", "NONE", false, true);
    }

    public boolean scoreAvailableFlag() {
        return Boolean.TRUE.equals(scoreAvailable);
    }
}
