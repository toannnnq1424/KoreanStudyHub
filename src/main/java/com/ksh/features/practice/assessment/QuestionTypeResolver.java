package com.ksh.features.practice.assessment;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class QuestionTypeResolver {

    private static final Map<String, CanonicalQuestionType> TYPES = Map.ofEntries(
            Map.entry("MCQ", CanonicalQuestionType.SINGLE_CHOICE),
            Map.entry("SINGLE_CHOICE", CanonicalQuestionType.SINGLE_CHOICE),
            Map.entry("MULTIPLE_CHOICE", CanonicalQuestionType.MULTIPLE_CHOICE),
            Map.entry("TRUE_FALSE_NOT_GIVEN", CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
            Map.entry("FILL_BLANK", CanonicalQuestionType.FILL_BLANK),
            Map.entry("GAP_FILL", CanonicalQuestionType.FILL_BLANK),
            Map.entry("MATCHING", CanonicalQuestionType.MATCHING),
            Map.entry("MATCHING_INFORMATION", CanonicalQuestionType.MATCHING),
            Map.entry("ESSAY", CanonicalQuestionType.ESSAY),
            Map.entry("SPEAKING", CanonicalQuestionType.SPEAKING)
    );

    public CanonicalQuestionType resolve(String rawType) {
        return resolveOptional(rawType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported canonical practice question type: " + safe(rawType)));
    }

    public Optional<CanonicalQuestionType> resolveOptional(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES.get(normalize(rawType)));
    }

    public String canonicalCode(String rawType) {
        return resolve(rawType).name();
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "<null>" : value.trim();
    }
}
