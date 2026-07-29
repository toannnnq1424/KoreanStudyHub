package com.ksh.features.practice.service;

import java.math.BigDecimal;

public record PracticeAttemptEvaluationOutcome(
        String terminalStatus,
        String inputFingerprint,
        BigDecimal score,
        BigDecimal totalPoints,
        String answersJson,
        String feedbackJson,
        String engine,
        String errorCode,
        boolean retryable
) {
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    public PracticeAttemptEvaluationOutcome {
        if (!SUCCEEDED.equals(terminalStatus)
                && !FAILED.equals(terminalStatus)
                && !UNAVAILABLE.equals(terminalStatus)) {
            throw new IllegalArgumentException(
                    "Evaluation outcome terminal status is invalid.");
        }
    }
}
