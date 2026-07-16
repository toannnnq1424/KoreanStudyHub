package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingFeedbackCompatibilityReaderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingFeedbackCompatibilityReader reader = new SpeakingFeedbackCompatibilityReader();

    @Test
    void currentMockSpeakingJsonRemainsReadable() throws Exception {
        SpeakingEvaluationResult result = reader.read(objectMapper.readTree("""
                {
                  "score":5.5,
                  "overall_score":5.5,
                  "percentage":61.11,
                  "summary":"Mock text feedback",
                  "source":"practice_speaking_mock",
                  "engine":"KSH_SPEAKING_MOCK_V1",
                  "rubric_scores":[
                    {"name":"Content","score":5.5,"feedback":"Feedback"},
                    {"name":"Structure","score":5.0,"feedback":"Feedback"},
                    {"name":"Language","score":4.5,"feedback":"Feedback"}
                  ],
                  "sample_answer":"Sample",
                  "corrected_version":"Corrected"
                }
                """));

        assertEquals(SpeakingEvaluationStatus.MOCK_EVALUATED, result.evaluationStatus());
        assertEquals(SpeakingEvaluationSource.MOCK, result.source());
        assertTrue(result.scoreAvailable());
        assertEquals(new BigDecimal("61.11"), result.overallScore());
        assertEquals(3, result.rubricScores().size());
        assertEquals("Sample", result.sampleAnswer());
        assertEquals("Corrected", result.upgradedAnswer());
    }

    @Test
    void legacyGlobalFeedbackGetsSafeNullableDefaults() throws Exception {
        SpeakingEvaluationResult result = reader.read(objectMapper.readTree("""
                {"score":7,"summary":"Legacy feedback","source":"old_engine"}
                """));

        assertEquals(SpeakingEvaluationStatus.LEGACY_RESULT, result.evaluationStatus());
        assertTrue(result.scoreAvailable());
        assertNotNull(result.rubricScores());
        assertNull(result.audioMediaId());
        assertNull(result.transcript());
    }

    @Test
    void typedInternalJsonRemainsReadableForPersistence() {
        SpeakingEvaluationResult original = new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.EVALUATED,
                true,
                SpeakingEvaluationSource.PROVIDER,
                "gemini-compatible",
                "gpt-4o-mini-transcribe",
                "p1",
                "r1",
                "s1",
                44L,
                5L,
                "들은 문장",
                "들은 문장",
                "들은 문장",
                "intent",
                null,
                new BigDecimal("0.91"),
                "LOW",
                new BigDecimal("80"),
                "B1",
                "Tổng quan",
                "Đạt yêu cầu",
                java.util.List.of("Rõ ý"),
                java.util.List.of("Thêm ví dụ"),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                "Tin cậy",
                java.util.List.of(new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                        new BigDecimal("16"),
                        new BigDecimal("20"),
                        "Tốt")),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                "업그레이드 답안",
                "샘플 답안",
                java.util.List.of(),
                java.util.List.of(),
                null,
                false);

        SpeakingEvaluationResult result = reader.read(objectMapper.valueToTree(original));

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
        assertEquals(0, result.overallScore().compareTo(new BigDecimal("80")));
        assertEquals("들은 문장", result.actuallyHeardTranscript());
        assertEquals("업그레이드 답안", result.upgradedAnswer());
    }

    @Test
    void providerSnakeCaseJsonNormalizesForPersistenceReadback() throws Exception {
        SpeakingEvaluationResult result = reader.read(objectMapper.readTree("""
                {
                  "evaluation_status":"EVALUATED",
                  "score_available":true,
                  "source":"PROVIDER",
                  "overall_score":80,
                  "overall_summary":"Tổng quan",
                  "task_achievement_summary":"Đạt yêu cầu",
                  "rubric_scores":[
                    {"criterion_id":"S_CONTENT_TASK_FULFILLMENT","score":16,"feedback":"ok"},
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","score":16,"feedback":"ok"},
                    {"criterion_id":"S_VOCABULARY_EXPRESSIONS","score":12,"feedback":"ok"},
                    {"criterion_id":"S_COHERENCE_ORGANIZATION","score":12,"feedback":"ok"},
                    {"criterion_id":"S_FLUENCY","score":12,"feedback":"ok"},
                    {"criterion_id":"S_PRONUNCIATION_DELIVERY","score":12,"feedback":"ok"}
                  ],
                  "upgraded_answer":"업그레이드 답안",
                  "sample_answer":"샘플 답안"
                }
                """));

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
        assertEquals(new BigDecimal("80.00"), result.overallScore());
        assertEquals(6, result.rubricScores().size());
    }
}
