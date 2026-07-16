package com.ksh.features.practice.assessment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentScoringEngineTest {

    private final AssessmentScoringEngine engine = new AssessmentScoringEngine();

    @Test
    void singleChoiceScoresExactSelectionAndEmptyAnswer() {
        AnswerSpec spec = spec(CanonicalQuestionType.SINGLE_CHOICE,
                List.of("opt_2"), null, List.of(), ScoringPolicyCode.ALL_OR_NOTHING);

        assertScore(engine.score(spec, selected(CanonicalQuestionType.SINGLE_CHOICE, "opt_2"), points("2")),
                AssessmentScoreStatus.CORRECT, "2");
        assertScore(engine.score(spec, selected(CanonicalQuestionType.SINGLE_CHOICE, "opt_1"), points("2")),
                AssessmentScoreStatus.INCORRECT, "0");
        assertScore(engine.score(spec, selected(CanonicalQuestionType.SINGLE_CHOICE), points("2")),
                AssessmentScoreStatus.NOT_ANSWERED, "0");
    }

    @Test
    void legacyMcqAliasAndCanonicalSingleChoiceScoreEquivalently() {
        AssessmentContractCodec codec = new AssessmentContractCodec(
                new com.fasterxml.jackson.databind.ObjectMapper(), new QuestionTypeResolver());
        QuestionContent content = codec.adaptLegacyContent("[\"A\",\"B\"]", "MCQ");
        AnswerSpec legacySpec = codec.adaptLegacyAnswerSpec("MCQ", "2", content);
        LearnerAnswer legacyAnswer = codec.adaptLegacyLearnerAnswer("MCQ", "B", content);

        assertScore(engine.score(legacySpec, legacyAnswer, BigDecimal.ONE),
                AssessmentScoreStatus.CORRECT, "1");
    }

    @Test
    void fillBlankNormalizesUnicodeWhitespaceAndScoresPerBlank() {
        String decomposedKorean = Normalizer.normalize("한글", Normalizer.Form.NFD);
        AnswerSpec spec = spec(CanonicalQuestionType.FILL_BLANK, List.of(), null,
                List.of(
                        new AnswerSpec.BlankAnswer("b1", List.of("한글")),
                        new AnswerSpec.BlankAnswer("b2", List.of("Seoul city"))),
                ScoringPolicyCode.NORMALIZED_EXACT);

        LearnerAnswer correct = answer(CanonicalQuestionType.FILL_BLANK, List.of(), null,
                Map.of("b1", "  " + decomposedKorean + " ", "b2", "seoul   CITY"), null);
        assertScore(engine.score(spec, correct, points("5")), AssessmentScoreStatus.CORRECT, "5");

        LearnerAnswer partial = answer(CanonicalQuestionType.FILL_BLANK, List.of(), null,
                Map.of("b1", "한글!", "b2", "Seoul city"), null);
        assertScore(engine.score(spec, partial, points("5")),
                AssessmentScoreStatus.PARTIALLY_CORRECT, "2.5");
    }

    @Test
    void trueFalseNotGivenAndAiScoredTypesHaveExplicitStates() {
        AnswerSpec tfng = spec(CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                List.of(), "NOT_GIVEN", List.of(), ScoringPolicyCode.ALL_OR_NOTHING);
        LearnerAnswer selected = answer(CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                List.of(), " not_given ", Map.of(), null);
        assertScore(engine.score(tfng, selected, BigDecimal.ONE), AssessmentScoreStatus.CORRECT, "1");

        for (CanonicalQuestionType type : List.of(CanonicalQuestionType.ESSAY, CanonicalQuestionType.SPEAKING)) {
            AnswerSpec profileBased = spec(type, List.of(), null, List.of(), ScoringPolicyCode.PROFILE_BASED);
            LearnerAnswer text = answer(type, List.of(), null, Map.of(), "response");
            assertScore(engine.score(profileBased, text, points("10")),
                    AssessmentScoreStatus.PENDING_AI, "0");
        }
    }

    @Test
    void malformedDuplicateMismatchedAndNegativeInputsFailClosed() {
        AnswerSpec spec = spec(CanonicalQuestionType.SINGLE_CHOICE,
                List.of("a"), null, List.of(), ScoringPolicyCode.ALL_OR_NOTHING);

        assertThatThrownBy(() -> engine.score(
                spec, selected(CanonicalQuestionType.SINGLE_CHOICE, "a", "a"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> engine.score(
                spec, selected(CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN, "a"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must match");
        assertThatThrownBy(() -> engine.score(
                spec, selected(CanonicalQuestionType.SINGLE_CHOICE, "a"), points("-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negative");
    }

    private static AnswerSpec spec(CanonicalQuestionType type,
                                   List<String> optionIds,
                                   String correctValue,
                                   List<AnswerSpec.BlankAnswer> blanks,
                                   ScoringPolicyCode policy) {
        return new AnswerSpec(AnswerSpec.SCHEMA_VERSION, type, optionIds, correctValue, blanks, policy);
    }

    private static LearnerAnswer selected(CanonicalQuestionType type, String... ids) {
        return answer(type, List.of(ids), null, Map.of(), null);
    }

    private static LearnerAnswer answer(CanonicalQuestionType type,
                                        List<String> selectedIds,
                                        String selectedValue,
                                        Map<String, String> blanks,
                                        String text) {
        return new LearnerAnswer(LearnerAnswer.SCHEMA_VERSION, type, selectedIds,
                selectedValue, blanks, text);
    }

    private static BigDecimal points(String value) {
        return new BigDecimal(value);
    }

    private static void assertScore(AssessmentScoreResult result,
                                    AssessmentScoreStatus status,
                                    String earnedPoints) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.earnedPoints()).isEqualByComparingTo(earnedPoints);
        assertThat(result.earnedPoints()).isBetween(BigDecimal.ZERO, result.possiblePoints());
    }
}
