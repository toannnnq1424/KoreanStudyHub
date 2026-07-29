package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SpeakingEvaluationNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingEvaluationNormalizer normalizer = new SpeakingEvaluationNormalizer();

    @Test
    void validProviderLikeJsonNormalizesToTypedResult() throws Exception {
        SpeakingEvaluationResult result = normalizer.normalize(validInput());

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertNull(result.levelLabel());
        assertNull(result.listenerBurden());
        assertTrue(result.profileAvailable());
        assertFalse(result.holisticScoreAvailable());
        assertEquals(SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                result.evaluatorCapability());
        assertEquals(SpeakingEvidenceMode.TRANSCRIPT_ONLY, result.evidenceMode());
        assertEquals(SpeakingContractTrust.CURRENT_VERIFIED, result.contractTrust());
        assertEquals(
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                result.policyBundleId());
        assertEquals(
                SpeakingAssessmentPolicyBundle.fingerprint(),
                result.policyBundleFingerprint());
        assertEquals(6, result.rubricScores().size());
        assertNull(score(result, SpeakingRubricCriterion.FLUENCY));
        assertNull(score(result, SpeakingRubricCriterion.PRONUNCIATION_DELIVERY));
        assertEquals(SpeakingCriterionAvailability.NOT_SCORABLE,
                availability(result, SpeakingRubricCriterion.FLUENCY));
        assertEquals(SpeakingCriterionAvailability.NOT_SCORABLE,
                availability(result, SpeakingRubricCriterion.PRONUNCIATION_DELIVERY));
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

        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertFalse(result.profileAvailable());
        assertEquals(SpeakingEvaluationStatus.INVALID_PROVIDER_RESULT, result.evaluationStatus());
        assertEquals("MISSING_AUTHORITATIVE_TRANSCRIPT", result.errorCategory());
        assertNotNull(result.rubricScores());
        assertNotNull(result.evidence());
        assertNotNull(result.recommendations());
        assertEquals(SpeakingEvaluationSource.SYSTEM, result.source());
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
    void providerOverallScoreIsIgnoredRatherThanPersisted() throws Exception {
        JsonNode input = (JsonNode) validInput().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("overall_score", 101);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
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
    void providerInterpretedIntentIsNotPromotedWithoutBackendAuthority() throws Exception {
        SpeakingEvaluationResult result = normalizer.normalize(validInput());

        assertEquals("저는 학교... 갔어요", result.actuallyHeardTranscript());
        assertNull(result.interpretedIntent());
        assertNull(result.intentConfidence());
    }

    @Test
    void interpretedIntentAndLowConfidenceNeverEnableAcousticScoring() throws Exception {
        JsonNode input = objectMapper.readTree(validInput().toString().replace(
                "\"transcript_confidence\":0.9",
                "\"transcript_confidence\":0.2").replace(
                "\"source\":\"TRANSCRIPT\"",
                "\"source\":\"INTERPRETED_INTENT\"").replace(
                "\"source\":\"AUDIO_METADATA\"",
                "\"source\":\"INTERPRETED_INTENT\""));

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE, result.evaluationStatus());
        assertFalse(result.profileAvailable());
        assertFalse(result.holisticScoreAvailable());
        assertTrue(result.rubricScores().isEmpty());
        assertTrue(result.criterionFeedback().isEmpty());
        assertNull(result.overallScore());
        assertTrue(result.recommendations().stream().anyMatch(value -> value.contains("bản chép lời thấp")));
    }

    @Test
    void lowConfidencePreservesProvenanceButFailsClosedWithoutAnyNumericProfile() throws Exception {
        JsonNode input = objectMapper.readTree(validInput().toString().replace(
                "\"transcript_confidence\":0.9",
                "\"transcript_confidence\":0.2"));

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE, result.evaluationStatus());
        assertEquals(new BigDecimal("0.2"), result.transcriptConfidence());
        assertEquals(SpeakingEvaluationSource.PROVIDER, result.source());
        assertTrue(result.currentEvidenceContract());
        assertFalse(result.profileAvailable());
        assertTrue(result.rubricScores().isEmpty());
        assertTrue(result.criterionFeedback().isEmpty());
        assertNull(result.overallScore());
    }

    @Test
    void allSupportedEvidenceSourcesAreTyped() throws Exception {
        SpeakingEvaluationResult result = normalizer.normalize(validInput());
        Set<SpeakingEvidenceSource> sources = result.evidence().stream()
                .map(SpeakingEvaluationResult.Evidence::source)
                .collect(Collectors.toSet());

        assertEquals(Set.of(SpeakingEvidenceSource.TRANSCRIPT), sources);
        assertFalse(sources.contains(SpeakingEvidenceSource.AUDIO_METADATA));
    }

    @Test
    void richSpeakingFeedbackContractNormalizesForFutureRendering() throws Exception {
        SpeakingEvaluationResult result = normalizer.normalize(richInput());

        assertEquals(
                "Câu trả lời rõ ý và chỉ còn một số điểm ngôn ngữ cần chỉnh.",
                result.overallSummary());
        assertEquals(
                "Học viên giới thiệu bản thân và bám đúng chủ đề.",
                result.taskAchievementSummary());
        assertEquals(2, result.majorStrengths().size());
        assertEquals(2, result.majorNeedsImprovement().size());
        assertEquals(1, result.actionPlan().size());
        assertEquals(SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL, result.actionPlan().get(0).criterion());
        assertEquals("S_GRAMMAR_PARTICLES", result.actionPlan().get(0).subCriterionId());
        assertNull(result.confidenceNotes());
        assertEquals(1, result.strengths().size());
        assertEquals("S_CONTENT_RELEVANCE", result.strengths().get(0).subCriterionId());
        assertEquals("", result.strengths().get(0).correction());
        assertEquals(1, result.needsImprovement().size());
        assertEquals("학생이에요", result.needsImprovement().get(0).correction());
        assertEquals(4, result.criterionFeedback().size());
        assertThat(result.criterionFeedback()).allMatch(row -> row.criterion().transcriptGrounded());
        assertEquals(SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT, result.criterionFeedback().get(0).criterion());
        assertEquals("S_CONTENT_RELEVANCE", result.criterionFeedback().get(0).subcriteria().get(0).subCriterionId());
        assertEquals(1, result.transcriptAnnotations().size());
        assertEquals("needs_improvement", result.transcriptAnnotations().get(0).annotationType());
        assertEquals(SpeakingEvidenceSource.TRANSCRIPT, result.transcriptAnnotations().get(0).evidenceSource());
        assertEquals("TEXT_SPAN", result.transcriptAnnotations().get(0).evidenceScope());
        assertEquals("학생 이에요", result.transcriptAnnotations().get(0).evidence());
        assertEquals("학생이에요", result.transcriptAnnotations().get(0).suggestionKo());
        assertEquals(3, result.transcriptAnnotations().get(0).startOffset());
        assertEquals(9, result.transcriptAnnotations().get(0).endOffset());
    }

    @Test
    void nonexistentTranscriptSpanIsDroppedFromAnnotationsAndFindings() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode annotation =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("transcript_annotations").get(0);
        annotation.put("evidence", "존재하지 않는 구절");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.transcriptAnnotations().isEmpty());
        assertTrue(result.findings().isEmpty());
        assertEquals(SpeakingContractTrust.CURRENT_VERIFIED, result.contractTrust());
    }

    @Test
    void providerOffsetsAreIgnoredAndDerivedFromAuthoritativeTranscript() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode annotation =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("transcript_annotations").get(0);
        annotation.put("start_offset", 999);
        annotation.put("end_offset", -7);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(1, result.transcriptAnnotations().size());
        assertEquals(3, result.transcriptAnnotations().get(0).startOffset());
        assertEquals(9, result.transcriptAnnotations().get(0).endOffset());
    }

    @Test
    void wholeAnswerWithNonemptyEvidenceIsDropped() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode strength =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("strengths").get(0);
        strength.put("evidence_scope", "WHOLE_ANSWER");
        strength.put("evidence", "fake whole-answer highlight");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.strengths().isEmpty());
    }

    @Test
    void strengthWithProviderCorrectionIsDropped() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        ((com.fasterxml.jackson.databind.node.ObjectNode) input.path("strengths").get(0))
                .put("correction", "không được phép sửa trong điểm mạnh");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.strengths().isEmpty());
    }

    @Test
    void taskMetadataWithoutAuthoritativeEnvelopeIsDropped() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode need =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("needs_improvement").get(0);
        need.put("evidence_scope", "TASK_METADATA");
        need.put("evidence_source", "PROMPT");
        need.put("evidence", "provider-authored task claim");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.needsImprovement().isEmpty());
    }

    @Test
    void repeatedExactSpanUsesDistinctBackendDerivedOccurrencesInProviderOrder() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        input.put("actually_heard_transcript", "다시 다시 말해요");
        com.fasterxml.jackson.databind.node.ArrayNode annotations =
                (com.fasterxml.jackson.databind.node.ArrayNode) input.path("transcript_annotations");
        com.fasterxml.jackson.databind.node.ObjectNode first =
                (com.fasterxml.jackson.databind.node.ObjectNode) annotations.get(0).deepCopy();
        first.put("evidence", "다시");
        first.put("start_offset", 100);
        first.put("end_offset", 200);
        com.fasterxml.jackson.databind.node.ObjectNode second = first.deepCopy();
        annotations.removeAll();
        annotations.add(first);
        annotations.add(second);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(2, result.transcriptAnnotations().size());
        assertEquals(0, result.transcriptAnnotations().get(0).startOffset());
        assertEquals(2, result.transcriptAnnotations().get(0).endOffset());
        assertEquals(3, result.transcriptAnnotations().get(1).startOffset());
        assertEquals(5, result.transcriptAnnotations().get(1).endOffset());
    }

    @Test
    void mismatchedSubcriterionParentIsDropped() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode annotation =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("transcript_annotations").get(0);
        annotation.put("criterion_id", "S_CONTENT_TASK_FULFILLMENT");
        annotation.put("sub_criterion_id", "S_GRAMMAR_PARTICLES");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.transcriptAnnotations().isEmpty());
    }

    @Test
    void speakingCriterionIdsSerializeWithSpeakingNamespace() throws Exception {
        String json = objectMapper.writeValueAsString(SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT);

        assertEquals("\"S_CONTENT_TASK_FULFILLMENT\"", json);
        assertEquals(SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                SpeakingRubricCriterion.fromExternalId("S_CONTENT_TASK_FULFILLMENT"));
        assertEquals(SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                SpeakingRubricCriterion.fromExternalId("CONTENT_TASK_FULFILLMENT"));
    }

    @Test
    void impossibleOverallAndRubricCombinationCannotCreateHolisticScore() throws Exception {
        JsonNode input = (JsonNode) validInput().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("overall_score", 99);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
    }

    @Test
    void fourTranscriptGroundedRowsRetainPartialLanguageProfile() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input =
                (com.fasterxml.jackson.databind.node.ObjectNode) validInput().deepCopy();
        com.fasterxml.jackson.databind.node.ArrayNode rows =
                (com.fasterxml.jackson.databind.node.ArrayNode) input.path("rubric_scores");
        rows.remove(rows.size() - 1);
        rows.remove(rows.size() - 1);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.evaluationStatus());
        assertTrue(result.profileAvailable());
        assertFalse(result.scoreAvailable());
        assertNull(result.overallScore());
        assertEquals(4, result.rubricScores().stream().filter(SpeakingEvaluationResult.RubricScore::scored).count());
        assertEquals(2, result.rubricScores().stream()
                .filter(row -> row.availability() == SpeakingCriterionAvailability.NOT_SCORABLE).count());
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

    @Test
    void missingOrMismatchedPolicyBundleCannotBecomeCurrent() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode missing =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        validInput().deepCopy();
        missing.remove("policy_bundle_id");
        com.fasterxml.jackson.databind.node.ObjectNode mismatched =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        validInput().deepCopy();
        mismatched.put("policy_bundle_id", "STALE_BUNDLE");
        com.fasterxml.jackson.databind.node.ObjectNode missingFingerprint =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        validInput().deepCopy();
        missingFingerprint.remove("policy_bundle_fingerprint");
        com.fasterxml.jackson.databind.node.ObjectNode mismatchedFingerprint =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        validInput().deepCopy();
        mismatchedFingerprint.put(
                "policy_bundle_fingerprint",
                "0".repeat(64));

        for (JsonNode input : java.util.List.of(
                missing,
                mismatched,
                missingFingerprint,
                mismatchedFingerprint)) {
            SpeakingEvaluationResult result = normalizer.normalize(input);
            assertThat(result.currentEvidenceContract()).isFalse();
            assertThat(result.profileAvailable()).isFalse();
            assertThat(result.contractTrust()).isEqualTo(
                    SpeakingContractTrust.LEGACY_UNVERIFIED);
        }
    }

    private BigDecimal score(SpeakingEvaluationResult result, SpeakingRubricCriterion criterion) {
        return result.rubricScores().stream()
                .filter(row -> row.criterion() == criterion)
                .findFirst()
                .orElseThrow()
                .score();
    }

    private SpeakingCriterionAvailability availability(
            SpeakingEvaluationResult result,
            SpeakingRubricCriterion criterion
    ) {
        return result.rubricScores().stream()
                .filter(row -> row.criterion() == criterion)
                .findFirst()
                .orElseThrow()
                .availability();
    }

    private JsonNode validInput() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree("""
                {
                  "evaluation_status":"EVALUATED",
                  "source":"PROVIDER",
                  "model":"fake-evaluator",
                  "transcription_model":"fake-transcriber",
                  "prompt_version":"speaking-eval-v5-policy-bundle-vi-ko-transcript-language-only",
                  "rubric_version":"speaking-rubric-v2-transcript-language-profile",
                  "schema_version":"speaking-schema-v3-policy-bundle-partial-language-profile",
                  "policy_bundle_id":"KSH_SPEAKING_POLICY_BUNDLE_V1",
                  "audio_media_id":44,
                  "media_version":3,
                  "transcript":"저는 학교 갔어요",
                  "normalized_transcript":"저는 학교에 갔어요.",
                  "actually_heard_transcript":"저는 학교... 갔어요",
                  "interpreted_intent":"Học viên muốn nói rằng mình đã đến trường.",
                  "intent_confidence":0.8,
                  "transcript_confidence":0.9,
                  "listener_burden":"LOW",
                  "overall_score":78,
                  "level_label":"Mức luyện tập nội bộ KSH",
                  "rubric_scores":[
                    {"criterion":"CONTENT_TASK_FULFILLMENT","score":17,"feedback":"Đúng trọng tâm"},
                    {"criterion":"GRAMMAR_SENTENCE_CONTROL","score":16,"feedback":"Kiểm soát khá tốt"},
                    {"criterion":"VOCABULARY_EXPRESSIONS","score":12,"feedback":"Đủ dùng"},
                    {"criterion":"COHERENCE_ORGANIZATION","score":12,"feedback":"Rõ ràng"},
                    {"criterion":"FLUENCY","score":11,"feedback":"Có một số chỗ ngập ngừng"},
                    {"criterion":"PRONUNCIATION_DELIVERY","score":10,"feedback":"Chỉ tham khảo"}
                  ],
                  "evidence":[
                    {"source":"TRANSCRIPT","criterion":"GRAMMAR_SENTENCE_CONTROL","excerpt":"학교... 갔어요","confidence":0.9},
                    {"source":"AUDIO_METADATA","criterion":"FLUENCY","excerpt":"pause metadata","confidence":0.8},
                    {"source":"PROMPT","criterion":"CONTENT_TASK_FULFILLMENT","excerpt":"task requirement","confidence":1},
                    {"source":"INTERPRETED_INTENT","criterion":"CONTENT_TASK_FULFILLMENT","excerpt":"intended meaning","confidence":0.8}
                  ],
                  "findings":[{"category":"UNKNOWN_SAFE_CATEGORY","message":"Nhận xét an toàn"}],
                  "recommendations":["Tiếp tục luyện tập"],
                  "upgraded_answer":"저는 학교에 갔어요.",
                  "sample_answer":"어제 학교에 갔어요.",
                  "pronunciation_advisory":["Có thể có vấn đề về 받침"],
                  "fluency_observations":["Có một khoảng dừng dài"],
                  "retryable":false
                }
                """);
        input.put(
                "policy_bundle_fingerprint",
                SpeakingAssessmentPolicyBundle.fingerprint());
        return input;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode richInput() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input =
                (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson());
        input.put("prompt_version", SpeakingPromptRules.PROMPT_VERSION);
        input.put("rubric_version", SpeakingPromptRules.RUBRIC_VERSION);
        input.put("schema_version", SpeakingPromptRules.SCHEMA_VERSION);
        input.put("policy_bundle_id",
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        input.put("policy_bundle_fingerprint",
                SpeakingAssessmentPolicyBundle.fingerprint());
        return input;
    }
}
