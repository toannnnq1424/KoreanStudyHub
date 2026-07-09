package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WritingTaskNativeScoringTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingEvaluationNormalizer normalizer = new WritingEvaluationNormalizer(objectMapper);

    @Test
    void q53UsesEarnedScoreThirtyPointMaximumAndExplicitPercentage() throws Exception {
        JsonNode result = normalize("Q53", """
                [
                  {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","name":"ignored","score":9,"maxScore":12,"feedback":"A"},
                  {"criterionId":"W_ORGANIZATION_COHERENCE","name":"ignored","score":6,"maxScore":9,"feedback":"B"},
                  {"criterionId":"W_LANGUAGE_EXPRESSION","name":"ignored","score":7,"maxScore":9,"feedback":"C"}
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
                  {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","name":"ignored","score":16,"maxScore":20,"feedback":"A"},
                  {"criterionId":"W_ORGANIZATION_COHERENCE","name":"ignored","score":12,"maxScore":15,"feedback":"B"},
                  {"criterionId":"W_LANGUAGE_EXPRESSION","name":"ignored","score":10,"maxScore":15,"feedback":"C"}
                ]
                """);

        assertThat(result.path("raw_score").asDouble()).isEqualTo(38.0);
        assertThat(result.path("raw_score_max").asDouble()).isEqualTo(50.0);
        assertThat(result.path("percentage").asDouble()).isEqualTo(76.0);
    }

    @Test
    void q51RequiresBothBlankRubricsAndUsesTenPointMaximum() throws Exception {
        JsonNode result = normalize("Q51", """
                [
                  {"criterionId":"W_CLOZE_BLANK_1_CONTEXT","name":"ignored","score":2,"maxScore":2,"feedback":"A"},
                  {"criterionId":"W_CLOZE_BLANK_1_GRAMMAR","name":"ignored","score":1.5,"maxScore":2,"feedback":"B"},
                  {"criterionId":"W_CLOZE_BLANK_1_EXPRESSION","name":"ignored","score":1,"maxScore":1,"feedback":"C"},
                  {"criterionId":"W_CLOZE_BLANK_2_CONTEXT","name":"ignored","score":1,"maxScore":2,"feedback":"D"},
                  {"criterionId":"W_CLOZE_BLANK_2_GRAMMAR","name":"ignored","score":1,"maxScore":2,"feedback":"E"},
                  {"criterionId":"W_CLOZE_BLANK_2_EXPRESSION","name":"ignored","score":0.5,"maxScore":1,"feedback":"F"}
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
    }

    @Test
    void legacyBandProjectionKeepsLegacyPercentageConversion() throws Exception {
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

        assertThat(result.path("score").asDouble()).isEqualTo(7.0);
        assertThat(result.path("percentage").decimalValue())
                .isEqualByComparingTo(WritingScoreMatrix.toHundredPointScale(7.0));
        assertThat(result.path("scoring_contract").asText()).isEqualTo("LEGACY_BAND_V1");
    }

    private JsonNode normalize(String taskType, String rubricScores) throws Exception {
        String json = """
                {
                  "summary":"OK",
                  "rubric_scores":%s,
                  "strengths":[],
                  "needs_improvement":[],
                  "upgraded_answer":"",
                  "upgraded_answer_annotated":"",
                  "sentence_rewrites":[]
                }
                """.formatted(rubricScores);
        return objectMapper.readTree(normalizer.normalize(json, taskType, "한국어 답안", null));
    }
}
