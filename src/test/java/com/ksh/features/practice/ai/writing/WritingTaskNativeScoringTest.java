package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WritingTaskNativeScoringTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingEvaluationNormalizer normalizer = new WritingEvaluationNormalizer(objectMapper);

    @Test
    void q53UsesEarnedScoreThirtyPointMaximumAndExplicitPercentage() throws Exception {
        JsonNode result = normalize("Q53", """
                [
                  {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","name":"ignored","score":12,"maxScore":12,"feedback":"A"},
                  {"criterionId":"W_ORGANIZATION_COHERENCE","name":"ignored","score":9,"maxScore":9,"feedback":"B"},
                  {"criterionId":"W_LANGUAGE_EXPRESSION","name":"ignored","score":1,"maxScore":9,"feedback":"C"}
                ]
                """);

        assertThat(result.path("raw_score").asDouble()).isEqualTo(22.0);
        assertThat(result.path("raw_score_max").asDouble()).isEqualTo(30.0);
        assertThat(result.path("percentage").asDouble()).isEqualTo(73.33);
        assertThat(result.path("score").asDouble()).isEqualTo(73.33);
        assertThat(result.path("scoring_contract").asText()).isEqualTo("TASK_NATIVE_RUBRIC_V1");
    }

    @Test
    void q54UsesFiftyPointMaximum() throws Exception {
        JsonNode result = normalize("Q54", """
                [
                  {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","name":"ignored","score":20,"maxScore":20,"feedback":"A"},
                  {"criterionId":"W_ORGANIZATION_COHERENCE","name":"ignored","score":15,"maxScore":15,"feedback":"B"},
                  {"criterionId":"W_LANGUAGE_EXPRESSION","name":"ignored","score":3,"maxScore":15,"feedback":"C"}
                ]
                """);

        assertThat(result.path("raw_score").asDouble()).isEqualTo(38.0);
        assertThat(result.path("raw_score_max").asDouble()).isEqualTo(50.0);
        assertThat(result.path("percentage").asDouble()).isEqualTo(76.0);

        JsonNode fullScore = normalize("Q54", """
                [
                  {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","name":"ignored","score":20,"maxScore":20,"feedback":"A"},
                  {"criterionId":"W_ORGANIZATION_COHERENCE","name":"ignored","score":15,"maxScore":15,"feedback":"B"},
                  {"criterionId":"W_LANGUAGE_EXPRESSION","name":"ignored","score":15,"maxScore":15,"feedback":"C"}
                ]
                """);
        assertThat(fullScore.path("evaluation_status").asText())
                .isEqualTo("EVALUATED");
        assertThat(fullScore.path("raw_score").asInt()).isEqualTo(50);
    }

    @Test
    void q51RequiresBothBlankRubricsAndUsesTenPointMaximum() throws Exception {
        JsonNode result = normalize("Q51", """
                [
                  {"criterionId":"W_CLOZE_BLANK_1_CONTEXT","name":"ignored","score":2,"maxScore":2,"feedback":"A"},
                  {"criterionId":"W_CLOZE_BLANK_1_GRAMMAR","name":"ignored","score":2,"maxScore":2,"feedback":"B"},
                  {"criterionId":"W_CLOZE_BLANK_1_EXPRESSION","name":"ignored","score":1,"maxScore":1,"feedback":"C"},
                  {"criterionId":"W_CLOZE_BLANK_2_CONTEXT","name":"ignored","score":2,"maxScore":2,"feedback":"D"},
                  {"criterionId":"W_CLOZE_BLANK_2_GRAMMAR","name":"ignored","score":0,"maxScore":2,"feedback":"E"},
                  {"criterionId":"W_CLOZE_BLANK_2_EXPRESSION","name":"ignored","score":0,"maxScore":1,"feedback":"F"}
                ]
                """);

        assertThat(result.path("rubric_scores")).hasSize(6);
        assertThat(result.path("raw_score").asDouble()).isEqualTo(7.0);
        assertThat(result.path("raw_score_max").asDouble()).isEqualTo(10.0);
        assertThat(result.path("percentage").asDouble()).isEqualTo(70.0);
    }

    @Test
    void invalidTaskNativeCriterionFailsContractWithoutScore() throws Exception {
        JsonNode result = normalize("Q53", """
                [
                  {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","name":"ignored","score":9,"maxScore":40,"feedback":"wrong max"}
                ]
                """);

        assertThat(result.path("evaluation_status").asText()).isEqualTo("EVALUATION_CONTRACT_FAILED");
        assertThat(result.path("score_available").asBoolean()).isFalse();
        assertThat(result.has("raw_score")).isFalse();

        String learnerAnswer =
                WritingContractTestFixtures.scoreBearingLearnerAnswer(
                        "Q53", 9);
        var unjustifiedPartial =
                WritingContractTestFixtures.zeroEnvelope(
                        objectMapper, "Q53", learnerAnswer);
        WritingContractTestFixtures.addEvidence(
                unjustifiedPartial,
                "EV_SCORE",
                learnerAnswer,
                learnerAnswer.substring(0, 1),
                0);
        var content = WritingContractTestFixtures.rubric(
                unjustifiedPartial,
                "W_CONTENT_TASK_ACHIEVEMENT");
        content.put("score", 9);
        WritingContractTestFixtures.replaceIds(
                content, "evidenceIds", "EV_SCORE");
        for (JsonNode coverage
                : unjustifiedPartial.withArray("taskCoverage")) {
            var row = (com.fasterxml.jackson.databind.node.ObjectNode)
                    coverage;
            row.put("status", "MET");
            if (!row.path("requirementId").asText()
                    .contains("_LENGTH_")) {
                WritingContractTestFixtures.replaceIds(
                        row, "evidenceIds", "EV_SCORE");
            }
        }
        JsonNode rejectedPartial = objectMapper.readTree(
                normalizer.normalize(
                        objectMapper.writeValueAsString(
                                unjustifiedPartial),
                        "Q53",
                        learnerAnswer,
                        null));
        assertThat(rejectedPartial.path("evaluation_status").asText())
                .isEqualTo("EVALUATION_CONTRACT_FAILED");
        assertThat(rejectedPartial.path("score_available").asBoolean())
                .isFalse();
    }

    @Test
    void legacyBandProjectionIsRejectedWithoutCreatingACompatibleScore() throws Exception {
        String legacy = """
                {
                  "summary":"OK",
                  "rubric_scores":[
                    {"name":"Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)","score":7,"feedback":"A"},
                    {"name":"Cấu trúc & Bố cục đoạn văn (글의 전개 구조)","score":7,"feedback":"B"},
                    {"name":"Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)","score":7,"feedback":"C"}
                  ],
                  "strengths":[],
                  "needs_improvement":[]
                }
                """;

        JsonNode result = objectMapper.readTree(normalizer.normalize(legacy, "Q53", "한국어 답안", null));

        assertThat(result.path("evaluation_status").asText())
                .isEqualTo("EVALUATION_CONTRACT_FAILED");
        assertThat(result.path("score_available").asBoolean()).isFalse();
        assertThat(result.has("score")).isFalse();
        assertThat(result.has("raw_score")).isFalse();
    }

    @Test
    void currentScoreProvenanceAcceptsOnlyNormalizerOwnedPairs() {
        assertThat(currentScoreProvenance(
                "EVALUATED", "PROVIDER", "NONE", false)).isTrue();
        assertThat(currentScoreProvenance(
                "EVALUATED", "CACHE", "NONE", false)).isTrue();
        assertThat(currentScoreProvenance(
                "INVALID_LEARNER_RESPONSE",
                "BACKEND_RULE",
                "BLANK_ANSWER",
                false)).isTrue();
        assertThat(currentScoreProvenance(
                "INVALID_LEARNER_RESPONSE",
                "BACKEND_RULE",
                "NO_HANGUL",
                false)).isTrue();
        assertThat(currentScoreProvenance(
                "INVALID_LEARNER_RESPONSE",
                "BACKEND_RULE",
                "INVALID_LEARNER_RESPONSE",
                false)).isTrue();
    }

    @Test
    void currentScoreProvenanceRejectsStaleReasonAndRetryableScore() {
        assertThat(currentScoreProvenance(
                "INVALID_LEARNER_RESPONSE",
                "BACKEND_RULE",
                "EMPTY_OR_TOO_SHORT",
                false)).isFalse();
        assertThat(currentScoreProvenance(
                "EVALUATED", "PROVIDER", "NONE", true)).isFalse();
        assertThat(currentScoreProvenance(
                "EVALUATED", "BACKEND_RULE", "NONE", false)).isFalse();
    }

    private boolean currentScoreProvenance(
            String status,
            String source,
            String reason,
            boolean retryable) {
        WritingEvaluationResult value = new WritingEvaluationResult(
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                "Q51",
                WritingEvaluationNormalizer.EVALUATION_ENGINE,
                "TASK_NATIVE_RUBRIC_V1",
                WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                status,
                source,
                reason,
                retryable,
                true,
                com.ksh.features.practice.ai.contract
                        .PracticeAiResultCompleteness.complete());
        return WritingAssessmentPolicyBundle
                .hasExactCurrentScoreProvenance(value);
    }

    private JsonNode normalize(String taskType, String rubricScores)
            throws Exception {
        int requestedRawScore = 0;
        for (JsonNode row : objectMapper.readTree(rubricScores)) {
            requestedRawScore += row.path("score").asInt();
        }
        String learnerAnswer =
                WritingContractTestFixtures.scoreBearingLearnerAnswer(
                        taskType, requestedRawScore);
        var envelope = WritingContractTestFixtures.zeroEnvelope(
                objectMapper, taskType, learnerAnswer);
        WritingContractTestFixtures.applyRawScore(
                envelope, taskType, learnerAnswer, requestedRawScore);
        for (JsonNode source : objectMapper.readTree(rubricScores)) {
            var target = WritingContractTestFixtures.rubric(
                    envelope, source.path("criterionId").asText());
            target.put("score", source.path("score").asInt());
            target.put("maxScore", source.path("maxScore").asInt());
        }
        return objectMapper.readTree(normalizer.normalize(
                objectMapper.writeValueAsString(envelope),
                taskType,
                learnerAnswer,
                null));
    }
}
