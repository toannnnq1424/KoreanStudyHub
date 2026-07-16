package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.dto.PracticeDtos.SpeakingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingActionPlanView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingCriterionFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingFindingView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingRubricScoreView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingSubcriterionFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingTranscriptAnnotationView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class SpeakingFeedbackViewMapper {

    public SpeakingFeedbackView map(SpeakingEvaluationResult result) {
        if (result == null) {
            return null;
        }
        return new SpeakingFeedbackView(
                result.scoreAvailable() ? result.overallScore() : null,
                result.overallSummary(),
                result.overallSummary(),
                result.rubricScores().stream()
                        .map(row -> new SpeakingRubricScoreView(
                                row.criterion().label(),
                                percentage(row.score(), row.maxScore()),
                                row.feedback(),
                                row.criterion().id(),
                                row.score(),
                                row.maxScore()))
                        .toList(),
                feedbackItems(result.strengths()),
                feedbackItems(result.needsImprovement()),
                result.sampleAnswer(),
                result.upgradedAnswer(),
                result.model(),
                result.source().name(),
                result.evaluationStatus().name(),
                result.scoreAvailable(),
                result.levelLabel(),
                result.overallSummary(),
                result.taskAchievementSummary(),
                result.majorStrengths(),
                result.majorNeedsImprovement(),
                actionPlan(result.actionPlan()),
                criterionFeedback(result.criterionFeedback()),
                transcriptAnnotations(result.transcriptAnnotations()),
                transcriptAnnotations(result.transcriptAnnotations()),
                result.transcript(),
                result.normalizedTranscript(),
                result.actuallyHeardTranscript(),
                result.interpretedIntent(),
                result.transcriptConfidence(),
                result.confidenceNotes(),
                result.listenerBurden(),
                result.pronunciationAdvisory(),
                result.fluencyObservations(),
                result.errorCategory(),
                result.retryable(),
                result.audioMediaId(),
                result.mediaVersion(),
                result.promptVersion(),
                result.rubricVersion(),
                result.schemaVersion(),
                result.model(),
                result.transcriptionModel());
    }

    private static BigDecimal percentage(BigDecimal score, BigDecimal maxScore) {
        return maxScore == null || maxScore.signum() == 0 || score == null ? null
                : score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);
    }

    private static List<SpeakingFindingView> feedbackItems(List<SpeakingEvaluationResult.FeedbackItem> items) {
        return items.stream()
                .map(item -> new SpeakingFindingView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.evidenceScope(),
                        item.evidence(),
                        item.evidenceSource() == null ? null : item.evidenceSource().name(),
                        item.explanationVi(),
                        item.correction()))
                .toList();
    }

    private static List<SpeakingActionPlanView> actionPlan(List<SpeakingEvaluationResult.ActionPlanItem> items) {
        return items.stream()
                .map(item -> new SpeakingActionPlanView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.title(),
                        item.instruction(),
                        item.reason(),
                        item.priority()))
                .toList();
    }

    private static List<SpeakingCriterionFeedbackView> criterionFeedback(
            List<SpeakingEvaluationResult.CriterionFeedback> items) {
        return items.stream()
                .map(item -> new SpeakingCriterionFeedbackView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.displayName(),
                        item.score(),
                        item.maxScore(),
                        item.levelLabel(),
                        item.summary(),
                        item.strengths(),
                        item.needsImprovement(),
                        item.subcriteria().stream()
                                .map(sub -> new SpeakingSubcriterionFeedbackView(
                                        sub.subCriterionId(),
                                        sub.displayName(),
                                        sub.levelLabel(),
                                        sub.summary(),
                                        sub.strengths(),
                                        sub.needsImprovement()))
                                .toList()))
                .toList();
    }

    private static List<SpeakingTranscriptAnnotationView> transcriptAnnotations(
            List<SpeakingEvaluationResult.TranscriptAnnotation> items) {
        return items.stream()
                .map(item -> new SpeakingTranscriptAnnotationView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.evidenceScope(),
                        item.evidence() == null ? item.originalSpan() : item.evidence(),
                        item.evidenceSource() == null ? null : item.evidenceSource().name(),
                        item.startOffset(),
                        item.endOffset(),
                        item.startOffset(),
                        item.endOffset(),
                        item.annotationType(),
                        annotationKind(item.annotationType()),
                        item.category(),
                        item.explanationVi() == null ? item.explanation() : item.explanationVi(),
                        item.suggestionKo(),
                        item.replacement(),
                        item.severity()))
                .toList();
    }

    private static String annotationKind(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("strength")) {
            return "strength";
        }
        if (normalized.contains("need")) {
            return "need";
        }
        return "advisory";
    }
}
