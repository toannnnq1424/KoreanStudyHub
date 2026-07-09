package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingEvaluationNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingEvaluationNormalizer normalizer = new SpeakingEvaluationNormalizer();

    @Test
    void validProviderLikeJsonNormalizesToTypedResult() throws Exception {
        SpeakingEvaluationResult result = normalizer.normalize(validInput());

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
        assertTrue(result.scoreAvailable());
        assertEquals(new BigDecimal("78.00"), result.overallScore());
        assertEquals(6, result.rubricScores().size());
        assertEquals(Long.valueOf(44), result.audioMediaId());
        assertEquals(Long.valueOf(3), result.mediaVersion());
        assertEquals(SpeakingEvaluationNormalizer.SCHEMA_VERSION, result.schemaVersion());
    }

    @Test
    void missingOptionalFieldsHaveSafeDefaults() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "evaluation_status":"EVALUATED",
                  "overall_score":60,
                  "rubric_scores":[
                    {"criterion":"CONTENT_TASK_FULFILLMENT","score":12},
                    {"criterion":"GRAMMAR_SENTENCE_CONTROL","score":12},
                    {"criterion":"VOCABULARY_EXPRESSIONS","score":9},
                    {"criterion":"COHERENCE_ORGANIZATION","score":9},
                    {"criterion":"FLUENCY","score":9},
                    {"criterion":"PRONUNCIATION_DELIVERY","score":9}
                  ]
                }
                """);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.scoreAvailable());
        assertNotNull(result.rubricScores());
        assertNotNull(result.evidence());
        assertNotNull(result.recommendations());
        assertEquals(SpeakingEvaluationSource.PROVIDER, result.source());
    }

    @Test
    void malformedStatusBecomesContractFailureWithoutScore() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"evaluation_status":"NOT_A_STATUS","overall_score":80,"rubric_scores":[]}
                """);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertEquals("INVALID_EVALUATION_STATUS", result.errorCategory());
    }

    @Test
    void invalidOverallScoreDoesNotBecomeFabricatedLowScore() throws Exception {
        JsonNode input = (JsonNode) validInput().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("overall_score", 101);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.INVALID_PROVIDER_RESULT, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
    }

    @Test
    void failureStatusesAlwaysProduceUnavailableScore() throws Exception {
        for (SpeakingEvaluationStatus status : SpeakingEvaluationStatus.values()) {
            if (status.scoreBearing()) {
                continue;
            }
            JsonNode input = objectMapper.readTree("""
                    {"evaluation_status":"%s","overall_score":99,"error_category":"EXPECTED_FAILURE"}
                    """.formatted(status.name()));

            SpeakingEvaluationResult result = normalizer.normalize(input);

            assertEquals(status, result.evaluationStatus());
            assertFalse(result.scoreAvailable());
            assertNull(result.overallScore());
        }
    }

    @Test
    void actuallyHeardTranscriptAndInterpretedIntentRemainSeparate() throws Exception {
        SpeakingEvaluationResult result = normalizer.normalize(validInput());

        assertEquals("저는 학교... 갔어요", result.actuallyHeardTranscript());
        assertEquals("The learner intended to say that they went to school.", result.interpretedIntent());
        assertNotEquals(result.actuallyHeardTranscript(), result.interpretedIntent());
    }

    @Test
    void interpretedIntentEvidenceDoesNotPreventGroundedScoreCaps() throws Exception {
        JsonNode input = objectMapper.readTree(validInput().toString().replace(
                "\"transcript_confidence\":0.9",
                "\"transcript_confidence\":0.2").replace(
                "\"source\":\"TRANSCRIPT\"",
                "\"source\":\"INTERPRETED_INTENT\"").replace(
                "\"source\":\"AUDIO_METADATA\"",
                "\"source\":\"INTERPRETED_INTENT\""));

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE, result.evaluationStatus());
        assertEquals(new BigDecimal("10.00"), score(result, SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL));
        assertEquals(new BigDecimal("7.50"), score(result, SpeakingRubricCriterion.FLUENCY));
        assertEquals(new BigDecimal("7.50"), score(result, SpeakingRubricCriterion.PRONUNCIATION_DELIVERY));
        assertTrue(result.recommendations().stream().anyMatch(value -> value.contains("Low transcript confidence")));
    }

    @Test
    void lowConfidenceKeepsCriterionWithGroundedEvidenceButCapsWeakOnes() throws Exception {
        JsonNode input = objectMapper.readTree(validInput().toString().replace(
                "\"transcript_confidence\":0.9",
                "\"transcript_confidence\":0.2"));

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(new BigDecimal("16.00"), score(result, SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL));
        assertEquals(new BigDecimal("11.00"), score(result, SpeakingRubricCriterion.FLUENCY));
        assertEquals(new BigDecimal("7.50"), score(result, SpeakingRubricCriterion.PRONUNCIATION_DELIVERY));
        assertEquals(new BigDecimal("75.50"), result.overallScore());
    }

    @Test
    void allSupportedEvidenceSourcesAreTyped() throws Exception {
        SpeakingEvaluationResult result = normalizer.normalize(validInput());
        Set<SpeakingEvidenceSource> sources = result.evidence().stream()
                .map(SpeakingEvaluationResult.Evidence::source)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                SpeakingEvidenceSource.TRANSCRIPT,
                SpeakingEvidenceSource.AUDIO_METADATA,
                SpeakingEvidenceSource.PROMPT,
                SpeakingEvidenceSource.INTERPRETED_INTENT), sources);
    }

    @Test
    void impossibleOverallAndRubricCombinationUsesDeterministicRubricTotal() throws Exception {
        JsonNode input = (JsonNode) validInput().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("overall_score", 99);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.scoreAvailable());
        assertEquals(new BigDecimal("78.00"), result.overallScore());
    }

    @Test
    void normalizedDtoCannotExposeStorageOrIdentitySecrets() throws Exception {
        String json = objectMapper.writeValueAsString(normalizer.normalize(validInput()));

        assertFalse(json.contains("storageKey"));
        assertFalse(json.contains("playbackPath"));
        assertFalse(json.contains("contentHash"));
        assertFalse(json.contains("userId"));
        assertFalse(json.contains("apiKey"));
        assertFalse(json.contains("providerSecret"));
    }

    private BigDecimal score(SpeakingEvaluationResult result, SpeakingRubricCriterion criterion) {
        return result.rubricScores().stream()
                .filter(row -> row.criterion() == criterion)
                .findFirst()
                .orElseThrow()
                .score();
    }

    private JsonNode validInput() throws Exception {
        return objectMapper.readTree("""
                {
                  "evaluation_status":"EVALUATED",
                  "source":"PROVIDER",
                  "model":"fake-evaluator",
                  "transcription_model":"fake-transcriber",
                  "audio_media_id":44,
                  "media_version":3,
                  "transcript":"저는 학교 갔어요",
                  "normalized_transcript":"저는 학교에 갔어요.",
                  "actually_heard_transcript":"저는 학교... 갔어요",
                  "interpreted_intent":"The learner intended to say that they went to school.",
                  "intent_confidence":0.8,
                  "transcript_confidence":0.9,
                  "listener_burden":"LOW",
                  "overall_score":78,
                  "level_label":"KSH internal practice level",
                  "rubric_scores":[
                    {"criterion":"CONTENT_TASK_FULFILLMENT","score":17,"feedback":"Relevant"},
                    {"criterion":"GRAMMAR_SENTENCE_CONTROL","score":16,"feedback":"Mostly controlled"},
                    {"criterion":"VOCABULARY_EXPRESSIONS","score":12,"feedback":"Adequate"},
                    {"criterion":"COHERENCE_ORGANIZATION","score":12,"feedback":"Clear"},
                    {"criterion":"FLUENCY","score":11,"feedback":"Some hesitation"},
                    {"criterion":"PRONUNCIATION_DELIVERY","score":10,"feedback":"Advisory only"}
                  ],
                  "evidence":[
                    {"source":"TRANSCRIPT","criterion":"GRAMMAR_SENTENCE_CONTROL","excerpt":"학교 갔어요","confidence":0.9},
                    {"source":"AUDIO_METADATA","criterion":"FLUENCY","excerpt":"pause metadata","confidence":0.8},
                    {"source":"PROMPT","criterion":"CONTENT_TASK_FULFILLMENT","excerpt":"task requirement","confidence":1},
                    {"source":"INTERPRETED_INTENT","criterion":"CONTENT_TASK_FULFILLMENT","excerpt":"intended meaning","confidence":0.8}
                  ],
                  "findings":[{"category":"UNKNOWN_SAFE_CATEGORY","message":"Safe finding"}],
                  "recommendations":["Keep practicing"],
                  "upgraded_answer":"저는 학교에 갔어요.",
                  "sample_answer":"어제 학교에 갔어요.",
                  "pronunciation_advisory":["Possible batchim issue"],
                  "fluency_observations":["One long pause"],
                  "retryable":false
                }
                """);
    }
}
