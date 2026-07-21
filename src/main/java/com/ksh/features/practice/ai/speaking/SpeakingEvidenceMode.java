package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SpeakingEvidenceMode {
    TRANSCRIPT_ONLY,
    DIRECT_AUDIO_AND_TRANSCRIPT,
    UNKNOWN;

    @JsonCreator
    public static SpeakingEvidenceMode fromJson(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
