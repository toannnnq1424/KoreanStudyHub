package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        assertEquals(1, result.majorStrengths().size());
        assertEquals(1, result.majorNeedsImprovement().size());
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
        assertEquals(2, result.transcriptAnnotations().size());
        assertEquals("needs_improvement", result.transcriptAnnotations().get(0).annotationType());
        assertEquals(SpeakingEvidenceSource.TRANSCRIPT, result.transcriptAnnotations().get(0).evidenceSource());
        assertEquals("TEXT_SPAN", result.transcriptAnnotations().get(0).evidenceScope());
        assertEquals("학생 이에요", result.transcriptAnnotations().get(0).evidence());
        assertEquals("학생이에요", result.transcriptAnnotations().get(0).suggestionKo());
        assertEquals(3, result.transcriptAnnotations().get(0).startOffset());
        assertEquals(9, result.transcriptAnnotations().get(0).endOffset());
    }

    @Test
    void nonexistentTranscriptSpanFailsClosedInsteadOfBeingGuessed() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode evidence =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("evidence").get(0);
        evidence.put("exact_text", "존재하지 않는 구절");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertTrue(result.transcriptAnnotations().isEmpty());
        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
        assertEquals(SpeakingContractTrust.CURRENT_VERIFIED,
                result.contractTrust());
    }

    @Test
    void genericTemplateFeedbackFailsClosed() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode finding =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        input.path("transcript_annotations").get(0);
        finding.put(
                "explanation_vi",
                "Cần điều chỉnh tiểu từ trong bản chép lời.");
        finding.put(
                "suggestion_ko",
                "표현을 더 정확하고 자연스럽게 고쳐 보세요.");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(
                SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
        assertTrue(result.transcriptAnnotations().isEmpty());
    }

    @Test
    void repetitionFindingCannotBeInferredFromOneOccurrenceOrReplaceOperation()
            throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode evidence =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        input.path("evidence").get(2);
        evidence.put(
                "sub_criterion_id",
                "S_VOCAB_REPETITION_CONTROL");
        com.fasterxml.jackson.databind.node.ObjectNode finding =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        input.path("transcript_annotations").get(0);
        finding.put("evidence_id", "SEV-VOCAB-1");
        finding.put("criterion_id", "S_VOCABULARY_EXPRESSIONS");
        finding.put(
                "sub_criterion_id",
                "S_VOCAB_REPETITION_CONTROL");
        finding.put("operation", "REPLACE");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(
                SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
        assertTrue(result.transcriptAnnotations().isEmpty());
    }

    @Test
    void providerOffsetsAreAuthoritativeAndMismatchFailsClosed() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode evidence =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("evidence").get(1);
        evidence.put("start_offset", 999);
        evidence.put("end_offset", 1005);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
        assertTrue(result.transcriptAnnotations().isEmpty());
    }

    @Test
    void wholeAnswerProviderScopeCannotEnterAtomicTranscriptLedger() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        ((com.fasterxml.jackson.databind.node.ObjectNode) input.path("evidence").get(0))
                .put("evidence_scope", "WHOLE_ANSWER");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
    }

    @Test
    void strengthWithProviderCorrectionFailsClosed() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                input.path("transcript_annotations").get(1))
                .put("suggestion_ko", "수정하면 안 됩니다");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
    }

    @Test
    void taskMetadataWithoutAuthoritativeEnvelopeFailsClosed() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        ((com.fasterxml.jackson.databind.node.ObjectNode) input.path("evidence").get(0))
                .put("source", "PROMPT");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
    }

    @Test
    void repeatedExactSpanRequiresExplicitDistinctOccurrenceIdentity() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        input.put("actually_heard_transcript", "저는 학생 이에요. 다시 다시");
        withAuthoritativeLedger(input);
        String transcript = input.path("actually_heard_transcript").asText();
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        transcript.getBytes(StandardCharsets.UTF_8)));
        com.fasterxml.jackson.databind.node.ArrayNode ledger =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        input.path("evidence");
        addEvidence(ledger, "SEV-REPEAT-1",
                "S_GRAMMAR_SENTENCE_CONTROL", "S_GRAMMAR_CONNECTORS",
                transcript, 11, 13, hash);
        addEvidence(ledger, "SEV-REPEAT-2",
                "S_GRAMMAR_SENTENCE_CONTROL", "S_GRAMMAR_CONNECTORS",
                transcript, 14, 16, hash);
        com.fasterxml.jackson.databind.node.ArrayNode annotations =
                (com.fasterxml.jackson.databind.node.ArrayNode) input.path("transcript_annotations");
        com.fasterxml.jackson.databind.node.ObjectNode first =
                (com.fasterxml.jackson.databind.node.ObjectNode) annotations.get(0).deepCopy();
        first.put("finding_id", "SF-REPEAT-1");
        first.put("evidence_id", "SEV-REPEAT-1");
        first.put("sub_criterion_id", "S_GRAMMAR_CONNECTORS");
        com.fasterxml.jackson.databind.node.ObjectNode second = first.deepCopy();
        second.put("finding_id", "SF-REPEAT-2");
        second.put("evidence_id", "SEV-REPEAT-2");
        annotations.removeAll();
        annotations.add(first);
        annotations.add(second);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(2, result.transcriptAnnotations().size());
        assertEquals(11, result.transcriptAnnotations().get(0).startOffset());
        assertEquals(1, result.transcriptAnnotations().get(0).occurrenceIndex());
        assertEquals(2, result.transcriptAnnotations().get(0).occurrenceCount());
        assertEquals(14, result.transcriptAnnotations().get(1).startOffset());
        assertEquals(2, result.transcriptAnnotations().get(1).occurrenceIndex());
        assertEquals(2, result.transcriptAnnotations().get(1).occurrenceCount());
    }

    @Test
    void mismatchedSubcriterionParentFailsClosed() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode annotation =
                (com.fasterxml.jackson.databind.node.ObjectNode) input.path("transcript_annotations").get(0);
        annotation.put("criterion_id", "S_CONTENT_TASK_FULFILLMENT");
        annotation.put("sub_criterion_id", "S_GRAMMAR_PARTICLES");

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
    }

    @Test
    void maximumCriterionScoreWithConfirmedImprovementFailsClosed()
            throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        com.fasterxml.jackson.databind.node.ObjectNode grammar =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        input.path("rubric_scores").get(1);
        grammar.put("score", 20);
        grammar.put("max_score", 20);

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
        assertEquals("SPEAKING_SCORE_EVIDENCE_CONTRADICTION",
                result.errorCategory());
    }

    @Test
    void mismatchedTranscriptSourceHashFailsClosed() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode input = richInput();
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                input.path("evidence").get(0))
                .put("source_hash", "0".repeat(64));

        SpeakingEvaluationResult result = normalizer.normalize(input);

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                result.evaluationStatus());
        assertTrue(result.evidence().isEmpty());
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
                  "prompt_version":"speaking-eval-v6-authoritative-transcript-ledger",
                  "rubric_version":"speaking-rubric-v2-transcript-language-profile",
                  "schema_version":"speaking-schema-v4-authoritative-utf16-ledger",
                  "policy_bundle_id":"KSH_SPEAKING_POLICY_BUNDLE_V2",
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
        input.put("prompt_version", SpeakingPromptRules.PROMPT_VERSION);
        input.put(
                "policy_bundle_fingerprint",
                SpeakingAssessmentPolicyBundle.fingerprint());
        return withAuthoritativeLedger(input);
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
        return withAuthoritativeLedger(input);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode withAuthoritativeLedger(
            com.fasterxml.jackson.databind.node.ObjectNode input
    ) throws Exception {
        String transcript = input.path("actually_heard_transcript").asText();
        String sourceHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        transcript.getBytes(StandardCharsets.UTF_8)));
        com.fasterxml.jackson.databind.node.ArrayNode ledger =
                objectMapper.createArrayNode();
        addEvidence(ledger, "SEV-CONTENT-1", "S_CONTENT_TASK_FULFILLMENT",
                "S_CONTENT_RELEVANCE", transcript, 0, 2, sourceHash);
        String grammarText = transcript.contains("학생 이에요")
                ? "학생 이에요" : "학교";
        int grammarStart = transcript.contains("학생 이에요") ? 3 : 3;
        addEvidence(ledger, "SEV-GRAMMAR-1", "S_GRAMMAR_SENTENCE_CONTROL",
                "S_GRAMMAR_PARTICLES", transcript, grammarStart,
                grammarStart + grammarText.length(), sourceHash);
        String vocabText = transcript.contains("학생") ? "학생" : "학교";
        int vocabStart = transcript.contains("학생") ? 3 : 3;
        addEvidence(ledger, "SEV-VOCAB-1", "S_VOCABULARY_EXPRESSIONS",
                "S_VOCAB_TOPIC_WORDS", transcript, vocabStart,
                vocabStart + vocabText.length(), sourceHash);
        String coherenceText = transcript.contains("갔어요")
                ? "갔어요" : "저는 학생";
        int coherenceStart = transcript.lastIndexOf(coherenceText);
        addEvidence(ledger, "SEV-COHERENCE-1",
                "S_COHERENCE_ORGANIZATION",
                "S_COHERENCE_LOGICAL_FLOW", transcript, coherenceStart,
                coherenceStart + coherenceText.length(), sourceHash);
        input.set("evidence", ledger);

        java.util.Map<String, String> criterionEvidence = java.util.Map.of(
                "S_CONTENT_TASK_FULFILLMENT", "SEV-CONTENT-1",
                "CONTENT_TASK_FULFILLMENT", "SEV-CONTENT-1",
                "S_GRAMMAR_SENTENCE_CONTROL", "SEV-GRAMMAR-1",
                "GRAMMAR_SENTENCE_CONTROL", "SEV-GRAMMAR-1",
                "S_VOCABULARY_EXPRESSIONS", "SEV-VOCAB-1",
                "VOCABULARY_EXPRESSIONS", "SEV-VOCAB-1",
                "S_COHERENCE_ORGANIZATION", "SEV-COHERENCE-1",
                "COHERENCE_ORGANIZATION", "SEV-COHERENCE-1");
        input.withArray("rubric_scores").forEach(row -> {
            String criterion = row.path("criterion").asText();
            String evidenceId = criterionEvidence.get(criterion);
            if (evidenceId != null) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) row)
                        .putArray("evidence_ids").add(evidenceId);
            }
        });

        com.fasterxml.jackson.databind.node.ArrayNode findings =
                objectMapper.createArrayNode();
        findings.addObject()
                .put("finding_id", "SF-GRAMMAR-1")
                .put("evidence_id", "SEV-GRAMMAR-1")
                .put("criterion_id", "S_GRAMMAR_SENTENCE_CONTROL")
                .put("sub_criterion_id", "S_GRAMMAR_PARTICLES")
                .put("evidence_source", "TRANSCRIPT")
                .put("annotation_type", "needs_improvement")
                .put("operation", "REPLACE")
                .put("category", "GRAMMAR")
                .put("severity", "LOW")
                .put("confidence", 0.8)
                .put("explanation_vi", "Cần chỉnh cách nói tự nhiên hơn.")
                .put("suggestion_ko", transcript.contains("학생 이에요")
                        ? "학생이에요" : "학교에");
        findings.addObject()
                .put("finding_id", "SF-CONTENT-1")
                .put("evidence_id", "SEV-CONTENT-1")
                .put("criterion_id", "S_CONTENT_TASK_FULFILLMENT")
                .put("sub_criterion_id", "S_CONTENT_RELEVANCE")
                .put("evidence_source", "TRANSCRIPT")
                .put("annotation_type", "strength")
                .put("operation", "KEEP")
                .put("category", "CONTENT")
                .put("severity", "LOW")
                .put("confidence", 0.9)
                .put("explanation_vi", "Câu trả lời bám đúng chủ đề.")
                .put("suggestion_ko", "");
        input.set("transcript_annotations", findings);
        return input;
    }

    private void addEvidence(
            com.fasterxml.jackson.databind.node.ArrayNode ledger,
            String evidenceId,
            String criterionId,
            String subCriterionId,
            String transcript,
            int start,
            int end,
            String sourceHash
    ) {
        String exact = transcript.substring(start, end);
        int occurrenceCount = 0;
        int occurrenceIndex = 0;
        for (int cursor = 0;
             cursor + exact.length() <= transcript.length();
             cursor++) {
            if (transcript.regionMatches(
                    cursor, exact, 0, exact.length())) {
                occurrenceCount++;
                if (cursor == start) {
                    occurrenceIndex = occurrenceCount;
                }
            }
        }
        ledger.addObject()
                .put("evidence_id", evidenceId)
                .put("source", "TRANSCRIPT")
                .put("criterion_id", criterionId)
                .put("sub_criterion_id", subCriterionId)
                .put("evidence_scope", "TEXT_SPAN")
                .put("exact_text", exact)
                .put("start_offset", start)
                .put("end_offset", end)
                .put("occurrence_index", occurrenceIndex)
                .put("occurrence_count", occurrenceCount)
                .put("normalization", "UTF16_EXACT_V1")
                .put("source_hash", sourceHash)
                .put("confidence", 0.9);
    }
}
