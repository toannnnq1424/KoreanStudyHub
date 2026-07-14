package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuestionContent(
        String schemaVersion,
        List<Option> options,
        List<Blank> blanks,
        String imageReference,
        String audioReference,
        SpeakingDelivery speakingDelivery
) {
    public static final String SCHEMA_VERSION = "question-content-v1";

    public QuestionContent {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        options = immutable(options);
        blanks = immutable(blanks);
    }

    public QuestionContent(String schemaVersion,
                           List<Option> options,
                           List<Blank> blanks,
                           String imageReference,
                           String audioReference) {
        this(schemaVersion, options, blanks, imageReference, audioReference, null);
    }

    public QuestionContent(String schemaVersion,
                           List<Option> options,
                           List<Blank> blanks) {
        this(schemaVersion, options, blanks, null, null, null);
    }

    public static QuestionContent empty() {
        return new QuestionContent(SCHEMA_VERSION, List.of(), List.of());
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Option(String id, String text, String imageReference) {
        public Option(String id, String text) {
            this(id, text, null);
        }
    }

    public record Blank(String id, String prompt) {
    }

    public record SpeakingDelivery(
            String promptAudioReference,
            Integer promptPlayLimit,
            Integer preparationSeconds,
            Integer responseSeconds
    ) {
    }
}
