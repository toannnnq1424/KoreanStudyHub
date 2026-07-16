package com.ksh.features.practice.assessment;

import java.math.BigDecimal;

/** Learner delivery boundary. Correct answers and profile internals are intentionally absent. */
public record PlayerQuestionPayload(
        String schemaVersion,
        Long questionId,
        CanonicalQuestionType questionType,
        String prompt,
        QuestionContent content,
        BigDecimal points
) {
    public static final String SCHEMA_VERSION = "player-question-v1";

    public PlayerQuestionPayload {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
    }
}
