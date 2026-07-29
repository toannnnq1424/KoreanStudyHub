package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WritingEvaluationNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingEvaluationNormalizer normalizer =
            new WritingEvaluationNormalizer(objectMapper);

    @Test
    void normalizesTaskNativeScoreAndStrictKoreanDiagnostics() throws Exception {
        String answer = "한국어를 공부합니다. 글의 흐름이 자연스럽습니다.";
        ObjectNode provider = providerPayload("Q53");
        provider.put("sample_answer", "평가기 생성 모범 답안");
        provider.withArray("strengths").add(finding(
                "W_LOGICAL_ORGANIZATION",
                "LOGICAL_RELATION",
                "W_ORGANIZATION_COHERENCE",
                "WHOLE_ANSWER",
                "",
                "Bố cục và quan hệ ý rõ ràng.",
                "",
                "MODERATE",
                1,
                0.92,
                "INFERRED_BOUNDED"));
        provider.withArray("needs_improvement").add(finding(
                "W_PARTICLE_ERRORS",
                "MORPHOLOGY_PARTICLES",
                "W_LANGUAGE_EXPRESSION",
                "TEXT_SPAN",
                "한국어를",
                "Cần kiểm tra tiểu từ theo ngữ cảnh.",
                "한국어를",
                "MINOR",
                1,
                0.98,
                "DIRECT"));

        JsonNode root = normalize(provider, "Q53", answer);

        assertThat(root.path("evaluation_status").asText())
                .isEqualTo("EVALUATED");
        assertThat(root.path("score_available").asBoolean()).isTrue();
        assertThat(root.path("raw_score_max").asDouble()).isEqualTo(30.0);
        assertThat(root.path("scoring_contract").asText())
                .isEqualTo(WritingScoringPolicy.SCORING_CONTRACT);
        assertThat(root.path("policy_bundle_id").asText())
                .isEqualTo(WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(root.path("sample_answer").asText()).isEmpty();
        JsonNode finding = root.path("needs_improvement").get(0);
        assertThat(finding.path("subtype").asText())
                .isEqualTo("MORPHOLOGY_PARTICLES");
        assertThat(finding.path("scoringCriterionId").asText())
                .isEqualTo("W_LANGUAGE_EXPRESSION");
        assertThat(finding.path("impact").asText()).isEqualTo("MINOR");
        assertThat(finding.path("frequency").asInt()).isEqualTo(1);
        assertThat(finding.path("confidence").decimalValue())
                .isEqualByComparingTo("0.98");
        assertThat(finding.path("observability").asText())
                .isEqualTo("DIRECT");
    }

    @Test
    void rejectsUnknownSubtypeCrossParentAndUnverifiableEvidence() throws Exception {
        ObjectNode provider = providerPayload("Q53");
        ArrayNode needs = provider.withArray("needs_improvement");
        needs.add(finding(
                "W_PARTICLE_ERRORS", "UNKNOWN_SUBTYPE",
                "W_LANGUAGE_EXPRESSION", "TEXT_SPAN", "한국어를",
                "Sai subtype.", "한국어를", "MINOR", 1, 0.9, "DIRECT"));
        needs.add(finding(
                "W_PARTICLE_ERRORS", "MORPHOLOGY_PARTICLES",
                "W_CONTENT_TASK_ACHIEVEMENT", "TEXT_SPAN", "한국어를",
                "Sai parent.", "한국어를", "MINOR", 1, 0.9, "DIRECT"));
        needs.add(finding(
                "W_PARTICLE_ERRORS", "MORPHOLOGY_PARTICLES",
                "W_LANGUAGE_EXPRESSION", "TEXT_SPAN", "không tồn tại",
                "Sai evidence.", "수정", "MINOR", 1, 0.9, "DIRECT"));
        needs.add(finding(
                "W_PARTICLE_ERRORS", "MORPHOLOGY_PARTICLES",
                "W_LANGUAGE_EXPRESSION", "TEXT_SPAN", "한국어를",
                "Có bằng chứng.", "한국어를", "MINOR", 2, 0.9, "DIRECT"));

        JsonNode root = normalize(provider, "Q53", "한국어를 공부합니다.");

        assertThat(root.path("needs_improvement")).hasSize(1);
        assertThat(root.path("needs_improvement").get(0)
                .path("frequency").asInt()).isEqualTo(2);
    }

    @Test
    void q51FindingRequiresExplicitNullParentUntilBlankIdentityExists()
            throws Exception {
        ObjectNode provider = providerPayload("Q51");
        ObjectNode valid = finding(
                "W_CLOZE_CONTEXT_FIT", "REQUIREMENT_COVERAGE",
                null, "TEXT_SPAN", "있다",
                "Phù hợp ngữ cảnh chỗ trống.", "",
                "MODERATE", 1, 0.95, "DIRECT");
        provider.withArray("strengths").add(valid);

        JsonNode accepted = normalize(provider, "Q51", "있다");
        assertThat(accepted.path("strengths")).hasSize(1);
        assertThat(accepted.path("strengths").get(0)
                .path("scoringCriterionId").isNull()).isTrue();

        valid.put("scoringCriterionId", "W_CLOZE_BLANK_1_CONTEXT");
        JsonNode rejected = normalize(provider, "Q51", "있다");
        assertThat(rejected.path("strengths")).isEmpty();
    }

    @Test
    void legacyNameOnlyRubricIsRejectedWithoutNumericFallback() throws Exception {
        String legacy = """
                {
                  "summary":"legacy",
                  "rubric_scores":[
                    {"name":"Nội dung","score":7,"feedback":""},
                    {"name":"Cấu trúc","score":7,"feedback":""},
                    {"name":"Ngôn ngữ","score":7,"feedback":""}
                  ],
                  "strengths":[],
                  "needs_improvement":[]
                }
                """;

        JsonNode root = objectMapper.readTree(
                normalizer.normalize(legacy, "Q53", "한국어 답안", null));

        assertThat(root.path("evaluation_status").asText())
                .isEqualTo("EVALUATION_CONTRACT_FAILED");
        assertThat(root.path("score_available").asBoolean()).isFalse();
        assertThat(root.has("score")).isFalse();
        assertThat(root.has("raw_score")).isFalse();
    }

    @Test
    void deterministicInvalidAnswerUsesCurrentTaskNativeZeroWithoutBand()
            throws Exception {
        JsonNode root = objectMapper.readTree(
                normalizer.spamResponse("Q54", ""));

        assertThat(root.path("evaluation_status").asText())
                .isEqualTo("INVALID_LEARNER_RESPONSE");
        assertThat(root.path("evaluation_source").asText())
                .isEqualTo("BACKEND_RULE");
        assertThat(root.path("raw_score").asDouble()).isZero();
        assertThat(root.path("raw_score_max").asDouble()).isEqualTo(50.0);
        assertThat(root.path("policy_bundle_id").asText())
                .isEqualTo(WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(root.has("band_label")).isFalse();
    }

    @Test
    void malformedUnavailableAndExplicitFallbackRemainNonScoreBearing()
            throws Exception {
        JsonNode malformed = objectMapper.readTree(normalizer.normalize(
                "{malformed", "Q54", "한국어", null));
        JsonNode unavailable = objectMapper.readTree(
                normalizer.providerUnavailable(
                        "MISSING_API_KEY", "Q54", "한국어", false));
        JsonNode fallback = objectMapper.readTree(
                normalizer.fallback("Vui lòng chấm lại.", "Q54"));

        for (JsonNode root : List.of(malformed, unavailable, fallback)) {
            assertThat(root.path("score_available").asBoolean()).isFalse();
            assertThat(root.has("score")).isFalse();
            assertThat(root.has("raw_score")).isFalse();
            assertThat(root.path("policy_bundle_id").asText())
                    .isEqualTo(
                            WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        }
    }

    @Test
    void cacheReuseRequiresExactCurrentBundleAndRevalidatesTextEvidence()
            throws Exception {
        ObjectNode provider = providerPayload("Q53");
        provider.withArray("strengths").add(finding(
                "W_ADVANCED_GRAMMAR_STRUCTURES",
                "ENDINGS_CONJUGATION",
                "W_LANGUAGE_EXPRESSION",
                "TEXT_SPAN",
                "공부합니다",
                "Đuôi câu văn viết chính xác.",
                "",
                "MODERATE",
                1,
                0.94,
                "DIRECT"));
        String normalized = normalizer.normalize(
                objectMapper.writeValueAsString(provider),
                "Q53",
                "한국어를 공부합니다",
                null);
        String cached = normalizer.sanitizeForCache(normalized);

        JsonNode hydrated = objectMapper.readTree(
                normalizer.rehydrateCachedResult(
                        cached, "한국어를 공부합니다", "Q53"));
        assertThat(hydrated.path("evaluation_source").asText())
                .isEqualTo("CACHE");
        assertThat(hydrated.path("strengths")).hasSize(1);

        ObjectNode wrongBundle = (ObjectNode) objectMapper.readTree(cached);
        wrongBundle.put("policy_bundle_id", "STALE_BUNDLE");
        assertThatThrownBy(() -> normalizer.rehydrateCachedResult(
                objectMapper.writeValueAsString(wrongBundle),
                "한국어를 공부합니다",
                "Q53"))
                .isInstanceOf(IllegalArgumentException.class);

        JsonNode differentAnswer = objectMapper.readTree(
                normalizer.rehydrateCachedResult(
                        cached, "다른 답안입니다", "Q53"));
        assertThat(differentAnswer.path("strengths")).isEmpty();
    }

    @Test
    void scoreDoesNotDependOnDiagnosticCount() throws Exception {
        ObjectNode withoutFindings = providerPayload("Q53");
        ObjectNode withFinding = withoutFindings.deepCopy();
        withFinding.withArray("strengths").add(finding(
                "W_LOGICAL_ORGANIZATION", "COHESION",
                "W_ORGANIZATION_COHERENCE", "WHOLE_ANSWER", "",
                "Mạch lạc.", "", "MODERATE", 1, 0.9,
                "INFERRED_BOUNDED"));

        JsonNode first = normalize(
                withoutFindings, "Q53", "한국어를 공부합니다.");
        JsonNode second = normalize(
                withFinding, "Q53", "한국어를 공부합니다.");

        assertThat(second.path("score").decimalValue())
                .isEqualByComparingTo(first.path("score").decimalValue());
    }

    @Test
    void deriveScoreUsesTaskNativeMaximumAndEmptyIsZero() {
        List<Map<String, Object>> rubrics = List.of(
                Map.of("score", 7.0, "maxScore", 12.0),
                Map.of("score", 8.0, "maxScore", 9.0),
                Map.of("score", 6.0, "maxScore", 9.0));

        assertThat(WritingEvaluationNormalizer.deriveScoreFromRubrics(rubrics))
                .isEqualTo(70.0);
        assertThat(WritingEvaluationNormalizer.deriveScoreFromRubrics(List.of()))
                .isZero();
    }

    private JsonNode normalize(
            ObjectNode provider,
            String taskType,
            String answer
    ) throws Exception {
        return objectMapper.readTree(normalizer.normalize(
                objectMapper.writeValueAsString(provider),
                taskType,
                answer,
                null));
    }

    private ObjectNode providerPayload(String taskType) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("summary", "Đánh giá có bằng chứng.");
        ArrayNode scores = root.putArray("rubric_scores");
        for (WritingScoringCriterion criterion :
                WritingScoringPolicy.rubricFor(taskType).criteria()) {
            ObjectNode row = scores.addObject();
            row.put("criterionId", criterion.criterionId());
            row.put("name", criterion.displayName());
            row.put("score", criterion.maxScore() * 0.8);
            row.put("maxScore", criterion.maxScore());
            row.put("feedback", "Nhận xét theo tiêu chí.");
        }
        root.putArray("strengths");
        root.putArray("needs_improvement");
        root.put("upgraded_answer", "");
        root.put("upgraded_answer_annotated", "");
        root.putArray("sentence_rewrites");
        return root;
    }

    private ObjectNode finding(
            String criterionId,
            String subtype,
            String scoringCriterionId,
            String evidenceScope,
            String evidence,
            String explanationVi,
            String correction,
            String impact,
            int frequency,
            double confidence,
            String observability
    ) {
        ObjectNode finding = objectMapper.createObjectNode();
        finding.put("criterionId", criterionId);
        finding.put("subtype", subtype);
        if (scoringCriterionId == null) {
            finding.putNull("scoringCriterionId");
        } else {
            finding.put("scoringCriterionId", scoringCriterionId);
        }
        finding.put("evidenceScope", evidenceScope);
        finding.put("evidence", evidence);
        finding.put("explanationVi", explanationVi);
        finding.put("correction", correction);
        finding.put("impact", impact);
        finding.put("frequency", frequency);
        finding.put("confidence", confidence);
        finding.put("observability", observability);
        return finding;
    }
}
