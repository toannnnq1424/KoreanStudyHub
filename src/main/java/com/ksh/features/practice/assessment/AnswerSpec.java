package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerSpec(
        String schemaVersion,
        CanonicalQuestionType questionType,
        List<String> correctOptionIds,
        String correctValue,
        List<BlankAnswer> blanks,
        Map<String, String> matchingPairs,
        ScoringPolicyCode scoringPolicyCode,
        String scoringProfileCode,
        String promptProfileCode,
        String rubricProfileCode,
        Integer scoringProfileVersion,
        Integer promptProfileVersion,
        Integer rubricProfileVersion
) {
    public static final String SCHEMA_VERSION = "answer-spec-v1";

    public AnswerSpec {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        correctOptionIds = correctOptionIds == null ? List.of() : List.copyOf(correctOptionIds);
        blanks = blanks == null ? List.of() : List.copyOf(blanks);
        matchingPairs = matchingPairs == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(matchingPairs));
    }

    public AnswerSpec(String schemaVersion,
                      CanonicalQuestionType questionType,
                      List<String> correctOptionIds,
                      String correctValue,
                      List<BlankAnswer> blanks,
                      Map<String, String> matchingPairs,
                      ScoringPolicyCode scoringPolicyCode,
                      String scoringProfileCode,
                      String promptProfileCode,
                      String rubricProfileCode) {
        this(schemaVersion, questionType, correctOptionIds, correctValue, blanks, matchingPairs,
                scoringPolicyCode, scoringProfileCode, promptProfileCode, rubricProfileCode,
                null, null, null);
    }

    public record BlankAnswer(String blankId, List<String> acceptedValues) {
        public BlankAnswer {
            acceptedValues = acceptedValues == null ? List.of() : List.copyOf(acceptedValues);
        }
    }
}
