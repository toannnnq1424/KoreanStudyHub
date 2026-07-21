package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import org.springframework.stereotype.Service;

@Service
public class SpeakingEvaluationOrchestrator {
    private final SpeakingEvaluationClient evaluationClient;
    private final SpeakingEvaluationNormalizer normalizer;
    private final SpeakingEvaluatorProperties properties;
    private final ObjectMapper objectMapper;

    public SpeakingEvaluationOrchestrator(
            SpeakingEvaluationClient evaluationClient,
            SpeakingEvaluationNormalizer normalizer,
            SpeakingEvaluatorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.evaluationClient = evaluationClient;
        this.normalizer = normalizer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SpeakingEvaluationResult evaluate(Input input) {
        if (input == null || input.transcriptionResult() == null) {
            return normalizeFailure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    "MISSING_TRANSCRIPTION_RESULT", false);
        }
        SpeakingTranscriptionResult transcription = input.transcriptionResult();
        if (!transcription.status().scoreBearing()) {
            return normalizeFailure(transcription.status(),
                    transcription.errorCategory() == null ? null : transcription.errorCategory().name(),
                    transcription.retryable());
        }

        SpeakingEvaluationRequest request = request(input);
        SpeakingEvaluationProviderResult providerResult = evaluationClient.evaluate(request);
        if (!providerResult.success()) {
            return normalizeFailure(providerResult.failureStatus(), providerResult.errorCategory(), providerResult.retryable());
        }
        return normalizer.normalize(completeProviderJson(
                providerResult.evaluationJson(), request, providerResult, transcription.status()));
    }

    private SpeakingEvaluationRequest request(Input input) {
        SpeakingTranscriptionResult transcription = input.transcriptionResult();
        String normalizedTranscript = transcription.normalizedTranscript();
        String transcript = transcription.transcript();
        return new SpeakingEvaluationRequest(
                input.attemptId(),
                input.questionId(),
                input.questionText(),
                input.targetLevel(),
                input.expectedAnswerGuidance(),
                input.imageEvidence(),
                input.audioMediaId(),
                input.mediaVersion(),
                input.mimeType(),
                input.byteSize(),
                input.durationMs() == null ? transcription.durationMs() : input.durationMs(),
                transcription.provider(),
                transcription.model(),
                transcription.language(),
                transcript,
                normalizedTranscript,
                normalizedTranscript == null ? transcript : normalizedTranscript,
                null,
                transcription.transcriptConfidence(),
                transcription.status() == SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED,
                properties.promptVersion(),
                properties.rubricVersion(),
                properties.schemaVersion(),
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION);
    }

    private JsonNode completeProviderJson(
            JsonNode providerJson,
            SpeakingEvaluationRequest request,
            SpeakingEvaluationProviderResult providerResult,
            SpeakingEvaluationStatus transcriptionStatus
    ) {
        ObjectNode copy = providerJson == null || !providerJson.isObject()
                ? objectMapper.createObjectNode()
                : ((ObjectNode) providerJson.deepCopy());
        putAuthoritative(copy, "source", request.textFallback()
                ? SpeakingEvaluationSource.TEXT_FALLBACK.name()
                : SpeakingEvaluationSource.PROVIDER.name());
        putAuthoritative(copy, "model", providerResult.model());
        putAuthoritative(copy, "transcription_model", request.transcriptionModel());
        putAuthoritative(copy, "prompt_version", request.promptVersion());
        putAuthoritative(copy, "rubric_version", request.rubricVersion());
        putAuthoritative(copy, "schema_version", request.schemaVersion());
        putAuthoritative(copy, "evaluator_capability", request.evaluatorCapability().name());
        putAuthoritative(copy, "evidence_mode", request.evidenceMode().name());
        putAuthoritative(copy, "evidence_contract_version", request.evidenceContractVersion());
        SpeakingEvaluationStatus authoritativeStatus;
        if (request.textFallback()) {
            authoritativeStatus = SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED;
        } else if (transcriptionStatus == SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE) {
            authoritativeStatus = SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE;
        } else {
            authoritativeStatus = SpeakingEvaluationStatus.EVALUATED;
        }
        putAuthoritative(copy, "evaluation_status", authoritativeStatus.name());
        putAuthoritative(copy, "audio_media_id", request.audioMediaId());
        putAuthoritative(copy, "media_version", request.mediaVersion());
        putAuthoritative(copy, "transcript", request.transcript());
        putAuthoritative(copy, "normalized_transcript", request.normalizedTranscript());
        putAuthoritative(copy, "actually_heard_transcript", request.actuallyHeardTranscript());
        putAuthoritative(copy, "transcript_confidence", request.transcriptConfidence());
        copy.remove("interpreted_intent");
        copy.remove("intent_confidence");
        copy.putNull("interpreted_intent");
        copy.putNull("intent_confidence");
        if (request.textFallback()) {
            copy.remove("audio_media_id");
            copy.remove("media_version");
            copy.remove("transcription_model");
            copy.put("evaluation_status", SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED.name());
            copy.put("source", SpeakingEvaluationSource.TEXT_FALLBACK.name());
        }
        return copy;
    }

    private SpeakingEvaluationResult normalizeFailure(
            SpeakingEvaluationStatus status,
            String errorCategory,
            boolean retryable
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("evaluation_status", status == null ? SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE.name() : status.name());
        if (errorCategory != null && !errorCategory.isBlank()) {
            node.put("error_category", errorCategory);
        }
        node.put("retryable", retryable);
        return normalizer.normalize(node);
    }

    private static void putAuthoritative(ObjectNode node, String field, String value) {
        node.remove(field);
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static void putAuthoritative(ObjectNode node, String field, Long value) {
        node.remove(field);
        if (value != null) {
            node.put(field, value);
        }
    }

    private static void putAuthoritative(ObjectNode node, String field, java.math.BigDecimal value) {
        node.remove(field);
        if (value != null) {
            node.put(field, value);
        }
    }

    public record Input(
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
            AiImageEvidence imageEvidence,
            SpeakingTranscriptionResult transcriptionResult,
            String textFallbackAnswer
    ) {
        public Input(
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
                SpeakingTranscriptionResult transcriptionResult,
                String textFallbackAnswer
        ) {
            this(attemptId, questionId, questionText, targetLevel, expectedAnswerGuidance,
                    audioMediaId, mediaVersion, mimeType, byteSize, durationMs,
                    null, transcriptionResult, textFallbackAnswer);
        }

        @Override
        public String toString() {
            return "SpeakingEvaluationOrchestrator.Input{"
                    + "attemptId=" + attemptId
                    + ", questionId=" + questionId
                    + ", questionTextPresent=" + (questionText != null && !questionText.isBlank())
                    + ", targetLevelPresent=" + (targetLevel != null && !targetLevel.isBlank())
                    + ", expectedAnswerGuidancePresent=" + (expectedAnswerGuidance != null && !expectedAnswerGuidance.isBlank())
                    + ", audioMediaPresent=" + (audioMediaId != null)
                    + ", mediaVersionPresent=" + (mediaVersion != null)
                    + ", mimeTypePresent=" + (mimeType != null && !mimeType.isBlank())
                    + ", byteSizePresent=" + (byteSize != null)
                    + ", durationPresent=" + (durationMs != null)
                    + ", questionImagePresent=" + (imageEvidence != null)
                    + ", transcriptionStatus=" + (transcriptionResult == null ? null : transcriptionResult.status())
                    + ", textFallbackPresent=" + (textFallbackAnswer != null && !textFallbackAnswer.isBlank())
                    + '}';
        }
    }
}
