package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SpeakingCriterionAvailability {
    SCORED,
    NOT_SCORABLE,
    UNAVAILABLE,
    LEGACY_UNVERIFIED;

    @JsonCreator
    public static SpeakingCriterionAvailability fromJson(String value) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return UNAVAILABLE;
        }
    }
}
