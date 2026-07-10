package com.ksh.features.practice.ai.writing;

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
        assertThat(rows).extracting(WritingScoringCriterion::criterionId)
                .containsExactly("W_CONTENT_TASK_ACHIEVEMENT", "W_ORGANIZATION_COHERENCE", "W_LANGUAGE_EXPRESSION");
        assertThat(rows.stream().mapToInt(WritingScoringCriterion::maxScore).sum()).isEqualTo(30);
        assertThat(rows).extracting(WritingScoringCriterion::maxScore).containsExactly(12, 9, 9);
    }

    @Test
    void clozePromptUsesTwoBlankTaskNativeCriteria() {
        var rows = WritingPromptRules.scoringCriteriaForTask("Q51");
        assertThat(rows).hasSize(6);
        assertThat(rows.stream().mapToInt(WritingScoringCriterion::maxScore).sum()).isEqualTo(10);
        assertThat(WritingPromptRules.buildUnifiedPrompt("Q51", false))
                .contains("allowed_rubric.scoring_criteria")
                .doesNotContain("đúng 3 phần tử", "đúng 3 tên tiêu chí");
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
