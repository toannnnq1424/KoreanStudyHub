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
        assertTrue(prompt.contains("Q51/Q52 không được phóng đại thành lỗi bài luận"));
        assertTrue(prompt.contains("KHÔNG trả score, raw_score, raw_score_max"));
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
        assertTrue(prompt.contains("dữ kiện hiển thị rõ trong prompt"));
        assertTrue(prompt.contains("không kết luận chắc chắn là \"bịa/sai số liệu\""));
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
        assertTrue(prompt.contains("không thực hiện kiểm tra yêu cầu có cấu trúc"));
        assertTrue(prompt.contains("tránh overclaim"));
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

    @Test
    void promptIncludesTaskAwareRuleSeverityGuidance() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q54", false);

        assertTrue(prompt.contains("severity và action"));
        assertTrue(prompt.contains("HIGH"));
        assertTrue(prompt.contains("MEDIUM"));
        assertTrue(prompt.contains("LOW"));
        assertTrue(prompt.contains("\"그리고\" và \"하고\" không phải lỗi cứng mặc định"));
        assertTrue(prompt.contains("action=NEEDS_IMPROVEMENT"));
        assertTrue(prompt.contains("không tự quyết định điểm cuối"));
        assertTrue(prompt.contains("Không lặp lại máy móc"));
        assertTrue(prompt.contains("thang điểm luyện tập nội bộ của KSH"));
    }

    @Test
    void promptPreservesEvidenceAndProviderBoundaryContract() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q53", false);

        assertTrue(prompt.contains("TEXT_SPAN: evidence PHẢI là chuỗi con CHÍNH XÁC"));
        assertTrue(prompt.contains("WHOLE_ANSWER: evidence phải là chuỗi rỗng"));
        assertTrue(prompt.contains("Không dùng TASK_METADATA khi payload không cung cấp metadata có thẩm quyền"));
        assertTrue(prompt.contains("Chỉ dùng criterionId có trong allowed_rubric"));
        assertTrue(prompt.contains("KHÔNG trả score, raw_score, raw_score_max"));
    }
}
