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
        assertFalse(root.has("score"), "Mock JSON should not contain top-level score");
        assertFalse(root.has("raw_score"), "Mock JSON should not contain top-level raw_score");
        assertTrue(root.path("summary").asText().contains("[MOCK_EVALUATION]"));
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
        assertFalse(root.has("score"), "Mock JSON should not contain top-level score");
        assertEquals("Q53", root.path("task_type").asText());
        assertFalse(root.path("strengths").isEmpty());
        assertFalse(root.path("needs_improvement").isEmpty());
    }

    @Test
    void testTaskAwareMockNormalization() throws Exception {
        // Test Q53
        {
            WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                    "Q53", 250, "OK: length fits", List.of()
            );
            String mockOutput = mockEvaluator.evaluate(
                    "Prompt Q53", "제시된 자료에 따르면 한국어 수치는 일정한 변화 양상을 보인다.", analysis, "Test"
            );
            String normalized = normalizer.normalize(mockOutput, "Q53", "제시된 자료에 따르면 한국어 수치는 일정한 변화 양상을 보인다.", analysis);
            JsonNode root = objectMapper.readTree(normalized);
            assertEquals(30.0, root.path("raw_score_max").asDouble(), "Q53 raw_score_max must be 30");
            assertEquals("MOCK_EVALUATED", root.path("evaluation_status").asText());
            assertEquals("MOCK", root.path("evaluation_source").asText());
            assertEquals("MOCK_ONLY", root.path("evaluation_reason").asText());
            assertTrue(root.path("score_available").asBoolean(false));
            assertTrue(root.path("raw_score").isNumber());
        }

        // Test Q54
        {
            WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                    "Q54", 450, "OK: length fits", List.of()
            );
            String mockOutput = mockEvaluator.evaluate(
                    "Prompt Q54", "현대 사회에서는 다양한 사회적 변화로 인해 새로운 문제가 나타나고 있다.", analysis, "Test"
            );
            String normalized = normalizer.normalize(mockOutput, "Q54", "현대 사회에서는 다양한 사회적 변화로 인해 새로운 문제가 나타나고 있다.", analysis);
            JsonNode root = objectMapper.readTree(normalized);
            assertEquals(50.0, root.path("raw_score_max").asDouble(), "Q54 raw_score_max must be 50");
        }

        // Test Q51_52
        {
            WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                    "Q51_52", 15, "OK: short answer", List.of()
            );
            String mockOutput = mockEvaluator.evaluate(
                    "Prompt Q51_52", "열심히 공부할 계획이다", analysis, "Test"
            );
            String normalized = normalizer.normalize(mockOutput, "Q51_52", "열심히 공부할 계획이다", analysis);
            JsonNode root = objectMapper.readTree(normalized);
            assertEquals(10.0, root.path("raw_score_max").asDouble(), "Q51_52 raw_score_max must be 10");
        }
    }

    @Test
    void testEvidenceValidationAndFiltering() throws Exception {
        // If evidence does not exist in student text, it should be filtered out by full normalizer overload
        WritingRuleEngine.RuleViolation violation = new WritingRuleEngine.RuleViolation(
                "해요", "-ㄴ다", "Use formal Korean in writing"
        );
        WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                "Q53", 250, "OK: length fits", List.of(violation)
        );
        
        // Mock evaluator generates mock JSON
        String mockOutput = mockEvaluator.evaluate(
                "Prompt Q53", "한국어를 공부합니다. 재미있다.", analysis, "Test"
        );
        
        // Validate mockOutput strengths & needs_improvement has evidence from learnerAnswer
        JsonNode mockRoot = objectMapper.readTree(mockOutput);
        
        // Learner answer for normalization does NOT contain "해요"
        String learnerAnswer = "한국어를 공부합니다. 재미있다.";
        String normalized = normalizer.normalize(mockOutput, "Q53", learnerAnswer, analysis);
        JsonNode normRoot = objectMapper.readTree(normalized);
        
        // Any need_improvement with evidence "해요" should be excluded because it does not exist in learnerAnswer
        JsonNode needs = normRoot.path("needs_improvement");
        for (JsonNode need : needs) {
            String evidence = need.path("evidence").asText();
            if (!evidence.isEmpty()) {
                assertTrue(learnerAnswer.contains(evidence), "Evidence must exist in learner answer: " + evidence);
            }
        }
    }
}
