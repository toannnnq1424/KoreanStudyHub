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
        SpeakingEvaluatorCapability evaluatorCapability,
        SpeakingEvidenceMode evidenceMode,
        String evidenceContractVersion,
        SpeakingContractTrust contractTrust,
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
        boolean explicitCurrentCapability = knownCapabilityContract(
                evaluatorCapability, evidenceMode, evidenceContractVersion);
        if (evaluatorCapability == null) {
            evaluatorCapability = SpeakingEvaluatorCapability.LEGACY_UNKNOWN;
        }
        if (evidenceMode == null) {
            evidenceMode = SpeakingEvidenceMode.UNKNOWN;
        }
        if (contractTrust == null || !explicitCurrentCapability) {
            contractTrust = SpeakingContractTrust.LEGACY_UNVERIFIED;
        }
        majorStrengths = copy(majorStrengths);
        majorNeedsImprovement = copy(majorNeedsImprovement);
        actionPlan = copy(actionPlan);
        criterionFeedback = copy(criterionFeedback);
        transcriptAnnotations = copy(transcriptAnnotations);
        strengths = copy(strengths);
        needsImprovement = copy(needsImprovement);
        rubricScores = copy(rubricScores);
        boolean detailedEvidenceValid = true;
        if (explicitCurrentCapability) {
            boolean transcriptRequired = evaluationStatus != null && evaluationStatus.scoreBearing();
            detailedEvidenceValid = (!transcriptRequired
                    || actuallyHeardTranscript != null && !actuallyHeardTranscript.isBlank())
                    && actionPlan.stream().allMatch(SpeakingEvaluationResult::validActionPlanItem)
                    && validCriterionFeedback(criterionFeedback, rubricScores)
                    && transcriptAnnotations.stream().allMatch(row ->
                    validTranscriptAnnotation(row, actuallyHeardTranscript))
                    && strengths.stream().allMatch(row -> validFeedbackItem(row, actuallyHeardTranscript)
                    && "".equals(row.correction()))
                    && needsImprovement.stream().allMatch(row -> validFeedbackItem(row, actuallyHeardTranscript));
            if (!transcriptRequired) {
                detailedEvidenceValid = detailedEvidenceValid
                        && actionPlan.isEmpty() && criterionFeedback.isEmpty()
                        && transcriptAnnotations.isEmpty() && strengths.isEmpty()
                        && needsImprovement.isEmpty();
            }

            actionPlan = actionPlan.stream()
                    .filter(SpeakingEvaluationResult::validActionPlanItem)
                    .toList();
            criterionFeedback = criterionFeedback.stream()
                    .filter(row -> row != null && row.criterion() != null && row.criterion().transcriptGrounded())
                    .map(SpeakingEvaluationResult::sanitizeCriterionFeedback)
                    .toList();
            transcriptAnnotations = transcriptAnnotations.stream()
                    .filter(row -> validTranscriptAnnotation(row, actuallyHeardTranscript))
                    .toList();
            strengths = strengths.stream()
                    .filter(row -> validFeedbackItem(row, actuallyHeardTranscript)
                            && "".equals(row.correction()))
                    .toList();
            needsImprovement = needsImprovement.stream()
                    .filter(row -> validFeedbackItem(row, actuallyHeardTranscript))
                    .toList();
            interpretedIntent = null;
            intentConfidence = null;
        }
        if (contractTrust == SpeakingContractTrust.CURRENT_VERIFIED
                && explicitCurrentCapability
                && (!validRubricContract(evaluationStatus, rubricScores) || !detailedEvidenceValid)) {
            contractTrust = SpeakingContractTrust.LEGACY_UNVERIFIED;
        }
        findings = copy(findings);
        evidence = copy(evidence);
        if (explicitCurrentCapability) {
            if (!findings.isEmpty() || !evidence.stream().allMatch(row ->
                    validEvidence(row, actuallyHeardTranscript))) {
                if (contractTrust == SpeakingContractTrust.CURRENT_VERIFIED) {
                    contractTrust = SpeakingContractTrust.LEGACY_UNVERIFIED;
                }
            }
            findings = List.of();
            evidence = evidence.stream()
                    .filter(row -> validEvidence(row, actuallyHeardTranscript))
                    .toList();
        }
        recommendations = copy(recommendations);
        pronunciationAdvisory = copy(pronunciationAdvisory);
        fluencyObservations = copy(fluencyObservations);
        if (evaluatorCapability != SpeakingEvaluatorCapability.LEGACY_UNKNOWN
                && !evaluatorCapability.acousticCriteriaSupported()) {
            rubricScores = rubricScores.stream()
                    .map(row -> row.criterion() != null && row.criterion().requiresAcousticEvidence()
                            ? new RubricScore(
                            row.criterion(), null, null, row.feedback(),
                            SpeakingCriterionAvailability.NOT_SCORABLE)
                            : row)
                    .toList();
            criterionFeedback = criterionFeedback.stream()
                    .filter(row -> row.criterion() != null && row.criterion().transcriptGrounded())
                    .toList();
            transcriptAnnotations = transcriptAnnotations.stream()
                    .filter(row -> transcriptGrounded(row.criterion(), row.evidenceSource()))
                    .toList();
            strengths = strengths.stream()
                    .filter(row -> transcriptGrounded(row.criterion(), row.evidenceSource()))
                    .toList();
            needsImprovement = needsImprovement.stream()
                    .filter(row -> transcriptGrounded(row.criterion(), row.evidenceSource()))
                    .toList();
            actionPlan = actionPlan.stream()
                    .filter(row -> row.criterion() != null && row.criterion().transcriptGrounded())
                    .toList();
            evidence = evidence.stream()
                    .filter(row -> transcriptGrounded(row.criterion(), row.source()))
                    .toList();
        }
        if (!scoreAvailable || overallScore == null || !evaluatorCapability.holisticScoreSupported()) {
            scoreAvailable = false;
            overallScore = null;
            levelLabel = null;
        }
        if (!evaluatorCapability.acousticCriteriaSupported()) {
            listenerBurden = null;
            pronunciationAdvisory = List.of();
            fluencyObservations = List.of();
        }
    }

    /**
     * Compatibility constructor for callers compiled against the pre-capability
     * result envelope. New persistence uses the canonical constructor fields.
     */
    public SpeakingEvaluationResult(
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
        this(evaluationStatus, scoreAvailable, source, model, transcriptionModel,
                promptVersion, rubricVersion, schemaVersion,
                SpeakingEvaluatorCapability.LEGACY_UNKNOWN,
                SpeakingEvidenceMode.UNKNOWN,
                null,
                SpeakingContractTrust.LEGACY_UNVERIFIED,
                audioMediaId, mediaVersion, transcript, normalizedTranscript,
                actuallyHeardTranscript, interpretedIntent, intentConfidence,
                transcriptConfidence, listenerBurden, overallScore, levelLabel,
                overallSummary, taskAchievementSummary, majorStrengths,
                majorNeedsImprovement, actionPlan, criterionFeedback,
                transcriptAnnotations, strengths, needsImprovement, confidenceNotes,
                rubricScores, findings, evidence, recommendations, upgradedAnswer,
                sampleAnswer, pronunciationAdvisory, fluencyObservations,
                errorCategory, retryable);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public boolean currentEvidenceContract() {
        return contractTrust == SpeakingContractTrust.CURRENT_VERIFIED
                && evaluationStatus != null
                && source != null
                && evaluationStatus != SpeakingEvaluationStatus.LEGACY_RESULT
                && evaluationStatus != SpeakingEvaluationStatus.MOCK_EVALUATED
                && source != SpeakingEvaluationSource.LEGACY
                && source != SpeakingEvaluationSource.MOCK
                && knownCapabilityContract(evaluatorCapability, evidenceMode, evidenceContractVersion)
                && currentVersionContract()
                && validRubricContract(evaluationStatus, rubricScores);
    }

    public boolean profileAvailable() {
        return currentEvidenceContract()
                && evaluationStatus != SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE
                && evaluationStatus.scoreBearing();
    }

    public boolean holisticScoreAvailable() {
        return currentEvidenceContract() && scoreAvailable && overallScore != null
                && evaluatorCapability.holisticScoreSupported();
    }

    private static boolean knownCapabilityContract(
            SpeakingEvaluatorCapability capability,
            SpeakingEvidenceMode mode,
            String contractVersion
    ) {
        if (capability == SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION) {
            return mode == SpeakingEvidenceMode.TRANSCRIPT_ONLY
                    && java.util.Objects.equals(capability.contractVersion(), contractVersion);
        }
        // AUDIO_DIRECT_FULL_RESERVED is an enum/seam only. Phase 13 has no
        // authorized evaluator that consumes learner audio, so even a matching
        // reserved version/mode must never become a trusted current contract.
        return false;
    }

    private boolean currentVersionContract() {
        return java.util.Objects.equals(SpeakingPromptRules.PROMPT_VERSION, promptVersion)
                && java.util.Objects.equals(SpeakingPromptRules.RUBRIC_VERSION, rubricVersion)
                && java.util.Objects.equals(SpeakingPromptRules.SCHEMA_VERSION, schemaVersion);
    }

    private static boolean validRubricContract(
            SpeakingEvaluationStatus status,
            List<RubricScore> rows
    ) {
        if (status == null || rows == null) {
            return false;
        }
        if (status == SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE) {
            return rows.isEmpty();
        }
        if (!status.scoreBearing()) {
            return rows.isEmpty();
        }

        java.util.EnumSet<SpeakingRubricCriterion> seen =
                java.util.EnumSet.noneOf(SpeakingRubricCriterion.class);
        for (RubricScore row : rows) {
            if (row == null || row.criterion() == null || !seen.add(row.criterion())) {
                return false;
            }
            SpeakingRubricCriterion criterion = row.criterion();
            if (criterion.requiresAcousticEvidence()) {
                if (row.availability() != SpeakingCriterionAvailability.NOT_SCORABLE
                        || row.score() != null || row.maxScore() != null) {
                    return false;
                }
                continue;
            }
            if (row.availability() != SpeakingCriterionAvailability.SCORED
                    || row.score() == null
                    || row.maxScore() == null
                    || row.maxScore().compareTo(criterion.maxScore()) != 0
                    || row.score().compareTo(BigDecimal.ZERO) < 0
                    || row.score().compareTo(row.maxScore()) > 0) {
                return false;
            }
        }
        return seen.equals(java.util.EnumSet.allOf(SpeakingRubricCriterion.class));
    }

    private static boolean transcriptGrounded(
            SpeakingRubricCriterion criterion,
            SpeakingEvidenceSource source
    ) {
        return criterion != null && criterion.transcriptGrounded()
                && source != null && source.transcriptLanguageGrounding();
    }

    private static boolean validActionPlanItem(ActionPlanItem row) {
        return row != null && row.criterion() != null && row.criterion().transcriptGrounded()
                && row.criterion().ownsSubcriterion(row.subCriterionId());
    }

    private static boolean validCriterionFeedback(
            List<CriterionFeedback> rows,
            List<RubricScore> rubricScores
    ) {
        java.util.EnumSet<SpeakingRubricCriterion> seen =
                java.util.EnumSet.noneOf(SpeakingRubricCriterion.class);
        return rows.stream().allMatch(row -> validCriterionFeedbackRow(row, rubricScores)
                && seen.add(row.criterion()));
    }

    private static boolean validCriterionFeedbackRow(
            CriterionFeedback row,
            List<RubricScore> rubricScores
    ) {
        return row != null && row.criterion() != null && row.criterion().transcriptGrounded()
                && rubricScores.stream().anyMatch(score -> score != null
                && score.criterion() == row.criterion()
                && score.availability() == SpeakingCriterionAvailability.SCORED
                && java.util.Objects.equals(score.score(), row.score())
                && java.util.Objects.equals(score.maxScore(), row.maxScore()))
                && row.subcriteria().stream().allMatch(sub -> sub != null
                && row.criterion().ownsSubcriterion(sub.subCriterionId()));
    }

    private static CriterionFeedback sanitizeCriterionFeedback(CriterionFeedback row) {
        return new CriterionFeedback(
                row.criterion(), row.displayName(), row.score(), row.maxScore(), row.levelLabel(),
                row.summary(), row.strengths(), row.needsImprovement(),
                row.subcriteria().stream()
                        .filter(sub -> sub != null && row.criterion().ownsSubcriterion(sub.subCriterionId()))
                        .toList());
    }

    private static boolean validTranscriptAnnotation(
            TranscriptAnnotation row,
            String actuallyHeardTranscript
    ) {
        return row != null && row.criterion() != null && row.criterion().transcriptGrounded()
                && row.criterion().ownsSubcriterion(row.subCriterionId())
                && row.evidenceSource() == SpeakingEvidenceSource.TRANSCRIPT
                && validEvidenceScope(row.evidenceScope(), row.evidence(),
                row.startOffset(), row.endOffset(), actuallyHeardTranscript);
    }

    private static boolean validFeedbackItem(FeedbackItem row, String actuallyHeardTranscript) {
        return row != null && row.criterion() != null && row.criterion().transcriptGrounded()
                && row.criterion().ownsSubcriterion(row.subCriterionId())
                && row.evidenceSource() == SpeakingEvidenceSource.TRANSCRIPT
                && validEvidenceScope(row.evidenceScope(), row.evidence(),
                null, null, actuallyHeardTranscript);
    }

    private static boolean validEvidence(Evidence row, String actuallyHeardTranscript) {
        return row != null && row.source() == SpeakingEvidenceSource.TRANSCRIPT
                && row.criterion() != null && row.criterion().transcriptGrounded()
                && row.excerpt() != null && !row.excerpt().isBlank()
                && actuallyHeardTranscript != null
                && actuallyHeardTranscript.contains(row.excerpt());
    }

    private static boolean validEvidenceScope(
            String scope,
            String evidence,
            Integer startOffset,
            Integer endOffset,
            String actuallyHeardTranscript
    ) {
        if (scope == null || actuallyHeardTranscript == null) {
            return false;
        }
        if ("WHOLE_ANSWER".equals(scope)) {
            return "".equals(evidence) && startOffset == null && endOffset == null;
        }
        if (!"TEXT_SPAN".equals(scope) || evidence == null || evidence.isBlank()
                || !actuallyHeardTranscript.contains(evidence)) {
            return false;
        }
        if (startOffset == null && endOffset == null) {
            // FeedbackItem has no offset fields; the exact substring invariant is
            // sufficient because it cannot create a positioned highlight.
            return true;
        }
        return startOffset != null && endOffset != null
                && startOffset >= 0 && endOffset == startOffset + evidence.length()
                && endOffset <= actuallyHeardTranscript.length()
                && actuallyHeardTranscript.substring(startOffset, endOffset).equals(evidence);
    }

    public record RubricScore(
            SpeakingRubricCriterion criterion,
            BigDecimal score,
            BigDecimal maxScore,
            String feedback,
            SpeakingCriterionAvailability availability
    ) {
        public RubricScore {
            availability = availability == null
                    ? (score == null ? SpeakingCriterionAvailability.UNAVAILABLE
                    : SpeakingCriterionAvailability.SCORED)
                    : availability;
            if (availability != SpeakingCriterionAvailability.SCORED) {
                score = null;
                maxScore = null;
            }
        }

        public RubricScore(
                SpeakingRubricCriterion criterion,
                BigDecimal score,
                BigDecimal maxScore,
                String feedback
        ) {
            this(criterion, score, maxScore, feedback,
                    score == null ? SpeakingCriterionAvailability.UNAVAILABLE
                            : SpeakingCriterionAvailability.SCORED);
        }

        public boolean scored() {
            return availability == SpeakingCriterionAvailability.SCORED
                    && score != null && maxScore != null && maxScore.signum() > 0;
        }
    }

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
