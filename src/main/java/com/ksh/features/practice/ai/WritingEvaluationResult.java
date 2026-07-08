package com.ksh.features.practice.ai;

import java.math.BigDecimal;

public record WritingEvaluationResult(
        BigDecimal rawScore,
        BigDecimal rawScoreMax,
        BigDecimal score,
        BigDecimal overallScore,
        String taskType,
        String engine,
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
                "LEGACY_EVALUATED", "LEGACY", "NONE", false, true);
    }

    public boolean scoreAvailableFlag() {
        return Boolean.TRUE.equals(scoreAvailable);
    }
}
