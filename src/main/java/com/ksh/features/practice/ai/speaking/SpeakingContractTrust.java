package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SpeakingContractTrust {
    CURRENT_VERIFIED,
    LEGACY_UNVERIFIED;

    @JsonCreator
    public static SpeakingContractTrust fromJson(String value) {
        if (value == null || value.isBlank()) {
            return LEGACY_UNVERIFIED;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return LEGACY_UNVERIFIED;
        }
    }
}
