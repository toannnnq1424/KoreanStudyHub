package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ksh.entities.WritingTaskType;

import java.util.List;

/**
 * Task-native response contract for Korean Writing Q51/Q52.
 *
 * <p>This contract is deliberately separate from the objective
 * {@link CanonicalQuestionType#FILL_BLANK} contract. Q51/Q52 remain Writing
 * ESSAY-family questions with profile-based scoring, while their learner
 * response is collected as stable, independently addressable blanks.</p>
 */
public final class WritingBlankContract {

    public static final String RESPONSE_SCHEMA_VERSION = "writing-blanks.v1";
    public static final String AUTHORITY_SCHEMA_VERSION =
            "writing-blank-authority.v1";
    public static final String LEARNER_SCHEMA_VERSION =
            "writing-blank-response.v1";
    public static final String EVALUATION_SCHEMA_VERSION =
            "writing-blank-evaluation.v1";
    public static final String RESPONSE_MODE = "STRUCTURED_BLANKS";
    public static final String AUTHORING_MODE_LEGACY_READ_ONLY =
            "LEGACY_ESSAY_READ_ONLY";
    public static final String NORMALIZATION = "NFC";
    public static final String WHITESPACE_POLICY = "TRIM_COLLAPSE";

    private WritingBlankContract() {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record QuestionResponse(
            String responseSchemaVersion,
            String responseMode,
            WritingTaskType taskType,
            List<BlankDefinition> blanks
    ) {
        public QuestionResponse {
            blanks = blanks == null ? List.of() : List.copyOf(blanks);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BlankDefinition(
            String blankId,
            Integer ordinal,
            String context
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AnswerAuthority(
            String contractVersion,
            WritingTaskType taskType,
            String normalization,
            String whitespacePolicy,
            List<BlankAuthority> blanks
    ) {
        public AnswerAuthority {
            blanks = blanks == null ? List.of() : List.copyOf(blanks);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BlankAuthority(
            String blankId,
            Integer ordinal,
            List<AcceptedAnswer> acceptedAnswers
    ) {
        public BlankAuthority {
            acceptedAnswers = acceptedAnswers == null
                    ? List.of()
                    : List.copyOf(acceptedAnswers);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AcceptedAnswer(
            String text,
            Equivalence equivalence,
            String reason,
            List<String> evidenceIds
    ) {
        public AcceptedAnswer {
            evidenceIds = evidenceIds == null
                    ? List.of()
                    : List.copyOf(evidenceIds);
        }
    }

    public enum Equivalence {
        EXACT,
        SEMANTIC_EQUIVALENT
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LearnerResponse(
            String contractVersion,
            WritingTaskType taskType,
            String responseMode,
            List<LearnerBlankAnswer> answers
    ) {
        public LearnerResponse {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LearnerBlankAnswer(
            String blankId,
            String text
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Evaluation(
            String contractVersion,
            WritingTaskType taskType,
            List<BlankEvaluation> blanks
    ) {
        public Evaluation {
            blanks = blanks == null ? List.of() : List.copyOf(blanks);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BlankEvaluation(
            String blankId,
            Integer ordinal,
            Verdict verdict,
            List<String> evidenceIds,
            List<String> findingIds,
            String correction,
            String scoreImpact
    ) {
        public BlankEvaluation {
            evidenceIds = evidenceIds == null
                    ? List.of()
                    : List.copyOf(evidenceIds);
            findingIds = findingIds == null
                    ? List.of()
                    : List.copyOf(findingIds);
        }
    }

    public enum Verdict {
        CORRECT,
        PARTIAL,
        INCORRECT,
        EMPTY,
        REVIEW_REQUIRED
    }
}
