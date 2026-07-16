package com.ksh.features.practice.result;

import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentScoreResult;
import com.ksh.features.practice.assessment.AssessmentScoreStatus;
import com.ksh.features.practice.assessment.AssessmentScoringEngine;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultTypeBreakdown;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
final class ObjectiveResultPresenter implements PracticeResultPresenter {

    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final AssessmentScoringEngine scoringEngine;
    private final QuestionExplanationReadService explanationReadService;

    ObjectiveResultPresenter(
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            AssessmentScoringEngine scoringEngine,
            QuestionExplanationReadService explanationReadService) {
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.scoringEngine = scoringEngine;
        this.explanationReadService = explanationReadService;
    }

    @Override
    public boolean supports(String skill) {
        return "READING".equals(skill) || "LISTENING".equals(skill);
    }

    @Override
    public Presentation present(PracticeResultContext context) {
        Map<String, TypeAccumulator> byType = new LinkedHashMap<>();
        StateAccumulator overall = new StateAccumulator();

        for (PracticeQuestionVersion question : context.snapshot().questions()) {
            TypeAccumulator type = byType.computeIfAbsent(
                    question.getQuestionType(),
                    ignored -> new TypeAccumulator(question.getQuestionType()));
            try {
                AssessmentScoreResult score = score(question,
                        context.answers().getOrDefault(String.valueOf(question.getQuestionId()), ""));
                type.add(score);
                overall.add(score.status());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                type.unscorable++;
                overall.unscorable++;
            }
        }

        List<ObjectiveResultTypeBreakdown> breakdown = new ArrayList<>();
        for (TypeAccumulator type : byType.values()) {
            breakdown.add(type.toView());
        }
        ResultAnswerDistribution distribution = overall.toDistribution(context.snapshot().questions().size());
        ResultFeedbackAvailability feedback = explanationReadService.availability(
                context.snapshot().questions().stream().map(PracticeQuestionVersion::getId).toList());
        return new Presentation(context.score(), distribution, feedback, new ObjectiveResultPayload(breakdown));
    }

    private AssessmentScoreResult score(PracticeQuestionVersion question, String rawAnswer) {
        CanonicalQuestionType type = typeResolver.resolve(question.getQuestionType());
        QuestionContent content = blank(question.getQuestionContentJson())
                ? contractCodec.adaptLegacyContent(question.getOptionsJson(), question.getQuestionType())
                : contractCodec.readQuestionContent(question.getQuestionContentJson(), type);
        AnswerSpec answerSpec = blank(question.getAnswerSpecJson())
                ? contractCodec.adaptLegacyAnswerSpec(question.getQuestionType(), question.getAnswerKey(), content)
                : contractCodec.readAnswerSpec(question.getAnswerSpecJson(), content);
        LearnerAnswer learnerAnswer = !blank(rawAnswer) && rawAnswer.trim().startsWith("{")
                ? contractCodec.readLearnerAnswer(rawAnswer)
                : contractCodec.adaptLegacyLearnerAnswer(question.getQuestionType(), rawAnswer, content);
        return scoringEngine.score(answerSpec, learnerAnswer, question.getPoints());
    }

    private static String questionTypeLabel(String type) {
        return switch (type == null ? "" : type) {
            case "SINGLE_CHOICE" -> "Trắc nghiệm một đáp án";
            case "TRUE_FALSE_NOT_GIVEN" -> "Đúng, sai hoặc không có thông tin";
            case "FILL_BLANK" -> "Điền từ";
            default -> type;
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static BigDecimal percentage(BigDecimal earned, BigDecimal possible) {
        return possible == null || possible.signum() <= 0 || earned == null
                ? null
                : earned.multiply(BigDecimal.valueOf(100))
                        .divide(possible, 2, RoundingMode.HALF_UP);
    }

    private static final class TypeAccumulator {
        private final String questionType;
        private int total;
        private int correct;
        private int partial;
        private int incorrect;
        private int notAnswered;
        private int pending;
        private int unscorable;
        private int scoredDenominator;
        private BigDecimal earned = BigDecimal.ZERO;
        private BigDecimal possible = BigDecimal.ZERO;

        private TypeAccumulator(String questionType) {
            this.questionType = questionType;
        }

        private void add(AssessmentScoreResult score) {
            total++;
            switch (score.status()) {
                case CORRECT -> correct++;
                case PARTIALLY_CORRECT -> partial++;
                case INCORRECT -> incorrect++;
                case NOT_ANSWERED -> notAnswered++;
                case PENDING_AI -> pending++;
            }
            if (score.status() != AssessmentScoreStatus.PENDING_AI) {
                scoredDenominator++;
                earned = earned.add(score.earnedPoints());
                possible = possible.add(score.possiblePoints());
            }
        }

        private ObjectiveResultTypeBreakdown toView() {
            return new ObjectiveResultTypeBreakdown(
                    questionType,
                    questionTypeLabel(questionType),
                    new ResultAnswerDistribution(correct, partial, incorrect, notAnswered, pending,
                            unscorable, total + unscorable, scoredDenominator),
                    earned,
                    possible,
                    percentage(earned, possible));
        }
    }

    private static final class StateAccumulator {
        private int correct;
        private int partial;
        private int incorrect;
        private int notAnswered;
        private int pending;
        private int unscorable;

        private void add(AssessmentScoreStatus status) {
            switch (status) {
                case CORRECT -> correct++;
                case PARTIALLY_CORRECT -> partial++;
                case INCORRECT -> incorrect++;
                case NOT_ANSWERED -> notAnswered++;
                case PENDING_AI -> pending++;
            }
        }

        private ResultAnswerDistribution toDistribution(int total) {
            int denominator = correct + partial + incorrect + notAnswered;
            return new ResultAnswerDistribution(correct, partial, incorrect, notAnswered,
                    pending, unscorable, total, denominator);
        }
    }
}
