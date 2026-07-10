package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearnerAnswer(
        String schemaVersion,
        CanonicalQuestionType questionType,
        List<String> selectedOptionIds,
        String selectedValue,
        Map<String, String> blankAnswers,
        Map<String, String> matchingAnswers,
        String textAnswer
) {
    public static final String SCHEMA_VERSION = "learner-answer-v1";

    public LearnerAnswer {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
        blankAnswers = immutable(blankAnswers);
        matchingAnswers = immutable(matchingAnswers);
    }

    private static Map<String, String> immutable(Map<String, String> values) {
        return values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }
}
