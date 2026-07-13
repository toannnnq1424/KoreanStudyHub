package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerSpec(
        String schemaVersion,
        CanonicalQuestionType questionType,
        List<String> correctOptionIds,
        String correctValue,
        List<BlankAnswer> blanks,
        ScoringPolicyCode scoringPolicyCode
) {
    public static final String SCHEMA_VERSION = "answer-spec-v1";

    public AnswerSpec {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        correctOptionIds = correctOptionIds == null ? List.of() : List.copyOf(correctOptionIds);
        blanks = blanks == null ? List.of() : List.copyOf(blanks);
    }

    public record BlankAnswer(String blankId, List<String> acceptedValues) {
        public BlankAnswer {
            acceptedValues = acceptedValues == null ? List.of() : List.copyOf(acceptedValues);
        }
    }
}
