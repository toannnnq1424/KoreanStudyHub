package com.ksh.features.practice.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WritingPromptRulesTest {

    @Test
    void testQ51_52PromptContract() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q51_52", false);
        
        // Verify correct rubrics
        List<String> expectedRubrics = WritingPromptRules.rubricNamesForTask("Q51_52");
        for (String name : expectedRubrics) {
            assertTrue(prompt.contains(name), "Prompt must contain rubric: " + name);
        }

        // Verify context requirements
        assertTrue(prompt.contains("hoàn thành câu") || prompt.contains("điền chỗ trống"));
        
        // Verify essay negation rules
        assertTrue(prompt.contains("KHÔNG yêu cầu bố cục mở/thân/kết") || prompt.contains("no essay organization/structure requirement"));
    }

    @Test
    void testQ53PromptContract() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q53", false);
        
        // Verify rubric names
        List<String> expectedRubrics = WritingPromptRules.rubricNamesForTask("Q53");
        for (String name : expectedRubrics) {
            assertTrue(prompt.contains(name), "Prompt must contain rubric: " + name);
        }

        // Verify task constraints
        assertTrue(prompt.contains("objective written style") || prompt.contains("khách quan"));
        assertTrue(prompt.contains("no fabricated data/statistics rule") || prompt.contains("KHÔNG tự bịa dữ kiện"));
        assertTrue(prompt.contains("data/task coverage") || prompt.contains("độ bao phủ dữ liệu"));
        assertTrue(prompt.contains("organization anchors") || prompt.contains("từ nối chuyển ý"));
        assertTrue(prompt.contains("language anchors") || prompt.contains("cấu trúc viết"));
    }

    @Test
    void testQ54PromptContract() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q54", false);
        
        // Verify rubric names
        List<String> expectedRubrics = WritingPromptRules.rubricNamesForTask("Q54");
        for (String name : expectedRubrics) {
            assertTrue(prompt.contains(name), "Prompt must contain rubric: " + name);
        }

        // Verify argumentative constraints
        assertTrue(prompt.contains("thesis/argument coverage") || prompt.contains("luận điểm rõ ràng"));
        assertTrue(prompt.contains("reasons/examples/development") || prompt.contains("phát triển ý kiến bằng lý do"));
        assertTrue(prompt.contains("organization anchors") || prompt.contains("mở-thân-kết"));
        assertTrue(prompt.contains("language anchors") || prompt.contains("vựng trung-cao cấp"));
    }

    @Test
    void testGeneralPromptContract() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("GENERAL", false);
        
        // Verify general rubric names
        List<String> expectedRubrics = WritingPromptRules.rubricNamesForTask("GENERAL");
        for (String name : expectedRubrics) {
            assertTrue(prompt.contains(name), "Prompt must contain rubric: " + name);
        }

        // Verify general safety constraint (not forcing into Q53/Q54)
        assertTrue(prompt.contains("no forcing into Q53 or Q54") || prompt.contains("KHÔNG được tự ý ép thành"));
    }
}
