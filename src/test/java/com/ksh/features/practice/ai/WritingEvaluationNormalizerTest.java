package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WritingEvaluationNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingEvaluationNormalizer normalizer = new WritingEvaluationNormalizer(objectMapper);

    @Test
    void testNormalizeHappyPath() throws Exception {
        String aiJson = """
        {
          "score": 6.5,
          "task_type": "Q53",
          "summary": "Tốt",
          "student_text": "한국어를 공부합니다.",
          "strengths": [
            {
              "criterionId": "W_REGISTER_HONORIFIC_ACCURACY",
              "evidence": "합니다",
              "explanationVi": "Đúng đuôi văn viết"
            },
            {
              "criterionId": "W_ADVANCED_GRAMMAR_STRUCTURES",
              "evidence": "공부합니다",
              "explanationVi": "Đúng cấu trúc"
            }
          ],
          "needs_improvement": []
        }
        """;

        String normalizedJson = normalizer.normalize(aiJson);
        JsonNode root = objectMapper.readTree(normalizedJson);

        assertEquals(6.0, root.path("score").asDouble());
        assertEquals("Q53", root.path("task_type").asText());
        assertEquals("KSH_WRITING_EVALUATOR_V1", root.path("engine").asText());
        assertFalse(root.path("annotations").isEmpty());
        assertEquals(7, root.path("annotations").get(0).path("start").asInt());
    }

    @Test
    void testNormalizeFallbackOnError() throws Exception {
        String invalidJson = "{ malformed json }";
        String normalizedJson = normalizer.normalize(invalidJson);
        
        JsonNode root = objectMapper.readTree(normalizedJson);
        assertEquals(1.0, root.path("score").asDouble());
        assertEquals("KSH_WRITING_EVALUATOR_FALLBACK", root.path("engine").asText());
    }
}
