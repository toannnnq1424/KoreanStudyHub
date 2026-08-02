package com.ksh.features.practice.ai.writing;

import java.util.List;
import java.util.Set;

/**
 * Immutable, Practice-owned identity for the complete Writing assessment policy.
 *
 * <p>The concise id is persisted with evaluation results. The component-complete
 * identity participates in provider/cache fingerprints so changing any scoring,
 * evidence, diagnostic, normalization, or language contract invalidates reuse.</p>
 */
public final class WritingAssessmentPolicyBundle {

    public static final String POLICY_BUNDLE_ID = "KSH_WRITING_POLICY_BUNDLE_V3";
    public static final String TASK_SPEC_VERSION = "writing-task-spec-v3";
    public static final String DESCRIPTOR_VERSION =
            "ksh-writing-detail-diagnostics-seam-v1";
    public static final String EVIDENCE_POLICY_VERSION =
            WritingEvidenceLedgerVerifier.CONTRACT_VERSION;
    public static final String NORMALIZER_VERSION = "writing-normalizer-v3";
    public static final String RULE_ENGINE_VERSION = "writing-rules-advisory-v2";
    public static final String SCORE_AVAILABILITY_VERSION = "writing-score-availability-v2";
    public static final String FIELD_LANGUAGE_VERSION = "writing-fields-vi-ko-v2";
    private static final Set<String> EVALUATED_SCORE_SOURCES =
            Set.of("PROVIDER", "CACHE");
    private static final Set<String> DETERMINISTIC_INVALID_REASONS =
            Set.of("BLANK_ANSWER", "NO_HANGUL", "INVALID_LEARNER_RESPONSE");

    private WritingAssessmentPolicyBundle() {
    }

    public static String identity() {
        return String.join("|", components());
    }

    public static List<String> components() {
        return List.of(
                POLICY_BUNDLE_ID,
                "skill=WRITING",
                "scoring-profile=" + WritingScoringPolicy.PROFILE_ID,
                "task-spec=" + TASK_SPEC_VERSION,
                "prompt=" + WritingPromptRules.PROMPT_VERSION,
                "rubric=" + WritingPromptRules.RUBRIC_VERSION,
                "schema=" + WritingPromptRules.EVALUATION_SCHEMA_VERSION,
                "contract=" + WritingPromptRules.EVALUATION_CONTRACT_VERSION,
                "diagnostics=" + WritingDiagnosticContract.VERSION,
                "descriptors=" + DESCRIPTOR_VERSION,
                "evidence=" + EVIDENCE_POLICY_VERSION,
                "score-anchors=" + WritingScoreAnchorPolicy.VERSION,
                "task-requirements=" + WritingTaskRequirementPolicy.VERSION,
                "normalizer=" + NORMALIZER_VERSION,
                "rules=" + RULE_ENGINE_VERSION,
                "availability=" + SCORE_AVAILABILITY_VERSION,
                "field-language=" + FIELD_LANGUAGE_VERSION);
    }

    /**
     * Exact provenance authority for a current score-bearing Writing result.
     *
     * <p>Every consumer that can authorize a score, READY result, cohort, or
     * reevaluation aggregate must use this predicate. Compatibility readers may
     * still identify older envelopes, but unsupported reason/retryability pairs
     * cannot regain score authority.</p>
     */
    public static boolean hasExactCurrentScoreProvenance(
            WritingEvaluationResult value) {
        if (value == null
                || !value.scoreAvailableFlag()
                || !Boolean.FALSE.equals(value.evaluationRetryable())) {
            return false;
        }
        return ("EVALUATED".equals(value.evaluationStatus())
                && EVALUATED_SCORE_SOURCES.contains(value.evaluationSource())
                && "NONE".equals(value.evaluationReason()))
                || ("INVALID_LEARNER_RESPONSE".equals(
                        value.evaluationStatus())
                && "BACKEND_RULE".equals(value.evaluationSource())
                && DETERMINISTIC_INVALID_REASONS.contains(
                        value.evaluationReason()));
    }
}
