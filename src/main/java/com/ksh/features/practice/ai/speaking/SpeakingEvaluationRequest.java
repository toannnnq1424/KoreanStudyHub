package com.ksh.features.practice.ai.speaking;

import java.math.BigDecimal;

public record SpeakingEvaluationRequest(
        Long attemptId,
        Long questionId,
        String questionText,
        String targetLevel,
        String expectedAnswerGuidance,
        Long audioMediaId,
        Long mediaVersion,
        String mimeType,
        Long byteSize,
        Long durationMs,
        String transcriptionProvider,
        String transcriptionModel,
        String language,
        String transcript,
        String normalizedTranscript,
        String actuallyHeardTranscript,
        String interpretedIntent,
        BigDecimal transcriptConfidence,
        boolean textFallback,
        String promptVersion,
        String rubricVersion,
        String schemaVersion
) {
    @Override
    public String toString() {
        return "SpeakingEvaluationRequest{"
                + "attemptId=" + attemptId
                + ", questionId=" + questionId
                + ", questionTextPresent=" + (questionText != null && !questionText.isBlank())
                + ", targetLevelPresent=" + (targetLevel != null && !targetLevel.isBlank())
                + ", expectedAnswerGuidancePresent=" + (expectedAnswerGuidance != null && !expectedAnswerGuidance.isBlank())
                + ", audioMediaId=" + audioMediaId
                + ", mediaVersion=" + mediaVersion
                + ", mimeType='" + mimeType + '\''
                + ", byteSize=" + byteSize
                + ", durationMs=" + durationMs
                + ", transcriptionProvider='" + transcriptionProvider + '\''
                + ", transcriptionModel='" + transcriptionModel + '\''
                + ", language='" + language + '\''
                + ", transcriptPresent=" + (transcript != null && !transcript.isBlank())
                + ", normalizedTranscriptPresent=" + (normalizedTranscript != null && !normalizedTranscript.isBlank())
                + ", actuallyHeardTranscriptPresent=" + (actuallyHeardTranscript != null && !actuallyHeardTranscript.isBlank())
                + ", interpretedIntentPresent=" + (interpretedIntent != null && !interpretedIntent.isBlank())
                + ", transcriptConfidence=" + transcriptConfidence
                + ", textFallback=" + textFallback
                + ", promptVersion='" + promptVersion + '\''
                + ", rubricVersion='" + rubricVersion + '\''
                + ", schemaVersion='" + schemaVersion + '\''
                + '}';
    }
}
