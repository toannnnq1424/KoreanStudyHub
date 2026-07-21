package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.ai.media.AiImageEvidence;

import java.math.BigDecimal;

public record SpeakingEvaluationRequest(
        Long attemptId,
        Long questionId,
        String questionText,
        String targetLevel,
        String expectedAnswerGuidance,
        AiImageEvidence imageEvidence,
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
        String schemaVersion,
        SpeakingEvaluatorCapability evaluatorCapability,
        SpeakingEvidenceMode evidenceMode,
        String evidenceContractVersion
) {
    public SpeakingEvaluationRequest {
        evaluatorCapability = evaluatorCapability == null
                ? SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION
                : evaluatorCapability;
        evidenceMode = evidenceMode == null ? SpeakingEvidenceMode.TRANSCRIPT_ONLY : evidenceMode;
        evidenceContractVersion = evidenceContractVersion == null || evidenceContractVersion.isBlank()
                ? evaluatorCapability.contractVersion()
                : evidenceContractVersion.trim();
    }

    public SpeakingEvaluationRequest(
            Long attemptId,
            Long questionId,
            String questionText,
            String targetLevel,
            String expectedAnswerGuidance,
            AiImageEvidence imageEvidence,
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
        this(attemptId, questionId, questionText, targetLevel, expectedAnswerGuidance,
                imageEvidence, audioMediaId, mediaVersion, mimeType, byteSize, durationMs,
                transcriptionProvider, transcriptionModel, language, transcript, normalizedTranscript,
                actuallyHeardTranscript, interpretedIntent, transcriptConfidence, textFallback,
                promptVersion, rubricVersion, schemaVersion,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION);
    }

    public SpeakingEvaluationRequest(
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
        this(attemptId, questionId, questionText, targetLevel, expectedAnswerGuidance,
                null, audioMediaId, mediaVersion, mimeType, byteSize, durationMs,
                transcriptionProvider, transcriptionModel, language, transcript, normalizedTranscript,
                actuallyHeardTranscript, interpretedIntent, transcriptConfidence, textFallback,
                promptVersion, rubricVersion, schemaVersion,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION);
    }

    public boolean transcriptLanguageEvaluatorContract() {
        return evaluatorCapability == SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION
                && evidenceMode == SpeakingEvidenceMode.TRANSCRIPT_ONLY
                && java.util.Objects.equals(
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION, evidenceContractVersion);
    }

    @Override
    public String toString() {
        return "SpeakingEvaluationRequest{"
                + "attemptId=" + attemptId
                + ", questionId=" + questionId
                + ", questionTextPresent=" + (questionText != null && !questionText.isBlank())
                + ", targetLevelPresent=" + (targetLevel != null && !targetLevel.isBlank())
                + ", expectedAnswerGuidancePresent=" + (expectedAnswerGuidance != null && !expectedAnswerGuidance.isBlank())
                + ", questionImagePresent=" + (imageEvidence != null)
                + ", audioMediaPresent=" + (audioMediaId != null)
                + ", mediaVersionPresent=" + (mediaVersion != null)
                + ", mimeTypePresent=" + (mimeType != null && !mimeType.isBlank())
                + ", byteSizePresent=" + (byteSize != null)
                + ", durationPresent=" + (durationMs != null)
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
                + ", evaluatorCapability=" + evaluatorCapability
                + ", evidenceMode=" + evidenceMode
                + ", evidenceContractVersion='" + evidenceContractVersion + '\''
                + '}';
    }
}
