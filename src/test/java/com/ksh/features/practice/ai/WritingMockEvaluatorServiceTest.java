package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WritingMockEvaluatorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingMockEvaluatorService mockEvaluator = new WritingMockEvaluatorService(objectMapper);

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
}
