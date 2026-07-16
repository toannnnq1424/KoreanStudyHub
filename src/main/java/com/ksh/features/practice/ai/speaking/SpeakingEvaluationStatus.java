package com.ksh.features.practice.ai.speaking;

public enum SpeakingEvaluationStatus {
    EVALUATED(true),
    TRANSCRIPTION_LOW_CONFIDENCE(true),
    TRANSCRIPTION_UNAVAILABLE(false),
    EVALUATION_UNAVAILABLE(false),
    EVALUATION_CONTRACT_FAILED(false),
    INVALID_PROVIDER_RESULT(false),
    AUDIO_MISSING(false),
    AUDIO_UNAVAILABLE(false),
    TEXT_FALLBACK_EVALUATED(true),
    MOCK_EVALUATED(true),
    LEGACY_RESULT(true);

    private final boolean scoreBearing;

    SpeakingEvaluationStatus(boolean scoreBearing) {
        this.scoreBearing = scoreBearing;
    }

    public boolean scoreBearing() {
        return scoreBearing;
    }
}
