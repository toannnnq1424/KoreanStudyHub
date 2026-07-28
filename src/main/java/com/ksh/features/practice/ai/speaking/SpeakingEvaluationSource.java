package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SpeakingEvaluationSource {
    PROVIDER,
    TEXT_FALLBACK,
    MOCK,
    LEGACY,
    SYSTEM;

    @JsonCreator
    public static SpeakingEvaluationSource fromJson(String value) {
        if (value == null || value.isBlank()) {
            return LEGACY;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return LEGACY;
        }
    }
}
