package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingFeedbackCompatibilityReaderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingFeedbackCompatibilityReader reader = new SpeakingFeedbackCompatibilityReader();

    @Test
    void legacyMockScoreRemainsReadableButFailsClosedAsUnverified() throws Exception {
        SpeakingEvaluationResult result = reader.read(objectMapper.readTree("""
                {
                  "score":5.5,
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
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertEquals(SpeakingEvaluatorCapability.LEGACY_UNKNOWN, result.evaluatorCapability());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertEquals(3, result.rubricScores().size());
        assertTrue(result.rubricScores().stream().allMatch(row ->
                row.availability() == SpeakingCriterionAvailability.LEGACY_UNVERIFIED
                        && row.score() == null && row.maxScore() == null));
        assertEquals("Sample", result.sampleAnswer());
        assertEquals("Corrected", result.upgradedAnswer());
    }

    @Test
    void legacyGlobalScoreIsUnavailableRatherThanSilentlyUpgraded() throws Exception {
        SpeakingEvaluationResult result = reader.read(objectMapper.readTree("""
                {"score":7,"summary":"Legacy feedback","source":"old_engine"}
                """));

        assertEquals(SpeakingEvaluationStatus.LEGACY_RESULT, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertEquals(SpeakingEvidenceMode.UNKNOWN, result.evidenceMode());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertNull(result.audioMediaId());
    }

    @Test
    void typedCurrentEnvelopeRetainsPartialLanguageProfileWithoutHolisticScore() {
        SpeakingEvaluationResult result = reader.read(objectMapper.valueToTree(currentTypedResult()));

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
        assertTrue(result.currentEvidenceContract());
        assertTrue(result.profileAvailable());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertEquals("들은 문장", result.actuallyHeardTranscript());
        assertEquals("업그레이드 답안", result.upgradedAnswer());
    }

    @Test
    void typedCurrentEnvelopeMustPersistExplicitAcousticNotScorableRows() {
        ObjectNode stored = storedCurrentResult();
        ArrayNode rows = profileRows(stored);
        rows.remove(5);
        rows.remove(4);

        SpeakingEvaluationResult result = reader.read(stored);

        assertLegacyUnverifiedWithoutScores(result);
    }

    @Test
    void stalePromptRubricOrSchemaVersionFailsClosed() {
        for (String field : List.of("promptVersion", "rubricVersion", "schemaVersion")) {
            ObjectNode stored = storedCurrentResult();
            stored.put(field, "stale-" + field);

            assertLegacyUnverifiedWithoutScores(reader.read(stored));
        }
    }

    @Test
    void duplicateTranscriptCriterionFailsClosed() {
        ObjectNode stored = storedCurrentResult();
        ArrayNode rows = profileRows(stored);
        rows.add(rows.get(0).deepCopy());

        assertLegacyUnverifiedWithoutScores(reader.read(stored));
    }

    @Test
    void missingTranscriptCriterionFailsClosed() {
        ObjectNode stored = storedCurrentResult();
        profileRows(stored).remove(3);

        assertLegacyUnverifiedWithoutScores(reader.read(stored));
    }

    @Test
    void wrongCriterionMaximumFailsClosed() {
        ObjectNode stored = storedCurrentResult();
        ((ObjectNode) profileRows(stored).get(0)).put("maxScore", 21);

        assertLegacyUnverifiedWithoutScores(reader.read(stored));
    }

    @Test
    void negativeOrAboveMaximumScoreFailsClosed() {
        for (int score : List.of(-1, 21)) {
            ObjectNode stored = storedCurrentResult();
            ((ObjectNode) profileRows(stored).get(0)).put("score", score);

            assertLegacyUnverifiedWithoutScores(reader.read(stored));
        }
    }

    @Test
    void persistedAcousticNumberFailsClosedEvenWhenMarkedNotScorable() {
        ObjectNode stored = storedCurrentResult();
        ObjectNode acoustic = (ObjectNode) profileRows(stored).get(4);
        acoustic.put("score", 8);
        acoustic.put("maxScore", 15);
        acoustic.put("availability", SpeakingCriterionAvailability.NOT_SCORABLE.name());

        assertLegacyUnverifiedWithoutScores(reader.read(stored));
    }

    @Test
    void nonScoreBearingStatusCannotCarryAReadyProfile() {
        ObjectNode stored = storedCurrentResult();
        stored.put("evaluationStatus", SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE.name());

        assertLegacyUnverifiedWithoutScores(reader.read(stored));
    }

    @Test
    void storedLowConfidenceEnvelopeRemainsCurrentOnlyWithNoNumericProfile() {
        ObjectNode stored = storedCurrentResult();
        stored.put("evaluationStatus", SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE.name());
        stored.putArray("rubricScores");
        stored.putArray("criterionFeedback");

        SpeakingEvaluationResult result = reader.read(stored);

        assertTrue(result.currentEvidenceContract());
        assertFalse(result.profileAvailable());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertTrue(result.rubricScores().isEmpty());
        assertTrue(result.criterionFeedback().isEmpty());
        assertEquals("들은 문장", result.actuallyHeardTranscript());
        assertEquals(new BigDecimal("0.91"), result.transcriptConfidence());
    }

    @Test
    void storedCurrentEnvelopeWithForgedTranscriptEvidenceFailsClosed() {
        ObjectNode stored = storedCurrentResult();
        ObjectNode forged = stored.putArray("transcriptAnnotations").addObject();
        forged.put("annotationType", "needs_improvement");
        forged.put("category", "GRAMMAR");
        forged.put("criterion", "S_GRAMMAR_SENTENCE_CONTROL");
        forged.put("subCriterionId", "S_GRAMMAR_PARTICLES");
        forged.put("originalSpan", "giả");
        forged.put("startOffset", 0);
        forged.put("endOffset", 3);
        forged.put("evidenceSource", "TRANSCRIPT");
        forged.put("evidenceScope", "TEXT_SPAN");
        forged.put("evidence", "giả");
        forged.put("explanationVi", "Bằng chứng không có trong transcript.");

        SpeakingEvaluationResult result = reader.read(stored);

        assertLegacyUnverifiedWithoutScores(result);
        assertTrue(result.transcriptAnnotations().isEmpty());
    }

    @Test
    void storedCurrentEnvelopeWithForgedCriterionSubtotalFailsClosed() {
        ObjectNode stored = storedCurrentResult();
        ObjectNode forged = stored.putArray("criterionFeedback").addObject();
        forged.put("criterion", "S_CONTENT_TASK_FULFILLMENT");
        forged.put("score", 20);
        forged.put("maxScore", 20);
        forged.put("summary", "Điểm chi tiết không khớp rubric đã lưu.");
        forged.putArray("strengths");
        forged.putArray("needsImprovement");
        forged.putArray("subcriteria");

        SpeakingEvaluationResult result = reader.read(stored);

        assertLegacyUnverifiedWithoutScores(result);
    }

    @Test
    void storedSnakeCaseSixCriterionResultIsLegacyUnverified() throws Exception {
        SpeakingEvaluationResult result = reader.read(objectMapper.readTree("""
                {
                  "evaluation_status":"EVALUATED",
                  "score_available":true,
                  "source":"PROVIDER",
                  "overall_score":80,
                  "overall_summary":"Tổng quan",
                  "rubric_scores":[
                    {"criterion_id":"S_CONTENT_TASK_FULFILLMENT","score":16,"feedback":"ok"},
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","score":16,"feedback":"ok"},
                    {"criterion_id":"S_VOCABULARY_EXPRESSIONS","score":12,"feedback":"ok"},
                    {"criterion_id":"S_COHERENCE_ORGANIZATION","score":12,"feedback":"ok"},
                    {"criterion_id":"S_FLUENCY","score":12,"feedback":"ok"},
                    {"criterion_id":"S_PRONUNCIATION_DELIVERY","score":12,"feedback":"ok"},
                    {"score":9,"feedback":"Không có identity nên không được gán theo vị trí"},
                    {"criterion_id":"S_UNKNOWN_FUTURE","score":9,"feedback":"Identity lạ phải bị bỏ"},
                    {"criterion_id":"S_CONTENT_TASK_FULFILLMENT","score":19,"feedback":"Identity trùng phải bị bỏ"}
                  ]
                }
                """));

        assertEquals(SpeakingEvaluationStatus.LEGACY_RESULT, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertEquals(6, result.rubricScores().size());
        assertEquals(List.of(
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                        SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                        SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                        SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                        SpeakingRubricCriterion.FLUENCY,
                        SpeakingRubricCriterion.PRONUNCIATION_DELIVERY),
                result.rubricScores().stream()
                        .map(SpeakingEvaluationResult.RubricScore::criterion)
                        .toList());
        assertTrue(result.rubricScores().stream().allMatch(row ->
                row.availability() == SpeakingCriterionAvailability.LEGACY_UNVERIFIED
                        && row.score() == null
                        && row.maxScore() == null
                        && row.feedback() == null));
    }

    @Test
    void unknownFutureCapabilityFailsClosedAsLegacyUnverified() {
        com.fasterxml.jackson.databind.node.ObjectNode stored = objectMapper.valueToTree(currentTypedResult());
        stored.put("evaluatorCapability", "FUTURE_UNKNOWN_CAPABILITY");
        stored.put("evidenceMode", "FUTURE_UNKNOWN_MODE");
        stored.put("scoreAvailable", true);
        stored.put("overallScore", 92);

        SpeakingEvaluationResult result = reader.read(stored);

        assertEquals(SpeakingEvaluatorCapability.LEGACY_UNKNOWN, result.evaluatorCapability());
        assertEquals(SpeakingEvidenceMode.UNKNOWN, result.evidenceMode());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
    }

    @Test
    void reservedDirectAudioCapabilityCannotEnableScoresBeforeRolloutGate() {
        com.fasterxml.jackson.databind.node.ObjectNode stored = objectMapper.valueToTree(currentTypedResult());
        stored.put("evaluatorCapability", "AUDIO_DIRECT_FULL_RESERVED");
        stored.put("evidenceMode", "DIRECT_AUDIO_AND_TRANSCRIPT");
        stored.put("evidenceContractVersion", "speaking-evidence-future-audio-direct-reserved");
        stored.put("contractTrust", "CURRENT_VERIFIED");
        stored.put("scoreAvailable", true);
        stored.put("overallScore", 92);

        SpeakingEvaluationResult result = reader.read(stored);

        assertEquals(SpeakingEvaluatorCapability.LEGACY_UNKNOWN, result.evaluatorCapability());
        assertEquals(SpeakingEvidenceMode.UNKNOWN, result.evidenceMode());
        assertNull(result.evidenceContractVersion());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertFalse(result.currentEvidenceContract());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
    }

    private SpeakingEvaluationResult currentTypedResult() {
        return new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.EVALUATED,
                false,
                SpeakingEvaluationSource.PROVIDER,
                "gemini-compatible",
                "gpt-4o-mini-transcribe",
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                SpeakingContractTrust.CURRENT_VERIFIED,
                44L,
                5L,
                "들은 문장",
                "들은 문장",
                "들은 문장",
                "intent",
                null,
                new BigDecimal("0.91"),
                null,
                null,
                null,
                "Tổng quan",
                "Đạt yêu cầu",
                List.of("Rõ ý"),
                List.of("Thêm ví dụ"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Tin cậy",
                currentProfileRows(),
                List.of(),
                List.of(),
                List.of(),
                "업그레이드 답안",
                "샘플 답안",
                List.of(),
                List.of(),
                null,
                false);
    }

    private ObjectNode storedCurrentResult() {
        return objectMapper.valueToTree(currentTypedResult());
    }

    private ArrayNode profileRows(ObjectNode stored) {
        return (ArrayNode) stored.get("rubricScores");
    }

    private void assertLegacyUnverifiedWithoutScores(SpeakingEvaluationResult result) {
        assertEquals(SpeakingEvaluationStatus.LEGACY_RESULT, result.evaluationStatus());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertFalse(result.currentEvidenceContract());
        assertFalse(result.profileAvailable());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertTrue(result.rubricScores().stream().allMatch(row ->
                row.score() == null && row.maxScore() == null
                        && row.availability() == SpeakingCriterionAvailability.LEGACY_UNVERIFIED));
        assertTrue(result.criterionFeedback().stream().allMatch(row ->
                row.score() == null && row.maxScore() == null));
    }

    private List<SpeakingEvaluationResult.RubricScore> currentProfileRows() {
        return List.of(
                scored(SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT, "16", "20"),
                scored(SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL, "16", "20"),
                scored(SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS, "12", "15"),
                scored(SpeakingRubricCriterion.COHERENCE_ORGANIZATION, "12", "15"),
                unavailable(SpeakingRubricCriterion.FLUENCY),
                unavailable(SpeakingRubricCriterion.PRONUNCIATION_DELIVERY));
    }

    private SpeakingEvaluationResult.RubricScore scored(
            SpeakingRubricCriterion criterion,
            String score,
            String max
    ) {
        return new SpeakingEvaluationResult.RubricScore(
                criterion, new BigDecimal(score), new BigDecimal(max), "Tốt");
    }

    private SpeakingEvaluationResult.RubricScore unavailable(SpeakingRubricCriterion criterion) {
        return new SpeakingEvaluationResult.RubricScore(
                criterion, null, null, "Không có âm thanh",
                SpeakingCriterionAvailability.NOT_SCORABLE);
    }
}
