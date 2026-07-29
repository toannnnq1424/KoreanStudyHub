package com.ksh.features.practice.ai.speaking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Immutable, Practice-owned identity for the complete Korean Speaking
 * assessment policy.
 *
 * <p>The concise id is persisted with every current typed result. The full
 * component identity participates in reuse and control-plane fingerprints so
 * that a change to any prompt, evidence, rubric, rule, normalization or
 * availability contract invalidates old results without promoting historical
 * JSON to the current contract.</p>
 */
public final class SpeakingAssessmentPolicyBundle {

    public static final String POLICY_BUNDLE_ID =
            "KSH_SPEAKING_POLICY_BUNDLE_V1";
    public static final String RUBRIC_REGISTRY_VERSION =
            "speaking-korean-rubric-registry-v2";
    public static final String SUBCRITERION_REGISTRY_VERSION =
            "speaking-korean-subcriteria-v2";
    public static final String RULE_ENGINE_VERSION =
            "speaking-rules-advisory-v2";
    public static final String NORMALIZER_VERSION =
            "speaking-normalizer-v3";
    public static final String TRANSCRIPT_CONFIDENCE_VERSION =
            "speaking-transcript-confidence-v1";
    public static final String AVAILABILITY_POLICY_VERSION =
            "speaking-profile-availability-v2";
    public static final String FIELD_LANGUAGE_VERSION =
            "speaking-fields-vi-ko-v2";

    private SpeakingAssessmentPolicyBundle() {
    }

    public static String identity() {
        return String.join("|", components());
    }

    public static String fingerprint() {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            identity().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is required for the Speaking policy bundle.",
                    exception);
        }
    }

    public static List<String> components() {
        return List.of(
                POLICY_BUNDLE_ID,
                "skill=SPEAKING",
                "prompt=" + SpeakingPromptRules.PROMPT_VERSION,
                "rubric=" + SpeakingPromptRules.RUBRIC_VERSION,
                "schema=" + SpeakingPromptRules.SCHEMA_VERSION,
                "capability="
                        + SpeakingEvaluatorCapability
                        .TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION.name(),
                "evidence-mode=" + SpeakingEvidenceMode.TRANSCRIPT_ONLY.name(),
                "evidence-contract="
                        + SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                "rubric-registry=" + RUBRIC_REGISTRY_VERSION,
                "subcriteria=" + SUBCRITERION_REGISTRY_VERSION,
                "rules=" + RULE_ENGINE_VERSION,
                "normalizer=" + NORMALIZER_VERSION,
                "transcript-confidence="
                        + TRANSCRIPT_CONFIDENCE_VERSION,
                "availability=" + AVAILABILITY_POLICY_VERSION,
                "field-language=" + FIELD_LANGUAGE_VERSION);
    }
}
