package com.ksh.features.practice.assessment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectiveExplanationStrategyRegistryTest {

    @Test
    void allowlistIsQuestionTypeSpecificAndVersioned() {
        assertThat(ObjectiveExplanationStrategyRegistry.options(
                CanonicalQuestionType.SINGLE_CHOICE))
                .extracting(ObjectiveExplanationStrategyRegistry.Option::code)
                .containsExactly(
                        "EXACT_EVIDENCE_ONLY",
                        "FULL_SOURCE_INLINE_HIGHLIGHT",
                        "QUESTION_EVIDENCE_TRANSLATION_TABLE",
                        "MCQ_OPTION_ELIMINATION",
                        "EVIDENCE_AND_ELIMINATION",
                        "KEYWORD_PARAPHRASE_BRIDGE",
                        "BILINGUAL_STEP_BY_STEP");
        assertThat(ObjectiveExplanationStrategyRegistry.options(
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN))
                .extracting(ObjectiveExplanationStrategyRegistry.Option::code)
                .containsExactly(
                        "EXACT_EVIDENCE_ONLY",
                        "FULL_SOURCE_INLINE_HIGHLIGHT",
                        "QUESTION_EVIDENCE_TRANSLATION_TABLE",
                        "TFNG_CONTRADICTION_TABLE",
                        "NOT_GIVEN_BOUNDARY",
                        "KEYWORD_PARAPHRASE_BRIDGE",
                        "BILINGUAL_STEP_BY_STEP");
        assertThat(ObjectiveExplanationStrategyRegistry.options(
                CanonicalQuestionType.FILL_BLANK))
                .extracting(ObjectiveExplanationStrategyRegistry.Option::code)
                .containsExactly(
                        "EXACT_EVIDENCE_ONLY",
                        "FULL_SOURCE_INLINE_HIGHLIGHT",
                        "QUESTION_EVIDENCE_TRANSLATION_TABLE",
                        "FILL_SLOT_GRAMMAR_ANALYSIS",
                        "KEYWORD_PARAPHRASE_BRIDGE",
                        "BILINGUAL_STEP_BY_STEP");
        assertThat(ObjectiveExplanationStrategyRegistry.options(
                CanonicalQuestionType.MULTIPLE_ANSWER))
                .extracting(ObjectiveExplanationStrategyRegistry.Option::code)
                .contains("EVIDENCE_AND_ELIMINATION");
        assertThat(ObjectiveExplanationStrategyRegistry.options(
                CanonicalQuestionType.MATCHING))
                .extracting(ObjectiveExplanationStrategyRegistry.Option::code)
                .contains("MATCHING_MATRIX");
        assertThat(ObjectiveExplanationStrategyRegistry.catalog())
                .hasSize(20);
        assertThat(ObjectiveExplanationStrategyRegistry.catalog())
                .filteredOn(entry -> !entry.selectable())
                .extracting(entry -> entry.code().name())
                .contains(
                        "SEQUENCE_TIMELINE",
                        "SPEAKER_INTENT_AND_ATTITUDE",
                        "TIMESTAMP_TURN_MAP",
                        "HYBRID_TEACHER_GUIDED");
    }

    @Test
    void incompatibleUnknownOrWrongVersionSelectionFailsClosed() {
        assertThatThrownBy(() ->
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        CanonicalQuestionType.FILL_BLANK,
                        ObjectiveExplanationStrategyRegistry
                                .CURRENT_REGISTRY_VERSION,
                        "MCQ_OPTION_ELIMINATION",
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        CanonicalQuestionType.SINGLE_CHOICE,
                        "future-registry",
                        "EXACT_EVIDENCE_ONLY",
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        CanonicalQuestionType.SINGLE_CHOICE,
                        ObjectiveExplanationStrategyRegistry
                                .CURRENT_REGISTRY_VERSION,
                        "PROVIDER_INVENTED",
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void immutableV1SelectionsRemainReadableButCannotMixWithV2Codes() {
        assertThat(ObjectiveExplanationStrategyRegistry.requireSelection(
                CanonicalQuestionType.SINGLE_CHOICE,
                ObjectiveExplanationStrategyRegistry.LEGACY_REGISTRY_VERSION,
                "EVIDENCE_ONLY",
                ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION)
                .generationFamily())
                .isEqualTo(
                        ObjectiveExplanationStrategyRegistry.GenerationFamily
                                .EVIDENCE);
        assertThatThrownBy(() ->
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        CanonicalQuestionType.SINGLE_CHOICE,
                        ObjectiveExplanationStrategyRegistry
                                .CURRENT_REGISTRY_VERSION,
                        "EVIDENCE_ONLY",
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notGivenBoundaryRequiresNotGivenCanonicalAnswer() {
        ObjectiveExplanationStrategyRegistry.Selection selection =
                currentSelection(
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        "NOT_GIVEN_BOUNDARY");
        QuestionContent content = QuestionContent.empty();

        assertThatThrownBy(() ->
                ObjectiveExplanationStrategyRegistry.requireAllowed(
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        selection,
                        content,
                        tfngAnswer("FALSE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires NOT_GIVEN");

        ObjectiveExplanationStrategyRegistry.requireAllowed(
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                selection,
                content,
                tfngAnswer("NOT_GIVEN"));
    }

    @Test
    void mcqAndFillStrategiesRequireMatchingStableAnswerIds() {
        QuestionContent mcq = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("option_a", "A"),
                        new QuestionContent.Option("option_b", "B")),
                List.of());
        AnswerSpec mismatchedMcq = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("missing_option"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        assertThatThrownBy(() ->
                ObjectiveExplanationStrategyRegistry.requireAllowed(
                        CanonicalQuestionType.SINGLE_CHOICE,
                        currentSelection(
                                CanonicalQuestionType.SINGLE_CHOICE,
                                "MCQ_OPTION_ELIMINATION"),
                        mcq,
                        mismatchedMcq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correct option ID");

        QuestionContent fill = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(new QuestionContent.Blank("blank_a", "Ô 1")));
        AnswerSpec mismatchedFill = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer(
                        "blank_b",
                        List.of("서울"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        assertThatThrownBy(() ->
                ObjectiveExplanationStrategyRegistry.requireAllowed(
                        CanonicalQuestionType.FILL_BLANK,
                        currentSelection(
                                CanonicalQuestionType.FILL_BLANK,
                                "FILL_SLOT_GRAMMAR_ANALYSIS"),
                        fill,
                        mismatchedFill))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank IDs");
    }

    private static ObjectiveExplanationStrategyRegistry.Selection
            currentSelection(
            CanonicalQuestionType type,
            String code) {
        return ObjectiveExplanationStrategyRegistry.requireSelection(
                type,
                ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION,
                code,
                ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
    }

    private static AnswerSpec tfngAnswer(String value) {
        return new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                List.of(),
                value,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
    }
}
