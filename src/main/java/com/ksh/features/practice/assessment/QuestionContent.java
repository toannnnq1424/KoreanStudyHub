package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuestionContent(
        String schemaVersion,
        List<Option> options,
        List<Item> matchingLeftItems,
        List<Item> matchingRightItems,
        List<Blank> blanks
) {
    public static final String SCHEMA_VERSION = "question-content-v1";

    public QuestionContent {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        options = immutable(options);
        matchingLeftItems = immutable(matchingLeftItems);
        matchingRightItems = immutable(matchingRightItems);
        blanks = immutable(blanks);
    }

    public static QuestionContent empty() {
        return new QuestionContent(SCHEMA_VERSION, List.of(), List.of(), List.of(), List.of());
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Option(String id, String text) {
    }

    public record Item(String id, String text) {
    }

    public record Blank(String id, String prompt) {
    }
}
