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
