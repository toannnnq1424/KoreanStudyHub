package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
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
        return normalizer.normalize(completeProviderJson(providerResult.evaluationJson(), request, providerResult));
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
                null,
                transcription.status() == SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED,
                properties.promptVersion(),
                properties.rubricVersion(),
                properties.schemaVersion());
    }

    private JsonNode completeProviderJson(
            JsonNode providerJson,
            SpeakingEvaluationRequest request,
            SpeakingEvaluationProviderResult providerResult
    ) {
        ObjectNode copy = providerJson == null || !providerJson.isObject()
                ? objectMapper.createObjectNode()
                : ((ObjectNode) providerJson.deepCopy());
        putIfMissing(copy, "source", request.textFallback()
                ? SpeakingEvaluationSource.TEXT_FALLBACK.name()
                : SpeakingEvaluationSource.PROVIDER.name());
        putIfMissing(copy, "model", providerResult.model());
        putIfMissing(copy, "transcription_model", request.transcriptionModel());
        putIfMissing(copy, "prompt_version", request.promptVersion());
        putIfMissing(copy, "rubric_version", request.rubricVersion());
        putIfMissing(copy, "schema_version", request.schemaVersion());
        putIfMissing(copy, "audio_media_id", request.audioMediaId());
        putIfMissing(copy, "media_version", request.mediaVersion());
        putIfMissing(copy, "transcript", request.transcript());
        putIfMissing(copy, "normalized_transcript", request.normalizedTranscript());
        putIfMissing(copy, "actually_heard_transcript", request.actuallyHeardTranscript());
        putIfMissing(copy, "transcript_confidence", request.transcriptConfidence());
        if (request.textFallback()) {
            putIfMissing(copy, "evaluation_status", SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED.name());
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

    private static void putIfMissing(ObjectNode node, String field, String value) {
        if (!node.hasNonNull(field) && value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static void putIfMissing(ObjectNode node, String field, Long value) {
        if (!node.hasNonNull(field) && value != null) {
            node.put(field, value);
        }
    }

    private static void putIfMissing(ObjectNode node, String field, java.math.BigDecimal value) {
        if (!node.hasNonNull(field) && value != null) {
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
            SpeakingTranscriptionResult transcriptionResult,
            String textFallbackAnswer
    ) {
        @Override
        public String toString() {
            return "SpeakingEvaluationOrchestrator.Input{"
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
                    + ", transcriptionStatus=" + (transcriptionResult == null ? null : transcriptionResult.status())
                    + ", textFallbackPresent=" + (textFallbackAnswer != null && !textFallbackAnswer.isBlank())
                    + '}';
        }
    }
}
