package com.ksh.features.practice.assessment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AssessmentScoringEngine {

    public AssessmentScoreResult score(AnswerSpec spec,
                                       LearnerAnswer answer,
                                       BigDecimal possiblePoints) {
        require(spec, "answer spec");
        require(answer, "learner answer");
        BigDecimal points = require(possiblePoints, "possible points");
        if (points.signum() < 0) {
            throw new IllegalArgumentException("Possible points must not be negative");
        }
        if (spec.questionType() == null || answer.questionType() == null
                || spec.questionType() != answer.questionType()) {
            throw new IllegalArgumentException("Answer type must match answer spec type");
        }
        rejectDuplicateSelections(answer.selectedOptionIds());

        return switch (spec.questionType()) {
            case SINGLE_CHOICE -> scoreSingleChoice(spec, answer, points);
            case TRUE_FALSE_NOT_GIVEN -> scoreTfng(spec, answer, points);
            case FILL_BLANK -> scoreFillBlank(spec, answer, points);
            case ESSAY, SPEAKING -> pendingAi(spec, points);
        };
    }

    private AssessmentScoreResult scoreSingleChoice(AnswerSpec spec,
                                                    LearnerAnswer answer,
                                                    BigDecimal points) {
        requirePolicy(spec, Set.of(ScoringPolicyCode.ALL_OR_NOTHING));
        requireCount(spec.correctOptionIds(), 1, "single-choice correct option");
        if (answer.selectedOptionIds().isEmpty()) {
            return result(AssessmentScoreStatus.NOT_ANSWERED, BigDecimal.ZERO, points, spec, 0, 1);
        }
        requireCount(answer.selectedOptionIds(), 1, "single-choice learner selection");
        boolean correct = spec.correctOptionIds().get(0).equals(answer.selectedOptionIds().get(0));
        return binary(correct, points, spec);
    }

    private AssessmentScoreResult scoreTfng(AnswerSpec spec,
                                            LearnerAnswer answer,
                                            BigDecimal points) {
        requirePolicy(spec, Set.of(ScoringPolicyCode.ALL_OR_NOTHING));
        if (blank(spec.correctValue())) {
            throw new IllegalArgumentException("TFNG answer spec has no correct value");
        }
        if (blank(answer.selectedValue())) {
            return result(AssessmentScoreStatus.NOT_ANSWERED, BigDecimal.ZERO, points, spec, 0, 1);
        }
        return binary(normalize(answer.selectedValue()).equals(normalize(spec.correctValue())), points, spec);
    }

    private AssessmentScoreResult scoreFillBlank(AnswerSpec spec,
                                                 LearnerAnswer answer,
                                                 BigDecimal points) {
        requirePolicy(spec, Set.of(ScoringPolicyCode.NORMALIZED_EXACT));
        if (spec.blanks().isEmpty()) {
            throw new IllegalArgumentException("Fill-blank answer spec has no blanks");
        }
        if (answer.blankAnswers().isEmpty()
                || answer.blankAnswers().values().stream().allMatch(AssessmentScoringEngine::blank)) {
            return result(AssessmentScoreStatus.NOT_ANSWERED, BigDecimal.ZERO, points, spec,
                    0, spec.blanks().size());
        }

        int correctUnits = 0;
        for (AnswerSpec.BlankAnswer blankSpec : spec.blanks()) {
            String learnerValue = answer.blankAnswers().get(blankSpec.blankId());
            if (!blank(learnerValue) && blankSpec.acceptedValues().stream()
                    .map(AssessmentScoringEngine::normalize)
                    .anyMatch(normalize(learnerValue)::equals)) {
                correctUnits++;
            }
        }
        return aggregate(correctUnits, spec.blanks().size(), points, spec);
    }

    private AssessmentScoreResult pendingAi(AnswerSpec spec, BigDecimal points) {
        requirePolicy(spec, Set.of(ScoringPolicyCode.PROFILE_BASED));
        return result(AssessmentScoreStatus.PENDING_AI, BigDecimal.ZERO, points, spec, 0, 0);
    }

    private AssessmentScoreResult aggregate(int correctUnits,
                                            int totalUnits,
                                            BigDecimal points,
                                            AnswerSpec spec) {
        if (correctUnits == totalUnits) {
            return result(AssessmentScoreStatus.CORRECT, points, points, spec, correctUnits, totalUnits);
        }
        if (correctUnits == 0) {
            return result(AssessmentScoreStatus.INCORRECT, BigDecimal.ZERO, points, spec, 0, totalUnits);
        }
        return result(AssessmentScoreStatus.PARTIALLY_CORRECT,
                proportional(points, correctUnits, totalUnits),
                points, spec, correctUnits, totalUnits);
    }

    private AssessmentScoreResult binary(boolean correct,
                                         BigDecimal points,
                                         AnswerSpec spec) {
        return binary(correct, points, spec, 1);
    }

    private AssessmentScoreResult binary(boolean correct,
                                         BigDecimal points,
                                         AnswerSpec spec,
                                         int totalUnits) {
        return result(correct ? AssessmentScoreStatus.CORRECT : AssessmentScoreStatus.INCORRECT,
                correct ? points : BigDecimal.ZERO,
                points,
                spec,
                correct ? totalUnits : 0,
                totalUnits);
    }

    private AssessmentScoreResult result(AssessmentScoreStatus status,
                                         BigDecimal earned,
                                         BigDecimal possible,
                                         AnswerSpec spec,
                                         int correctUnits,
                                         int totalUnits) {
        return new AssessmentScoreResult(
                status,
                earned,
                possible,
                spec.scoringPolicyCode(),
                correctUnits,
                totalUnits
        );
    }

    private static BigDecimal proportional(BigDecimal points, int numerator, int denominator) {
        return points.multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), MathContext.DECIMAL128);
    }

    private static void rejectDuplicateSelections(List<String> selections) {
        if (new LinkedHashSet<>(selections).size() != selections.size()) {
            throw new IllegalArgumentException("Duplicate selected option ID");
        }
    }

    private static void requirePolicy(AnswerSpec spec, Set<ScoringPolicyCode> allowed) {
        if (spec.scoringPolicyCode() == null || !allowed.contains(spec.scoringPolicyCode())) {
            throw new IllegalArgumentException(
                    "Scoring policy " + spec.scoringPolicyCode() + " is not valid for " + spec.questionType());
        }
    }

    private static void requireCount(List<?> values, int expected, String label) {
        if (values.size() != expected) {
            throw new IllegalArgumentException(label + " must contain exactly " + expected + " value(s)");
        }
    }

    private static <T> T require(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + label);
        }
        return value;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
