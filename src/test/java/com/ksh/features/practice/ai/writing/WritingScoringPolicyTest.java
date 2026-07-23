package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WritingScoringPolicyTest {

    @Test
    void exposesStableInternalTaskNativeProfileIdentity() {
        assertThat(WritingScoringPolicy.PROFILE_ID)
                .isEqualTo("KSH_INTERNAL_TASK_NATIVE_V1");
    }

    @Test
    void q51AndQ52HaveTwoFivePointBlankRubrics() {
        assertClozeRubric("Q51");
        assertClozeRubric("Q52");
        assertClozeRubric("Q51_52");
    }

    @Test
    void q53UsesThirtyPointTaskNativeRubric() {
        WritingScoringRubric rubric = WritingScoringPolicy.rubricFor("Q53");

        assertThat(rubric.criteria()).extracting(WritingScoringCriterion::maxScore)
                .containsExactly(12, 9, 9);
        assertThat(rubric.totalMaxScore()).isEqualTo(30);
    }

    @Test
    void q54UsesFiftyPointTaskNativeRubric() {
        WritingScoringRubric rubric = WritingScoringPolicy.rubricFor("Q54");

        assertThat(rubric.criteria()).extracting(WritingScoringCriterion::maxScore)
                .containsExactly(20, 15, 15);
        assertThat(rubric.totalMaxScore()).isEqualTo(50);
    }

    @Test
    void generalKeepsOneHundredPointInternalRubric() {
        WritingScoringRubric rubric = WritingScoringPolicy.rubricFor("GENERAL");

        assertThat(rubric.criteria()).extracting(WritingScoringCriterion::maxScore)
                .containsExactly(40, 30, 30);
        assertThat(rubric.totalMaxScore()).isEqualTo(100);
    }

    @Test
    void percentageUsesEarnedOverTaskMaximumAndClamps() {
        assertThat(WritingScoringPolicy.percentage(BigDecimal.valueOf(7.5), BigDecimal.TEN))
                .isEqualByComparingTo("75.00");
        assertThat(WritingScoringPolicy.percentage(BigDecimal.valueOf(60), BigDecimal.valueOf(50)))
                .isEqualByComparingTo("100.00");
        assertThat(WritingScoringPolicy.percentage(BigDecimal.valueOf(-1), BigDecimal.valueOf(30)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void feedbackPercentageIsExplicitAndLegacyBandRemainsCompatible() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(WritingScoringPolicy.percentageFromFeedback(
                mapper.readTree("{\"percentage\":8.0,\"score\":8.0,\"scoring_contract\":\"TASK_NATIVE_RUBRIC_V1\"}")))
                .isEqualByComparingTo("8.0");
        assertThat(WritingScoringPolicy.percentageFromFeedback(
                mapper.readTree("{\"score\":8.0,\"scoring_contract\":\"TASK_NATIVE_RUBRIC_V1\"}")))
                .isEqualByComparingTo("8.0");
        assertThat(WritingScoringPolicy.percentageFromFeedback(mapper.readTree("{\"score\":8.0}")))
                .isEqualByComparingTo(WritingScoreMatrix.toHundredPointScale(8.0));
        assertThat(WritingScoringPolicy.percentageFromFeedback(
                mapper.readTree("{\"score_available\":false,\"score\":8.0}")))
                .isEqualByComparingTo("0");
    }

    private static void assertClozeRubric(String taskType) {
        WritingScoringRubric rubric = WritingScoringPolicy.rubricFor(taskType);
        assertThat(rubric.criteria()).extracting(WritingScoringCriterion::maxScore)
                .containsExactly(2, 2, 1, 2, 2, 1);
        assertThat(rubric.criteria()).extracting(WritingScoringCriterion::criterionId)
                .containsExactly(
                        "W_CLOZE_BLANK_1_CONTEXT",
                        "W_CLOZE_BLANK_1_GRAMMAR",
                        "W_CLOZE_BLANK_1_EXPRESSION",
                        "W_CLOZE_BLANK_2_CONTEXT",
                        "W_CLOZE_BLANK_2_GRAMMAR",
                        "W_CLOZE_BLANK_2_EXPRESSION"
                );
        assertThat(rubric.totalMaxScore()).isEqualTo(10);
    }
}
