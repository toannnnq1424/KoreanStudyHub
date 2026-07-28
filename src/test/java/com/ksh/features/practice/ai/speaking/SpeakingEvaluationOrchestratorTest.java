package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionErrorCategory;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpeakingEvaluationOrchestratorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingEvaluationNormalizer normalizer = new SpeakingEvaluationNormalizer();
    private final SpeakingEvaluatorProperties properties = new SpeakingEvaluatorProperties(
            false,
            "openai-compatible",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "secret-key",
            "models/gemini-2.5-flash",
            Duration.ofSeconds(30),
            0,
            SpeakingPromptRules.PROMPT_VERSION,
            SpeakingPromptRules.RUBRIC_VERSION,
            SpeakingPromptRules.SCHEMA_VERSION);

    @Test
    void validProviderJsonNormalizesThrough8EAFoundation() throws Exception {
        FakeClient client = FakeClient.success(objectMapper.readTree(OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson()));
        SpeakingEvaluationOrchestrator orchestrator = orchestrator(client);

        SpeakingEvaluationResult result = orchestrator.evaluate(input(transcription(SpeakingEvaluationStatus.EVALUATED,
                new BigDecimal("0.81")), false));

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertThat(result.overallScore()).isNull();
        assertThat(result.profileAvailable()).isTrue();
        assertEquals(SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                result.evaluatorCapability());
        assertEquals("models/gemini-2.5-flash", result.model());
        assertEquals(Long.valueOf(12), result.audioMediaId());
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void failedTranscriptionPreservesFailureAndSkipsEvaluatorCall() {
        FakeClient client = FakeClient.success(null);
        SpeakingEvaluationOrchestrator orchestrator = orchestrator(client);

        SpeakingEvaluationResult result = orchestrator.evaluate(input(SpeakingTranscriptionResult.failure(
                SpeakingEvaluationStatus.AUDIO_UNAVAILABLE,
                "openai",
                "gpt-4o-mini-transcribe",
                "ko",
                SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE,
                false), false));

        assertEquals(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertThat(result.currentEvidenceContract()).isTrue();
        assertThat(client.calls()).isZero();
    }

    @Test
    void evaluatorFailureDoesNotFabricateScore() {
        FakeClient client = FakeClient.failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                "MISSING_API_KEY", false);
        SpeakingEvaluationResult result = orchestrator(client).evaluate(input(transcription(
                SpeakingEvaluationStatus.EVALUATED, null), false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertThat(result.overallScore()).isNull();
        assertEquals("MISSING_API_KEY", result.errorCategory());
    }

    @Test
    void schemaViolationMapsContractFailure() throws Exception {
        JsonNode invalid = objectMapper.readTree("""
                {"evaluation_status":"EVALUATED","overall_score":90,"rubric_scores":[]}
                """);

        SpeakingEvaluationResult result = orchestrator(FakeClient.success(invalid))
                .evaluate(input(transcription(SpeakingEvaluationStatus.EVALUATED, null), false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
    }

    @Test
    void authoritativeTranscriptionConfidenceDoesNotEnableAcousticScoring() throws Exception {
        JsonNode evaluation = objectMapper.readTree(OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson()
                .replace("\"transcript_confidence\":0.81", "\"transcript_confidence\":0.2")
                .replace("\"source\":\"TRANSCRIPT\"", "\"source\":\"INTERPRETED_INTENT\""));

        SpeakingEvaluationResult result = orchestrator(FakeClient.success(evaluation))
                .evaluate(input(transcription(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE,
                        new BigDecimal("0.20")), false));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE, result.evaluationStatus());
        assertEquals(new BigDecimal("0.20"), result.transcriptConfidence());
        assertFalse(result.profileAvailable());
        assertFalse(result.holisticScoreAvailable());
        assertThat(result.rubricScores()).isEmpty();
        assertThat(result.criterionFeedback()).isEmpty();
        assertThat(result.overallScore()).isNull();
    }

    @Test
    void authoritativeLowConfidenceStatusFailsClosedEvenWhenNumericConfidenceLooksHigh() throws Exception {
        JsonNode evaluation = objectMapper.readTree(
                OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson());

        SpeakingEvaluationResult result = orchestrator(FakeClient.success(evaluation))
                .evaluate(input(transcription(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE,
                        new BigDecimal("0.91")), false));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE, result.evaluationStatus());
        assertEquals(new BigDecimal("0.91"), result.transcriptConfidence());
        assertFalse(result.profileAvailable());
        assertThat(result.rubricScores()).isEmpty();
        assertThat(result.criterionFeedback()).isEmpty();
        assertThat(result.overallScore()).isNull();
    }

    @Test
    void interpretedIntentCannotRepairGrammarFluencyOrPronunciationScores() throws Exception {
        JsonNode evaluation = objectMapper.readTree(OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson()
                .replace("\"transcript_confidence\":0.81", "\"transcript_confidence\":0.2")
                .replace("\"source\":\"TRANSCRIPT\"", "\"source\":\"INTERPRETED_INTENT\"")
                .replace("\"source\":\"PROMPT\"", "\"source\":\"INTERPRETED_INTENT\""));

        SpeakingEvaluationResult result = orchestrator(FakeClient.success(evaluation))
                .evaluate(input(transcription(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE,
                        new BigDecimal("0.20")), false));

        assertThat(result.evidence()).isEmpty();
        assertThat(result.rubricScores()).isEmpty();
        assertFalse(result.profileAvailable());
        assertThat(result.overallScore()).isNull();
    }

    @Test
    void textFallbackMapsExplicitStatusAndKeepsAcousticCriteriaNotScorable() throws Exception {
        JsonNode evaluation = objectMapper.readTree(OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson()
                .replace("\"evaluation_status\":\"EVALUATED\"", "\"evaluation_status\":\"TEXT_FALLBACK_EVALUATED\"")
                .replace("\"source\":\"PROVIDER\"", "\"source\":\"TEXT_FALLBACK\"")
                .replace("\"score\":10,\"max_score\":15,\"feedback\":\"Advisory only\"",
                        "\"score\":14,\"max_score\":15,\"feedback\":\"Not audio-grounded\""));

        SpeakingEvaluationResult result = orchestrator(FakeClient.success(evaluation))
                .evaluate(input(transcription(SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED, null), true));

        assertEquals(SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED, result.evaluationStatus());
        assertEquals(SpeakingEvaluationSource.TEXT_FALLBACK, result.source());
        assertThat(score(result, SpeakingRubricCriterion.FLUENCY)).isNull();
        assertThat(score(result, SpeakingRubricCriterion.PRONUNCIATION_DELIVERY)).isNull();
        assertThat(result.overallScore()).isNull();
        assertThat(result.audioMediaId()).isNull();
        assertThat(result.mediaVersion()).isNull();
    }

    @Test
    void inputToStringDoesNotExposeTranscriptPromptOrSecrets() {
        SpeakingEvaluationOrchestrator.Input input = input(transcription(SpeakingEvaluationStatus.EVALUATED, null), false);

        assertThat(input.toString())
                .doesNotContain("저는 학생")
                .doesNotContain("자기소개")
                .doesNotContain("secret-key")
                .doesNotContain("storage-key")
                .doesNotContain("playback");
    }

    private SpeakingEvaluationOrchestrator orchestrator(FakeClient client) {
        return new SpeakingEvaluationOrchestrator(client, normalizer, properties, objectMapper);
    }

    private SpeakingEvaluationOrchestrator.Input input(SpeakingTranscriptionResult transcription, boolean textFallback) {
        return new SpeakingEvaluationOrchestrator.Input(
                10L,
                11L,
                "자기소개를 하세요.",
                "TOPIK II",
                "Say who you are and what you study.",
                textFallback ? null : 12L,
                textFallback ? null : 13L,
                textFallback ? null : "audio/webm",
                textFallback ? null : 12345L,
                textFallback ? null : 3200L,
                transcription,
                textFallback ? "저는 학생 이에요" : null);
    }

    private SpeakingTranscriptionResult transcription(SpeakingEvaluationStatus status, BigDecimal confidence) {
        return new SpeakingTranscriptionResult(
                status,
                status == SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED
                        ? SpeakingEvaluationSource.TEXT_FALLBACK : SpeakingEvaluationSource.PROVIDER,
                status == SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED ? "text" : "openai",
                status == SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED ? null : "gpt-4o-mini-transcribe",
                "ko",
                "저는 학생 이에요",
                "저는 학생이에요.",
                confidence,
                null,
                3200L,
                25L,
                null,
                false);
    }

    private BigDecimal score(SpeakingEvaluationResult result, SpeakingRubricCriterion criterion) {
        return result.rubricScores().stream()
                .filter(row -> row.criterion() == criterion)
                .findFirst()
                .orElseThrow()
                .score();
    }

    private static class FakeClient implements SpeakingEvaluationClient {
        private final SpeakingEvaluationProviderResult result;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeClient(SpeakingEvaluationProviderResult result) {
            this.result = result;
        }

        static FakeClient success(JsonNode json) {
            return new FakeClient(SpeakingEvaluationProviderResult.success(
                    json, "openai-compatible", "models/gemini-2.5-flash", 1L));
        }

        static FakeClient failure(SpeakingEvaluationStatus status, String errorCategory, boolean retryable) {
            return new FakeClient(SpeakingEvaluationProviderResult.failure(
                    status, "openai-compatible", "models/gemini-2.5-flash", errorCategory, retryable, 1L));
        }

        @Override
        public SpeakingEvaluationProviderResult evaluate(SpeakingEvaluationRequest request) {
            calls.incrementAndGet();
            return result;
        }

        int calls() {
            return calls.get();
        }
    }
}
