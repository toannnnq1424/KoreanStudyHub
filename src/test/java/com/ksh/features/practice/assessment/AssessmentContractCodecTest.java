package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentContractCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AssessmentContractCodec codec = new AssessmentContractCodec(
            objectMapper,
            new QuestionTypeResolver()
    );

    @Test
    void answerSpecsRoundTripForEveryCanonicalType() {
        for (CanonicalQuestionType type : CanonicalQuestionType.values()) {
            QuestionContent content = contentFor(type);
            AnswerSpec expected = answerSpecFor(type);

            String contentJson = codec.writeQuestionContent(content, type);
            QuestionContent decodedContent = codec.readQuestionContent(contentJson, type);
            String answerJson = codec.writeAnswerSpec(expected, decodedContent);

            assertThat(codec.readAnswerSpec(answerJson, decodedContent)).isEqualTo(expected);
            assertThat(answerJson).doesNotContain("answerKey");
        }
    }

    @Test
    void learnerAnswersRoundTripWithoutLegacyShape() {
        LearnerAnswer answer = new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.MULTIPLE_CHOICE,
                List.of("opt_1", "opt_3"),
                null,
                Map.of(),
                Map.of(),
                null
        );

        String json = codec.writeLearnerAnswer(answer);

        assertThat(codec.readLearnerAnswer(json)).isEqualTo(answer);
        assertThat(json).contains("selectedOptionIds").doesNotContain("answerKey");
    }

    @Test
    void malformedDuplicateAndMissingIdsAreRejected() {
        QuestionContent duplicateOptions = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("opt_1", "A"),
                        new QuestionContent.Option("opt_1", "B")
                ),
                List.of(), List.of(), List.of()
        );
        assertThatThrownBy(() -> codec.writeQuestionContent(
                duplicateOptions,
                CanonicalQuestionType.SINGLE_CHOICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate option ID");

        QuestionContent content = contentFor(CanonicalQuestionType.SINGLE_CHOICE);
        AnswerSpec missingOption = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("missing"),
                null,
                List.of(),
                Map.of(),
                ScoringPolicyCode.ALL_OR_NOTHING,
                null, null, null
        );
        assertThatThrownBy(() -> codec.writeAnswerSpec(missingOption, content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option ID");

        AnswerSpec duplicateNormalizedAliases = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer("blank_1", List.of("서울", "  서울 "))),
                Map.of(),
                ScoringPolicyCode.NORMALIZED_EXACT,
                null, null, null
        );
        assertThatThrownBy(() -> codec.writeAnswerSpec(
                duplicateNormalizedAliases,
                contentFor(CanonicalQuestionType.FILL_BLANK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate normalized accepted value");
    }

    @Test
    void playerPayloadSerializationCannotLeakAnswerSpec() throws Exception {
        PlayerQuestionPayload payload = new PlayerQuestionPayload(
                PlayerQuestionPayload.SCHEMA_VERSION,
                42L,
                CanonicalQuestionType.SINGLE_CHOICE,
                "정답을 고르세요.",
                contentFor(CanonicalQuestionType.SINGLE_CHOICE),
                BigDecimal.ONE
        );

        String json = objectMapper.writeValueAsString(payload);

        assertThat(json)
                .contains("question-content-v1", "opt_1")
                .doesNotContain("answerSpec", "correctOptionIds", "correctValue", "scoringProfileCode");
    }

    @Test
    void legacyMcqOptionsKeysAndAnswersAdaptToStableIds() {
        QuestionContent content = codec.adaptLegacyContent("[\"하나\",\"둘\",\"셋\"]", "MCQ");
        AnswerSpec answerSpec = codec.adaptLegacyAnswerSpec("MCQ", "B", content);
        LearnerAnswer learnerAnswer = codec.adaptLegacyLearnerAnswer("SINGLE_CHOICE", "2", content);

        assertThat(content.options()).extracting(QuestionContent.Option::id)
                .containsExactly("opt_1", "opt_2", "opt_3");
        assertThat(answerSpec.correctOptionIds()).containsExactly("opt_2");
        assertThat(learnerAnswer.selectedOptionIds()).containsExactly("opt_2");
        assertThat(codec.writeAnswerSpec(answerSpec, content)).doesNotContain("answerKey");
    }

    @Test
    void ambiguousLegacyMatchingStringsFailClosed() {
        QuestionContent content = contentFor(CanonicalQuestionType.MATCHING);

        assertThatThrownBy(() -> codec.adaptLegacyAnswerSpec(
                "MATCHING_INFORMATION",
                "1-A,2-B",
                content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be converted safely");
    }

    private QuestionContent contentFor(CanonicalQuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(
                            new QuestionContent.Option("opt_1", "하나"),
                            new QuestionContent.Option("opt_2", "둘"),
                            new QuestionContent.Option("opt_3", "셋")
                    ),
                    List.of(), List.of(), List.of());
            case TRUE_FALSE_NOT_GIVEN, ESSAY, SPEAKING -> QuestionContent.empty();
            case FILL_BLANK -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(), List.of(), List.of(),
                    List.of(new QuestionContent.Blank("blank_1", "도시는 ____입니다.")));
            case MATCHING -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(),
                    List.of(
                            new QuestionContent.Item("left_1", "문단 1"),
                            new QuestionContent.Item("left_2", "문단 2")
                    ),
                    List.of(
                            new QuestionContent.Item("right_a", "제목 A"),
                            new QuestionContent.Item("right_b", "제목 B")
                    ),
                    List.of());
        };
    }

    private AnswerSpec answerSpecFor(CanonicalQuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of("opt_2"), null,
                    List.of(), Map.of(), ScoringPolicyCode.ALL_OR_NOTHING,
                    null, null, null);
            case MULTIPLE_CHOICE -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of("opt_1", "opt_3"), null,
                    List.of(), Map.of(), ScoringPolicyCode.PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO,
                    null, null, null);
            case TRUE_FALSE_NOT_GIVEN -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of(), "NOT_GIVEN",
                    List.of(), Map.of(), ScoringPolicyCode.ALL_OR_NOTHING,
                    null, null, null);
            case FILL_BLANK -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of(), null,
                    List.of(new AnswerSpec.BlankAnswer("blank_1", List.of("서울", "서울시"))),
                    Map.of(), ScoringPolicyCode.NORMALIZED_EXACT,
                    null, null, null);
            case MATCHING -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of(), null,
                    List.of(), Map.of("left_1", "right_a", "left_2", "right_b"),
                    ScoringPolicyCode.PER_PAIR, null, null, null);
            case ESSAY, SPEAKING -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of(), null,
                    List.of(), Map.of(), ScoringPolicyCode.PROFILE_BASED,
                    type == CanonicalQuestionType.ESSAY ? "TOPIK_WRITING_Q54_V1" : "TOPIK_SPEAKING_V1",
                    type == CanonicalQuestionType.ESSAY ? "TOPIK_WRITING_PROMPT_V1" : "TOPIK_SPEAKING_PROMPT_V1",
                    type == CanonicalQuestionType.ESSAY ? "TOPIK_WRITING_RUBRIC_V1" : "TOPIK_SPEAKING_RUBRIC_V1");
        };
    }
}
