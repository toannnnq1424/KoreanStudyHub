package com.ksh.features.ai.questiongen;

import java.util.List;

/** Transport types for the lecturer AI question-generation preview flow. */
public final class AiQuestionGenDtos {

    private AiQuestionGenDtos() {
    }

    public record GenerateRequest(int count, String type, String difficulty) {
    }

    public record DraftOption(String content, boolean correct) {
    }

    public record DraftQuestion(String type, String content, String explanation,
                                List<DraftOption> options) {
    }

    public record Preview(String sessionId, List<DraftQuestion> questions) {
    }

    /**
     * Session ids remain strings at the HTTP boundary so malformed input follows the
     * same non-disclosing "expired session" path as an unknown id.
     */
    public record ConfirmRequest(String sessionId, List<Integer> indexes) {
    }

    public record ConfirmResult(int insertedCount) {
    }
}
