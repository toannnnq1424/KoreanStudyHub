package com.ksh.features.practice.assessment;

import com.ksh.entities.WritingTaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeContentRulesTest {

    private final PracticeContentRules rules = new PracticeContentRules();

    @Test
    void exposesTheOnlyAllowedQuestionTypesForEachSkill() {
        assertThat(rules.allowedTypes(AssessmentSkill.READING)).containsExactly(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN);
        assertThat(rules.allowedTypes(AssessmentSkill.LISTENING)).containsExactly(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN);
        assertThat(rules.allowedTypes(AssessmentSkill.WRITING))
                .containsExactly(CanonicalQuestionType.ESSAY);
        assertThat(rules.allowedTypes(AssessmentSkill.SPEAKING))
                .containsExactly(CanonicalQuestionType.SPEAKING);
    }

    @Test
    void rejectsQuestionTypesOutsideEachSkillBoundary() {
        assertThatThrownBy(() -> rules.requireAllowed(
                AssessmentSkill.READING, CanonicalQuestionType.ESSAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> rules.requireAllowed(
                AssessmentSkill.WRITING, CanonicalQuestionType.SINGLE_CHOICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void writingRequiresExactlyTheFourKnownTaskIdentities() {
        assertThat(rules.requiredWritingTasks()).containsExactlyInAnyOrder(
                WritingTaskType.Q51,
                WritingTaskType.Q52,
                WritingTaskType.Q53,
                WritingTaskType.Q54);
    }
}
