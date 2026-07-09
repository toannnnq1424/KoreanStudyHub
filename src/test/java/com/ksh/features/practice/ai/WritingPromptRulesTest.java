package com.ksh.features.practice.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WritingPromptRulesTest {
    @Test
    void usesAllowedRubricContractWithoutLegacyBandsOrSampleAnswer() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q53", false);
        assertThat(prompt)
                .contains("allowed_rubric.scoring_criteria", "max_score", "backend tính",
                        "upgraded_answer", "upgraded_answer_annotated", "sentence_rewrites",
                        "correction phải sửa đúng lỗi", "Không bịa dữ kiện hoặc lập luận mới")
                .doesNotContain("1.0-9.0", "FEW-SHOT CALIBRATION", "Bài mẫu khoảng", "max khoảng 4.0")
                .contains("Không tạo bài mẫu độc lập", "Không tạo sample_answer");
    }

    @Test
    void scoringCriteriaAreStableAndSumToOneHundred() {
        var rows = WritingPromptRules.scoringCriteriaForTask("Q53");
        assertThat(rows).extracting(WritingPromptRules.ScoringCriterion::criterionId)
                .containsExactly("W_CONTENT_TASK_ACHIEVEMENT", "W_ORGANIZATION_COHERENCE", "W_LANGUAGE_EXPRESSION");
        assertThat(rows.stream().mapToInt(WritingPromptRules.ScoringCriterion::maxScore).sum()).isEqualTo(100);
    }

    @Test
    void preservesRichTaskSpecificSections() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q54", false);
        assertThat(prompt).contains("STRENGTHS", "NEEDS IMPROVEMENT", "QUY TẮC EVIDENCE",
                "ĐÁNH GIÁ TỪ VỰNG", "SPAM / OFF-TOPIC", "YÊU CẦU OUTPUT");
        assertThat(WritingPromptRules.taskDetailRules("Q54")).isNotBlank();
        assertThat(WritingPromptRules.taskUpgradeRules("Q54")).isNotBlank();
    }
}
