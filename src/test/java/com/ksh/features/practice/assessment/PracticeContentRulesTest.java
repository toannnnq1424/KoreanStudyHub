package com.ksh.features.practice.assessment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeContentRulesTest {

    private final PracticeContentRules rules = new PracticeContentRules();

    @Test
    void exposesTheOnlyAllowedQuestionTypesForEachSkill() {
        assertThat(rules.allowedTypes(AssessmentSkill.READING)).containsExactly(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.MULTIPLE_ANSWER,
                CanonicalQuestionType.MATCHING,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN);
        assertThat(rules.allowedTypes(AssessmentSkill.LISTENING)).containsExactly(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.MULTIPLE_ANSWER,
                CanonicalQuestionType.MATCHING,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN);
    }

    @Test
    void objectivePoliciesCoverTypedMultipleAnswerAndMatching() {
        assertThat(rules.scoringPolicy(CanonicalQuestionType.MULTIPLE_ANSWER))
                .isEqualTo(ScoringPolicyCode.ALL_OR_NOTHING);
        assertThat(rules.scoringPolicy(CanonicalQuestionType.MATCHING))
                .isEqualTo(ScoringPolicyCode.NORMALIZED_EXACT);
        assertThat(rules.maxOptions(CanonicalQuestionType.MATCHING)).isEqualTo(8);
    }

    @Test
    void rejectsQuestionTypesOutsideEachSkillBoundary() {
        assertThatThrownBy(() -> rules.requireAllowed(
                AssessmentSkill.READING, CanonicalQuestionType.ESSAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }
}
