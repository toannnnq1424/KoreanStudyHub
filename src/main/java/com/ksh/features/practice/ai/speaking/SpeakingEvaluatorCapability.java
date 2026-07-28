package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Declares what evidence the selected evaluator actually consumes. The current
 * OpenAI-compatible evaluator receives transcript text and optional task-image
 * context; it never receives learner audio or derived acoustic measurements.
 */
public enum SpeakingEvaluatorCapability {
    TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION(
            "speaking-evidence-v1-transcript-language-only",
            false,
            false,
            false),
    /**
     * Extension point only. No client selects this capability today. A future
     * implementation must directly consume authorized learner audio and pass
     * calibration/readiness gates before acousticCriteriaSupported and
     * holisticScoreSupported can be enabled.
     */
    AUDIO_DIRECT_FULL_RESERVED(
            "speaking-evidence-future-audio-direct-reserved",
            true,
            false,
            false),
    LEGACY_UNKNOWN(null, false, false, false);

    private final String contractVersion;
    private final boolean directLearnerAudioRequired;
    private final boolean acousticCriteriaSupported;
    private final boolean holisticScoreSupported;

    SpeakingEvaluatorCapability(
            String contractVersion,
            boolean directLearnerAudioRequired,
            boolean acousticCriteriaSupported,
            boolean holisticScoreSupported
    ) {
        this.contractVersion = contractVersion;
        this.directLearnerAudioRequired = directLearnerAudioRequired;
        this.acousticCriteriaSupported = acousticCriteriaSupported;
        this.holisticScoreSupported = holisticScoreSupported;
    }

    public String contractVersion() {
        return contractVersion;
    }

    public boolean holisticScoreSupported() {
        return holisticScoreSupported;
    }

    public boolean directLearnerAudioRequired() {
        return directLearnerAudioRequired;
    }

    public boolean acousticCriteriaSupported() {
        return acousticCriteriaSupported;
    }

    @JsonCreator
    public static SpeakingEvaluatorCapability fromJson(String value) {
        if (value == null || value.isBlank()) {
            return LEGACY_UNKNOWN;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return LEGACY_UNKNOWN;
        }
    }
}
