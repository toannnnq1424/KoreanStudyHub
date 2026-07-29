package com.ksh.features.practice.ai.writing;

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
        assertTrue(analysis.charCountWarning().contains("nằm trong phạm vi"));
    }

    @Test
    void oldAnalyzeOverloadKeepsLegacyDetection() {
        WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze(
                "Bài 54 nghị luận 600-700자",
                "가".repeat(250));

        assertEquals("Q54", analysis.taskType());
        assertTrue(analysis.charCountWarning().contains("thấp hơn đáng kể"));
    }

    @Test
    void testAnalyzeSpokenLanguage() {
        WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze("Câu 53", "진짜 그리고 했어요");
        assertEquals("Q53", analysis.taskType());
        assertEquals(10, analysis.characterCount());
        
        List<WritingRuleEngine.RuleViolation> violations = analysis.ruleViolations();
        assertTrue(violations.stream().anyMatch(v -> "진짜".equals(v.evidence())));
        assertTrue(violations.stream().noneMatch(v -> "그리고".equals(v.evidence())));
        assertTrue(violations.stream().anyMatch(v -> "했어요".equals(v.evidence())));
    }

    @Test
    void severityModelClassifiesAuditedTerms() {
        WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze(
                "Câu 54",
                "근데 진짜 되게 엄청 해요 했어요 있어요 없어요 예요 이에요 어떤 것 같아요 같아요 좀 한테 이랑 그리고 하고",
                "Q54");

        List<WritingRuleEngine.RuleViolation> violations = analysis.ruleViolations();
        assertSeverity(violations, "근데", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "진짜", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "되게", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "엄청", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "해요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "했어요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "있어요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "없어요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "예요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "이에요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "어떤 것 같아요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "같아요", WritingRuleEngine.RuleSeverity.HIGH);
        assertSeverity(violations, "좀", WritingRuleEngine.RuleSeverity.MEDIUM);
        assertSeverity(violations, "한테", WritingRuleEngine.RuleSeverity.MEDIUM);
        assertSeverity(violations, "이랑", WritingRuleEngine.RuleSeverity.MEDIUM);
        assertTrue(violations.stream().noneMatch(v -> "그리고".equals(v.evidence())));
        assertTrue(violations.stream().noneMatch(v -> "하고".equals(v.evidence())));
    }

    @Test
    void overlappingRulesPreferLongerExactEvidence() {
        WritingRuleEngine.RuleAnalysis particleAnalysis = ruleEngine.analyze(
                "Câu 54",
                "친구이랑 공부했다",
                "Q54");
        assertTrue(particleAnalysis.ruleViolations().stream().anyMatch(v -> "이랑".equals(v.evidence())));
        assertTrue(particleAnalysis.ruleViolations().stream().noneMatch(v -> "랑".equals(v.evidence())));

        WritingRuleEngine.RuleAnalysis phraseAnalysis = ruleEngine.analyze(
                "Câu 54",
                "어떤 것 같아요",
                "Q54");
        assertTrue(phraseAnalysis.ruleViolations().stream().anyMatch(v -> "어떤 것 같아요".equals(v.evidence())));
        assertTrue(phraseAnalysis.ruleViolations().stream().noneMatch(v -> "같아요".equals(v.evidence())));
    }

    @Test
    void everyDeterministicSignalRemainsAdvisory() {
        for (String taskType : List.of("Q53", "Q54")) {
            WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze(
                    "Prompt",
                    "진짜 좀",
                    taskType);

            assertAction(analysis.ruleViolations(), "진짜", WritingRuleEngine.RuleAction.SUGGESTION);
            assertAction(analysis.ruleViolations(), "좀", WritingRuleEngine.RuleAction.SUGGESTION);
        }
    }

    @Test
    void q51AndQ52SignalsAreAdvisoryOnly() {
        for (String taskType : List.of("Q51", "Q52")) {
            WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze(
                    "Prompt",
                    "그리고 하고 좀 한테 이랑 사랑 해요",
                    taskType);

            assertTrue(analysis.ruleViolations().stream().noneMatch(v -> "그리고".equals(v.evidence())));
            assertTrue(analysis.ruleViolations().stream().noneMatch(v -> "하고".equals(v.evidence())));
            assertTrue(analysis.ruleViolations().stream().noneMatch(v -> "랑".equals(v.evidence())));
            assertAction(analysis.ruleViolations(), "해요", WritingRuleEngine.RuleAction.SUGGESTION);
        }
    }

    @Test
    void generalIsSofterThanEssayTasks() {
        WritingRuleEngine.RuleAnalysis analysis = ruleEngine.analyze(
                "General prompt",
                "진짜 좀",
                "GENERAL");

        assertAction(analysis.ruleViolations(), "진짜", WritingRuleEngine.RuleAction.SUGGESTION);
        assertAction(analysis.ruleViolations(), "좀", WritingRuleEngine.RuleAction.SUGGESTION);
    }

    @Test
    void normalConnectorsEmbeddedSyllablesAndDecomposedHangulDoNotCreateHardFindings() {
        WritingRuleEngine.RuleAnalysis normal = ruleEngine.analyze(
                "Prompt",
                "사랑을 이야기하고 그리고 결론을 쓴다.",
                "Q54");
        assertTrue(normal.ruleViolations().isEmpty());

        WritingRuleEngine.RuleAnalysis decomposed = ruleEngine.analyze(
                "Prompt",
                java.text.Normalizer.normalize(
                        "진짜 그렇다.", java.text.Normalizer.Form.NFD),
                "Q54");
        assertEquals(1, decomposed.ruleViolations().size());
        assertEquals(
                WritingRuleEngine.RuleAction.SUGGESTION,
                decomposed.ruleViolations().get(0).action());
    }

    @Test
    void testBuildCharCountWarningQ53() {
        WritingRuleEngine.RuleAnalysis analysisShort = ruleEngine.analyze("Câu 53", "가".repeat(100));
        assertTrue(analysisShort.charCountWarning().contains("thấp hơn đáng kể"));

        WritingRuleEngine.RuleAnalysis analysisWarning = ruleEngine.analyze("Câu 53", "가".repeat(180));
        assertTrue(analysisWarning.charCountWarning().contains("thấp hơn phạm vi"));

        WritingRuleEngine.RuleAnalysis analysisOk = ruleEngine.analyze("Câu 53", "가".repeat(250));
        assertTrue(analysisOk.charCountWarning().contains("nằm trong phạm vi"));

        WritingRuleEngine.RuleAnalysis analysisLong = ruleEngine.analyze("Câu 53", "가".repeat(380));
        assertTrue(analysisLong.charCountWarning().contains("vượt đáng kể"));
        assertFalse(analysisLong.charCountWarning().contains("-10"));
    }

    @Test
    void testBuildCharCountWarningQ54() {
        WritingRuleEngine.RuleAnalysis analysisShort = ruleEngine.analyze("Câu 54", "가".repeat(300));
        assertTrue(analysisShort.charCountWarning().contains("thấp hơn đáng kể"));

        WritingRuleEngine.RuleAnalysis analysisWarning = ruleEngine.analyze("Câu 54", "가".repeat(500));
        assertTrue(analysisWarning.charCountWarning().contains("thấp hơn phạm vi"));

        WritingRuleEngine.RuleAnalysis analysisOk = ruleEngine.analyze("Câu 54", "가".repeat(650));
        assertTrue(analysisOk.charCountWarning().contains("nằm trong phạm vi"));

        WritingRuleEngine.RuleAnalysis analysisLong = ruleEngine.analyze("Câu 54", "가".repeat(800));
        assertTrue(analysisLong.charCountWarning().contains("vượt đáng kể"));
        assertFalse(analysisLong.charCountWarning().contains("-15"));
    }

    private static void assertSeverity(List<WritingRuleEngine.RuleViolation> violations,
                                       String evidence,
                                       WritingRuleEngine.RuleSeverity severity) {
        WritingRuleEngine.RuleViolation violation = findViolation(violations, evidence);
        assertEquals(severity, violation.severity());
    }

    private static void assertAction(List<WritingRuleEngine.RuleViolation> violations,
                                     String evidence,
                                     WritingRuleEngine.RuleAction action) {
        WritingRuleEngine.RuleViolation violation = findViolation(violations, evidence);
        assertEquals(action, violation.action());
    }

    private static WritingRuleEngine.RuleViolation findViolation(
            List<WritingRuleEngine.RuleViolation> violations,
            String evidence) {
        return violations.stream()
                .filter(v -> evidence.equals(v.evidence()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing violation for evidence: " + evidence));
    }
}
