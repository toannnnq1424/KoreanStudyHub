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
}
