package com.ksh.features.practice.ai.speaking;

import java.math.BigDecimal;
import java.util.List;

public record SpeakingEvaluationResult(
        SpeakingEvaluationStatus evaluationStatus,
        boolean scoreAvailable,
        SpeakingEvaluationSource source,
        String model,
        String transcriptionModel,
        String promptVersion,
        String rubricVersion,
        String schemaVersion,
        Long audioMediaId,
        Long mediaVersion,
        String transcript,
        String normalizedTranscript,
        String actuallyHeardTranscript,
        String interpretedIntent,
        BigDecimal intentConfidence,
        BigDecimal transcriptConfidence,
        String listenerBurden,
        BigDecimal overallScore,
        String levelLabel,
        String overallSummary,
        String taskAchievementSummary,
        List<String> majorStrengths,
        List<String> majorNeedsImprovement,
        List<ActionPlanItem> actionPlan,
        List<CriterionFeedback> criterionFeedback,
        List<TranscriptAnnotation> transcriptAnnotations,
        List<FeedbackItem> strengths,
        List<FeedbackItem> needsImprovement,
        String confidenceNotes,
        List<RubricScore> rubricScores,
        List<Finding> findings,
        List<Evidence> evidence,
        List<String> recommendations,
        String upgradedAnswer,
        String sampleAnswer,
        List<String> pronunciationAdvisory,
        List<String> fluencyObservations,
        String errorCategory,
        boolean retryable
) {
    public SpeakingEvaluationResult {
        majorStrengths = copy(majorStrengths);
        majorNeedsImprovement = copy(majorNeedsImprovement);
        actionPlan = copy(actionPlan);
        criterionFeedback = copy(criterionFeedback);
        transcriptAnnotations = copy(transcriptAnnotations);
        strengths = copy(strengths);
        needsImprovement = copy(needsImprovement);
        rubricScores = copy(rubricScores);
        findings = copy(findings);
        evidence = copy(evidence);
        recommendations = copy(recommendations);
        pronunciationAdvisory = copy(pronunciationAdvisory);
        fluencyObservations = copy(fluencyObservations);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record RubricScore(
            SpeakingRubricCriterion criterion,
            BigDecimal score,
            BigDecimal maxScore,
            String feedback
    ) {}

    public record CriterionFeedback(
            SpeakingRubricCriterion criterion,
            String displayName,
            BigDecimal score,
            BigDecimal maxScore,
            String levelLabel,
            String summary,
            List<String> strengths,
            List<String> needsImprovement,
            List<SubCriterionFeedback> subcriteria
    ) {
        public CriterionFeedback {
            strengths = copy(strengths);
            needsImprovement = copy(needsImprovement);
            subcriteria = copy(subcriteria);
        }
    }

    public record SubCriterionFeedback(
            String subCriterionId,
            String displayName,
            String levelLabel,
            String summary,
            List<String> strengths,
            List<String> needsImprovement
    ) {
        public SubCriterionFeedback {
            strengths = copy(strengths);
            needsImprovement = copy(needsImprovement);
        }
    }

    public record TranscriptAnnotation(
            String annotationType,
            String category,
            SpeakingRubricCriterion criterion,
            String subCriterionId,
            String originalSpan,
            String replacement,
            Integer startOffset,
            Integer endOffset,
            String explanation,
            String severity,
            SpeakingEvidenceSource evidenceSource,
            String evidenceScope,
            String evidence,
            String explanationVi,
            String suggestionKo,
            BigDecimal confidence
    ) {}

    public record ActionPlanItem(
            SpeakingRubricCriterion criterion,
            String subCriterionId,
            String title,
            String instruction,
            String reason,
            String priority
    ) {}

    public record FeedbackItem(
            SpeakingRubricCriterion criterion,
            String subCriterionId,
            String evidenceScope,
            String evidence,
            SpeakingEvidenceSource evidenceSource,
            String explanationVi,
            String correction
    ) {}

    public record Finding(
            String category,
            String message,
            String recommendation
    ) {}

    public record Evidence(
            SpeakingEvidenceSource source,
            SpeakingRubricCriterion criterion,
            String excerpt,
            BigDecimal confidence
    ) {}
}
