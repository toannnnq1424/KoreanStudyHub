package com.ksh.features.practice.ai;

import java.math.BigDecimal;

public record WritingEvaluationResult(
        BigDecimal rawScore,
        BigDecimal rawScoreMax,
        BigDecimal score,
        BigDecimal overallScore,
        String taskType,
        String engine
) {
}
