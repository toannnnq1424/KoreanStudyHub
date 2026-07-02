package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WritingMockEvaluatorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingMockEvaluatorService mockEvaluator = new WritingMockEvaluatorService(objectMapper);
    private final WritingEvaluationNormalizer normalizer = new WritingEvaluationNormalizer(objectMapper);

    @Test
    void testEvaluateSpam() throws Exception {
        WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                "Q53", 4, "CRITICAL: too short", List.of()
        );
        String resultJson = mockEvaluator.evaluate("Prompt Q53", "asdf", analysis, "No API Key");
        
        JsonNode root = objectMapper.readTree(resultJson);
        assertEquals(1.0, root.path("score").asDouble());
        assertTrue(root.path("summary").asText().contains("SPAM_DETECTED"));
    }

    @Test
    void testEvaluateValid() throws Exception {
        WritingRuleEngine.RuleViolation violation = new WritingRuleEngine.RuleViolation(
                "해요", "-ㄴ다", "Use formal Korean in writing"
        );
        WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                "Q53", 250, "OK: length fits", List.of(violation)
        );
        String resultJson = mockEvaluator.evaluate(
                "Prompt Q53", "저는 한국어를 진짜 공부해요. 따라서 재미있어요.", analysis, "No API Key"
        );
        
        JsonNode root = objectMapper.readTree(resultJson);
        assertTrue(root.path("score").asDouble() > 1.0);
        assertEquals("Q53", root.path("task_type").asText());
        assertFalse(root.path("strengths").isEmpty());
        assertFalse(root.path("needs_improvement").isEmpty());
    }

    @Test
    void testMockOutputNormalizesWithUnifiedNormalizer() throws Exception {
        WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                "Q53", 250, "OK: length fits", List.of()
        );
        String mockOutput = mockEvaluator.evaluate(
                "Prompt Q53", "한국어를 공부합니다. 재미있다.", analysis, "Test"
        );

        // Use backward-compatible normalize(String) — must not crash
        String normalized = normalizer.normalize(mockOutput);
        JsonNode root = objectMapper.readTree(normalized);

        assertTrue(root.path("score").asDouble() >= 1.0);
        assertTrue(root.path("score").asDouble() <= 9.0);
        assertEquals(3, root.path("rubric_scores").size());
        assertTrue(root.has("engine"));
        assertTrue(root.has("raw_score"));
        assertTrue(root.has("raw_score_max"));
        assertTrue(root.has("band_label"));
    }
}
