package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeSectionDelivery(
        String schemaVersion,
        ListeningDelivery listeningDelivery
) {
    public static final String SCHEMA_VERSION = "practice-section-delivery-v1";

    public PracticeSectionDelivery {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
    }

    public record ListeningDelivery(String checkAudioReference) {
    }
}
