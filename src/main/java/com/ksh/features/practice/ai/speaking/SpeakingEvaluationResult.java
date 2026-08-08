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
        String policyBundleId,
        SpeakingEvaluatorCapability evaluatorCapability,
        SpeakingEvidenceMode evidenceMode,
        String evidenceContractVersion,
        SpeakingContractTrust contractTrust,
        Long questionVersionId,
        String promptContextFingerprint,
        String promptContextContractIdentity,
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
        boolean retryable,
        String policyBundleFingerprint
) {
    public SpeakingEvaluationResult {
        policyBundleId = policyBundleId == null || policyBundleId.isBlank()
                ? null
                : policyBundleId.trim();
        policyBundleFingerprint = policyBundleFingerprint == null
                || policyBundleFingerprint.isBlank()
                ? null
                : policyBundleFingerprint.trim();
        boolean explicitCurrentCapability = knownCapabilityContract(
                evaluatorCapability, evidenceMode, evidenceContractVersion)
                && java.util.Objects.equals(
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                policyBundleId)
                && java.util.Objects.equals(
                SpeakingAssessmentPolicyBundle.fingerprint(),
                policyBundleFingerprint);
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
        findings = copy(findings);
        evidence = copy(evidence);
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
                    && needsImprovement.stream().allMatch(row -> validFeedbackItem(row, actuallyHeardTranscript))
                    && evidence.stream().allMatch(row ->
                    validEvidence(row, actuallyHeardTranscript))
                    && validLedgerLinkage(
                    rubricScores, transcriptAnnotations, strengths,
                    needsImprovement, evidence);
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
     * Source-compatibility constructor. Direct current-result producers in this
     * codebase receive the current full-bundle fingerprint; JSON deserialization
     * still uses the canonical constructor and therefore fails closed when the
     * persisted fingerprint is absent.
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
            String policyBundleId,
            SpeakingEvaluatorCapability evaluatorCapability,
            SpeakingEvidenceMode evidenceMode,
            String evidenceContractVersion,
            SpeakingContractTrust contractTrust,
            Long questionVersionId,
            String promptContextFingerprint,
            String promptContextContractIdentity,
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
        this(
                evaluationStatus, scoreAvailable, source, model,
                transcriptionModel, promptVersion, rubricVersion,
                schemaVersion, policyBundleId, evaluatorCapability,
                evidenceMode, evidenceContractVersion, contractTrust,
                questionVersionId, promptContextFingerprint,
                promptContextContractIdentity, audioMediaId, mediaVersion,
                transcript, normalizedTranscript, actuallyHeardTranscript,
                interpretedIntent, intentConfidence, transcriptConfidence,
                listenerBurden, overallScore, levelLabel, overallSummary,
                taskAchievementSummary, majorStrengths,
                majorNeedsImprovement, actionPlan, criterionFeedback,
                transcriptAnnotations, strengths, needsImprovement,
                confidenceNotes, rubricScores, findings, evidence,
                recommendations, upgradedAnswer, sampleAnswer,
                pronunciationAdvisory, fluencyObservations, errorCategory,
                retryable,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID.equals(
                        policyBundleId)
                        ? SpeakingAssessmentPolicyBundle.fingerprint()
                        : null);
    }

    /**
     * Compatibility constructor for the pre-13C3-03 current-capability
     * envelope. A result without immutable question-context identity is
     * readable but cannot match a new reuse identity.
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
        this(evaluationStatus, scoreAvailable, source, model, transcriptionModel,
                promptVersion, rubricVersion, schemaVersion, null,
                evaluatorCapability, evidenceMode, evidenceContractVersion,
                contractTrust, null, null, null,
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
                promptVersion, rubricVersion, schemaVersion, null,
                SpeakingEvaluatorCapability.LEGACY_UNKNOWN,
                SpeakingEvidenceMode.UNKNOWN,
                null,
                SpeakingContractTrust.LEGACY_UNVERIFIED,
                null, null, null,
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
                && evaluationStatus != SpeakingEvaluationStatus.MOCK_EVALUATED
                && source != SpeakingEvaluationSource.LEGACY
                && source != SpeakingEvaluationSource.MOCK
                && java.util.Objects.equals(
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                policyBundleId)
                && java.util.Objects.equals(
                SpeakingAssessmentPolicyBundle.fingerprint(),
                policyBundleFingerprint)
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
                    || row.evidenceIds().isEmpty()
                    || row.evidenceIds().stream().anyMatch(id ->
                    id == null || id.isBlank())
                    || row.evidenceIds().stream().distinct().count()
                    != row.evidenceIds().size()
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
        return row != null
                && presentId(row.findingId()) && presentId(row.evidenceId())
                && row.criterion() != null && row.criterion().transcriptGrounded()
                && row.criterion().ownsSubcriterion(row.subCriterionId())
                && row.evidenceSource() == SpeakingEvidenceSource.TRANSCRIPT
                && validOccurrenceIdentity(
                row.evidence(), row.startOffset(), row.endOffset(),
                row.occurrenceIndex(), row.occurrenceCount(),
                row.normalization(), row.sourceHash(),
                actuallyHeardTranscript)
                && validEvidenceScope(row.evidenceScope(), row.evidence(),
                row.startOffset(), row.endOffset(), actuallyHeardTranscript);
    }

    private static boolean validFeedbackItem(FeedbackItem row, String actuallyHeardTranscript) {
        return row != null
                && presentId(row.findingId()) && presentId(row.evidenceId())
                && row.criterion() != null && row.criterion().transcriptGrounded()
                && row.criterion().ownsSubcriterion(row.subCriterionId())
                && row.evidenceSource() == SpeakingEvidenceSource.TRANSCRIPT
                && validOccurrenceIdentity(
                row.evidence(), row.startOffset(), row.endOffset(),
                row.occurrenceIndex(), row.occurrenceCount(),
                row.normalization(), row.sourceHash(),
                actuallyHeardTranscript)
                && validEvidenceScope(row.evidenceScope(), row.evidence(),
                row.startOffset(), row.endOffset(), actuallyHeardTranscript);
    }

    private static boolean validEvidence(Evidence row, String actuallyHeardTranscript) {
        return row != null && presentId(row.evidenceId())
                && row.source() == SpeakingEvidenceSource.TRANSCRIPT
                && row.criterion() != null && row.criterion().transcriptGrounded()
                && row.criterion().ownsSubcriterion(row.subCriterionId())
                && validOccurrenceIdentity(
                row.excerpt(), row.startOffset(), row.endOffset(),
                row.occurrenceIndex(), row.occurrenceCount(),
                row.normalization(), row.sourceHash(),
                actuallyHeardTranscript)
                && validEvidenceScope(row.evidenceScope(), row.excerpt(),
                row.startOffset(), row.endOffset(), actuallyHeardTranscript);
    }

    private static boolean validLedgerLinkage(
            List<RubricScore> rubricScores,
            List<TranscriptAnnotation> annotations,
            List<FeedbackItem> strengths,
            List<FeedbackItem> needsImprovement,
            List<Evidence> evidence
    ) {
        java.util.Map<String, Evidence> evidenceById =
                new java.util.LinkedHashMap<>();
        for (Evidence row : evidence) {
            if (row == null || row.evidenceId() == null
                    || evidenceById.put(row.evidenceId(), row) != null) {
                return false;
            }
        }
        java.util.Set<String> findingIds = new java.util.HashSet<>();
        java.util.Set<String> annotationEvidenceIds =
                new java.util.HashSet<>();
        for (TranscriptAnnotation annotation : annotations) {
            Evidence row = annotation == null
                    ? null : evidenceById.get(annotation.evidenceId());
            if (row == null
                    || !findingIds.add(annotation.findingId())
                    || !annotationEvidenceIds.add(annotation.evidenceId())
                    || row.criterion() != annotation.criterion()
                    || !java.util.Objects.equals(
                    row.subCriterionId(), annotation.subCriterionId())) {
                return false;
            }
        }
        java.util.List<FeedbackItem> feedback = new java.util.ArrayList<>();
        feedback.addAll(strengths);
        feedback.addAll(needsImprovement);
        if (feedback.size() != annotations.size()) {
            return false;
        }
        for (FeedbackItem item : feedback) {
            boolean matches = annotations.stream().anyMatch(annotation ->
                    java.util.Objects.equals(
                            annotation.findingId(), item.findingId())
                            && java.util.Objects.equals(
                            annotation.evidenceId(), item.evidenceId())
                            && annotation.criterion() == item.criterion()
                            && java.util.Objects.equals(
                            annotation.subCriterionId(),
                            item.subCriterionId()));
            if (!matches) {
                return false;
            }
        }
        for (RubricScore score : rubricScores) {
            if (score == null || !score.scored()) {
                continue;
            }
            if (score.evidenceIds().isEmpty()
                    || score.evidenceIds().stream().anyMatch(id -> {
                Evidence row = evidenceById.get(id);
                return row == null || row.criterion() != score.criterion();
            })) {
                return false;
            }
            if (score.score().compareTo(score.maxScore()) == 0
                    && annotations.stream().anyMatch(annotation ->
                    annotation.criterion() == score.criterion()
                            && "needs_improvement".equals(
                            annotation.annotationType()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean presentId(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validOccurrenceIdentity(
            String exactText,
            Integer startOffset,
            Integer endOffset,
            Integer index,
            Integer count,
            String normalization,
            String sourceHash,
            String source
    ) {
        if (source == null || exactText == null || exactText.isBlank()
                || startOffset == null || endOffset == null
                || index == null || count == null
                || !"UTF16_EXACT_V1".equals(normalization)
                || !java.util.Objects.equals(
                speakingSourceHash(source), sourceHash)
                || startOffset < 0
                || endOffset != startOffset + exactText.length()
                || endOffset > source.length()
                || !source.substring(startOffset, endOffset).equals(exactText)) {
            return false;
        }
        java.util.List<Integer> positions = new java.util.ArrayList<>();
        for (int cursor = 0;
             cursor + exactText.length() <= source.length();
             cursor++) {
            if (source.regionMatches(
                    cursor, exactText, 0, exactText.length())) {
                positions.add(cursor);
            }
        }
        return count == positions.size()
                && index >= 1 && index <= positions.size()
                && positions.get(index - 1).equals(startOffset);
    }

    private static String speakingSourceHash(String source) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(source.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is required for Speaking evidence identity",
                    exception);
        }
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
            SpeakingCriterionAvailability availability,
            List<String> evidenceIds
    ) {
        public RubricScore {
            evidenceIds = copy(evidenceIds);
            availability = availability == null
                    ? (score == null ? SpeakingCriterionAvailability.UNAVAILABLE
                    : SpeakingCriterionAvailability.SCORED)
                    : availability;
            if (availability != SpeakingCriterionAvailability.SCORED) {
                score = null;
                maxScore = null;
                evidenceIds = List.of();
            }
        }

        public RubricScore(
                SpeakingRubricCriterion criterion,
                BigDecimal score,
                BigDecimal maxScore,
                String feedback,
                SpeakingCriterionAvailability availability
        ) {
            this(criterion, score, maxScore, feedback, availability, List.of());
        }

        public RubricScore(
                SpeakingRubricCriterion criterion,
                BigDecimal score,
                BigDecimal maxScore,
                String feedback
        ) {
            this(criterion, score, maxScore, feedback,
                    score == null ? SpeakingCriterionAvailability.UNAVAILABLE
                            : SpeakingCriterionAvailability.SCORED,
                    List.of());
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
            String findingId,
            String evidenceId,
            String annotationType,
            String category,
            SpeakingRubricCriterion criterion,
            String subCriterionId,
            String originalSpan,
            String replacement,
            Integer startOffset,
            Integer endOffset,
            Integer occurrenceIndex,
            Integer occurrenceCount,
            String normalization,
            String sourceHash,
            String operation,
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
            String findingId,
            String evidenceId,
            SpeakingRubricCriterion criterion,
            String subCriterionId,
            String evidenceScope,
            String evidence,
            SpeakingEvidenceSource evidenceSource,
            Integer startOffset,
            Integer endOffset,
            Integer occurrenceIndex,
            Integer occurrenceCount,
            String normalization,
            String sourceHash,
            String operation,
            String category,
            String explanationVi,
            String correction
    ) {}

    public record Finding(
            String category,
            String message,
            String recommendation
    ) {}

    public record Evidence(
            String evidenceId,
            SpeakingEvidenceSource source,
            SpeakingRubricCriterion criterion,
            String subCriterionId,
            String evidenceScope,
            String excerpt,
            Integer startOffset,
            Integer endOffset,
            Integer occurrenceIndex,
            Integer occurrenceCount,
            String normalization,
            String sourceHash,
            BigDecimal confidence
    ) {}
}
