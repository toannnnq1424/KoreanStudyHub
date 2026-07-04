package com.ksh.features.practice.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WritingRuleEngineTest {

    private final WritingRuleEngine ruleEngine = new WritingRuleEngine();

    @Test
    void testDetectTaskType() {
        assertEquals("Q51_52", WritingRuleEngine.detectTaskType("Bài 51 điền vào chỗ trống"));
        assertEquals("Q51_52", WritingRuleEngine.detectTaskType("Bài 52 điền (ㄱ) (ㄴ)"));
        assertEquals("Q53", WritingRuleEngine.detectTaskType("Bài 53 viết 200-300자"));
        assertEquals("Q54", WritingRuleEngine.detectTaskType("Bài 54 nghị luận 600-700자"));
        assertEquals("GENERAL", WritingRuleEngine.detectTaskType("Bài luyện tập chung"));
    }

    @Test
    void analyzeWithResolvedTaskDoesNotRedetectPrompt() {
        WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze(
                "Bài 54 nghị luận 600-700자",
                "가".repeat(250),
                "Q53");

        assertEquals("Q53", analysis.taskType());
        assertTrue(analysis.charCountWarning().contains("OK"));
    }

    @Test
    void oldAnalyzeOverloadKeepsLegacyDetection() {
        WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze(
                "Bài 54 nghị luận 600-700자",
                "가".repeat(250));

        assertEquals("Q54", analysis.taskType());
        assertTrue(analysis.charCountWarning().contains("CRITICAL"));
    }

    @Test
    void testAnalyzeSpokenLanguage() {
        WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze("Câu 53", "진짜 그리고 했어요");
        assertEquals("Q53", analysis.taskType());
        assertEquals(10, analysis.characterCount());
        
        List<WritingRuleEngine.RuleViolation> violations = analysis.ruleViolations();
        assertTrue(violations.stream().anyMatch(v -> "진짜".equals(v.evidence())));
        assertTrue(violations.stream().anyMatch(v -> "그리고".equals(v.evidence())));
        assertTrue(violations.stream().anyMatch(v -> "했어요".equals(v.evidence())));
    }

    @Test
    void testBuildCharCountWarningQ53() {
        WritingRuleEngine.RuleAnalysis analysisShort = ruleEngine.analyze("Câu 53", "가".repeat(100));
        assertTrue(analysisShort.charCountWarning().contains("CRITICAL"));

        WritingRuleEngine.RuleAnalysis analysisWarning = ruleEngine.analyze("Câu 53", "가".repeat(180));
        assertTrue(analysisWarning.charCountWarning().contains("WARNING"));

        WritingRuleEngine.RuleAnalysis analysisOk = ruleEngine.analyze("Câu 53", "가".repeat(250));
        assertTrue(analysisOk.charCountWarning().contains("OK"));

        WritingRuleEngine.RuleAnalysis analysisLong = ruleEngine.analyze("Câu 53", "가".repeat(380));
        assertTrue(analysisLong.charCountWarning().contains("WARNING"));
    }

    @Test
    void testBuildCharCountWarningQ54() {
        WritingRuleEngine.RuleAnalysis analysisShort = ruleEngine.analyze("Câu 54", "가".repeat(300));
        assertTrue(analysisShort.charCountWarning().contains("CRITICAL"));

        WritingRuleEngine.RuleAnalysis analysisWarning = ruleEngine.analyze("Câu 54", "가".repeat(500));
        assertTrue(analysisWarning.charCountWarning().contains("WARNING"));

        WritingRuleEngine.RuleAnalysis analysisOk = ruleEngine.analyze("Câu 54", "가".repeat(650));
        assertTrue(analysisOk.charCountWarning().contains("OK"));

        WritingRuleEngine.RuleAnalysis analysisLong = ruleEngine.analyze("Câu 54", "가".repeat(800));
        assertTrue(analysisLong.charCountWarning().contains("WARNING"));
    }
}
