package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentContractCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final AssessmentContractCodec codec = new AssessmentContractCodec(
            objectMapper, new QuestionTypeResolver());

    @Test
    void answerSpecsRoundTripForEveryCanonicalType() {
        for (CanonicalQuestionType type : CanonicalQuestionType.values()) {
            QuestionContent content = contentFor(type);
            AnswerSpec expected = answerSpecFor(type);

            String contentJson = codec.writeQuestionContent(content, type);
            QuestionContent decodedContent = codec.readQuestionContent(contentJson, type);
            String answerJson = codec.writeAnswerSpec(expected, decodedContent);

            assertThat(codec.readAnswerSpec(answerJson, decodedContent)).isEqualTo(expected);
            assertThat(answerJson).doesNotContain("answerKey", "profileCode");
        }
    }

    @Test
    void learnerAnswersRoundTripWithoutLegacyShape() {
        LearnerAnswer answer = new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("opt_1"),
                null,
                Map.of(),
                null);

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
                        new QuestionContent.Option("opt_1", "B")),
                List.of());
        assertThatThrownBy(() -> codec.writeQuestionContent(
                duplicateOptions, CanonicalQuestionType.SINGLE_CHOICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate option ID");

        QuestionContent content = contentFor(CanonicalQuestionType.SINGLE_CHOICE);
        AnswerSpec missingOption = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("missing"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        assertThatThrownBy(() -> codec.writeAnswerSpec(missingOption, content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option ID");

        AnswerSpec duplicateNormalizedAliases = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer("blank_1", List.of("서울", "  서울 "))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        assertThatThrownBy(() -> codec.writeAnswerSpec(
                duplicateNormalizedAliases, contentFor(CanonicalQuestionType.FILL_BLANK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate normalized accepted value");
    }

    @Test
    void speakingDeliveryRoundTripsAndRejectsInvalidTiming() {
        QuestionContent content = contentFor(CanonicalQuestionType.SPEAKING);

        String json = codec.writeQuestionContent(content, CanonicalQuestionType.SPEAKING);

        assertThat(codec.readQuestionContent(json, CanonicalQuestionType.SPEAKING))
                .isEqualTo(content);
        assertThat(json).contains("speakingDelivery", "preparationSeconds", "responseSeconds");

        QuestionContent invalid = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(),
                null,
                null,
                new QuestionContent.SpeakingDelivery("/practice/materials/7/content", 0, 30, 60));
        assertThatThrownBy(() -> codec.writeQuestionContent(invalid, CanonicalQuestionType.SPEAKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speaking prompt play limit");
        assertThatThrownBy(() -> codec.writeQuestionContent(content, CanonicalQuestionType.ESSAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only valid for SPEAKING");
    }

    @Test
    void historicalSpeakingContentWithoutDeliveryRemainsReadable() {
        QuestionContent legacy = QuestionContent.empty();

        String json = codec.writeQuestionContent(legacy, CanonicalQuestionType.SPEAKING);

        assertThat(codec.readQuestionContent(json, CanonicalQuestionType.SPEAKING).speakingDelivery())
                .isNull();
    }

    @Test
    void speakingV2RoundTripsOnlyTheThreeLockedLearnerSafeModeCombinations() {
        QuestionContent uploadedAudio = speakingV2(
                QuestionContent.SpeakingPromptInputType.AUDIO_UPLOAD,
                QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                "/practice/materials/7/content",
                QuestionContent.SpeakingAudioOrigin.TEACHER_UPLOAD);
        QuestionContent manualText = speakingV2(
                QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                QuestionContent.SpeakingDeliveryMode.TEXT_ONLY,
                null,
                QuestionContent.SpeakingAudioOrigin.NONE);
        QuestionContent manualTextWithTts = speakingV2(
                QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                QuestionContent.SpeakingDeliveryMode.TEXT_AND_AUDIO,
                "/practice/materials/8/content",
                QuestionContent.SpeakingAudioOrigin.AI_TTS);

        for (QuestionContent content : List.of(uploadedAudio, manualText, manualTextWithTts)) {
            String json = codec.writeQuestionContent(content, CanonicalQuestionType.SPEAKING);

            assertThat(codec.readQuestionContent(json, CanonicalQuestionType.SPEAKING))
                    .isEqualTo(content);
            assertThat(json).contains("\"schemaVersion\":\"question-content-v2\"");
            assertThat(json).doesNotContain(
                    "transcript", "task", "fingerprint", "confidence",
                    "provider", "learnerAnswer", "acoustic");
        }
    }

    @Test
    void speakingV2RejectsCrossModeAudioAndOriginCombinations() {
        QuestionContent invalid = speakingV2(
                QuestionContent.SpeakingPromptInputType.AUDIO_UPLOAD,
                QuestionContent.SpeakingDeliveryMode.TEXT_AND_AUDIO,
                "/practice/materials/7/content",
                QuestionContent.SpeakingAudioOrigin.AI_TTS);

        assertThatThrownBy(() -> codec.writeQuestionContent(
                invalid, CanonicalQuestionType.SPEAKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode combination");
        assertThatThrownBy(() -> codec.writeQuestionContent(
                QuestionContent.speakingV2(null), CanonicalQuestionType.SPEAKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v2 speaking delivery");
        assertThatThrownBy(() -> codec.writeQuestionContent(
                speakingV2(
                        QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                        QuestionContent.SpeakingDeliveryMode.TEXT_ONLY,
                        null,
                        QuestionContent.SpeakingAudioOrigin.NONE),
                CanonicalQuestionType.ESSAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only valid for SPEAKING");
    }

    @Test
    void speakingContentRejectsUnknownInternalFieldsWithBootStyleMapper() {
        String internalMetadata = """
                {
                  "schemaVersion":"question-content-v2",
                  "speakingDelivery":{
                    "inputType":"audio_upload",
                    "deliveryMode":"audio_only",
                    "promptAudioReference":"/practice/materials/7/content",
                    "audioOrigin":"teacher_upload",
                    "promptPlayLimit":1,
                    "preparationSeconds":30,
                    "responseSeconds":60,
                    "transcript":"internal prompt context"
                  }
                }
                """;

        assertThatThrownBy(() -> codec.readQuestionContent(
                internalMetadata, CanonicalQuestionType.SPEAKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported speaking delivery field: transcript");
    }

    @Test
    void speakingV2ModeCodesAreExactLowercaseIdentities() {
        String uppercaseIdentity = """
                {
                  "schemaVersion":"question-content-v2",
                  "speakingDelivery":{
                    "inputType":"AUDIO_UPLOAD",
                    "deliveryMode":"audio_only",
                    "promptAudioReference":"/practice/materials/7/content",
                    "audioOrigin":"teacher_upload",
                    "promptPlayLimit":1,
                    "preparationSeconds":30,
                    "responseSeconds":60
                  }
                }
                """;

        assertThatThrownBy(() -> codec.readQuestionContent(
                uppercaseIdentity, CanonicalQuestionType.SPEAKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid question content JSON");
    }

    @Test
    void v1SpeakingShapeRemainsExactAndCannotBeSilentlyUpgraded() {
        QuestionContent legacy = contentFor(CanonicalQuestionType.SPEAKING);

        String json = codec.writeQuestionContent(legacy, CanonicalQuestionType.SPEAKING);

        assertThat(json)
                .contains("\"schemaVersion\":\"question-content-v1\"")
                .doesNotContain("inputType", "deliveryMode", "audioOrigin");
        assertThat(codec.readQuestionContent(json, CanonicalQuestionType.SPEAKING))
                .isEqualTo(legacy);
    }

    @Test
    void playerPayloadSerializationCannotLeakAnswerSpec() throws Exception {
        PlayerQuestionPayload payload = new PlayerQuestionPayload(
                PlayerQuestionPayload.SCHEMA_VERSION,
                42L,
                CanonicalQuestionType.SINGLE_CHOICE,
                "정답을 고르세요.",
                contentFor(CanonicalQuestionType.SINGLE_CHOICE),
                BigDecimal.ONE);

        String json = objectMapper.writeValueAsString(payload);

        assertThat(json)
                .contains("question-content-v1", "opt_1")
                .doesNotContain(
                        "answerSpec", "correctOptionIds", "correctValue", "profileCode",
                        "transcript", "task", "fingerprint", "confidence",
                        "provider", "acousticEvidence");
    }

    @Test
    void learnerSafeRecordShapesCannotAcquireInternalAuthoringMetadata() {
        List<String> forbiddenFragments = List.of(
                "transcript", "task", "fingerprint", "confidence",
                "provider", "artifact", "acoustic");

        for (Class<?> learnerSafeType : List.of(
                QuestionContent.class,
                QuestionContent.SpeakingDelivery.class,
                PlayerQuestionPayload.class)) {
            List<String> componentNames = Arrays.stream(learnerSafeType.getRecordComponents())
                    .map(component -> component.getName().toLowerCase())
                    .toList();
            for (String forbidden : forbiddenFragments) {
                assertThat(componentNames)
                        .noneMatch(name -> name.contains(forbidden));
            }
        }
    }

    @Test
    void legacyMcqAndGapFillAliasesAdaptButRemovedTypesFailClosed() {
        QuestionContent content = codec.adaptLegacyContent("[\"하나\",\"둘\",\"셋\"]", "MCQ");
        AnswerSpec answerSpec = codec.adaptLegacyAnswerSpec("MCQ", "B", content);
        LearnerAnswer learnerAnswer = codec.adaptLegacyLearnerAnswer("SINGLE_CHOICE", "2", content);

        assertThat(content.options()).extracting(QuestionContent.Option::id)
                .containsExactly("opt_1", "opt_2", "opt_3");
        assertThat(answerSpec.correctOptionIds()).containsExactly("opt_2");
        assertThat(learnerAnswer.selectedOptionIds()).containsExactly("opt_2");
        assertThatThrownBy(() -> codec.adaptLegacyContent("[]", "MATCHING_INFORMATION"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported canonical practice question type");
        assertThatThrownBy(() -> codec.adaptLegacyContent("[]", "MCQ_MULTIPLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported canonical practice question type");
    }

    private QuestionContent contentFor(CanonicalQuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(
                            new QuestionContent.Option("opt_1", "하나"),
                            new QuestionContent.Option("opt_2", "둘"),
                            new QuestionContent.Option("opt_3", "셋")),
                    List.of());
            case FILL_BLANK -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(),
                    List.of(new QuestionContent.Blank("blank_1", "도시는 ____입니다.")));
            case TRUE_FALSE_NOT_GIVEN, ESSAY -> QuestionContent.empty();
            case SPEAKING -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    new QuestionContent.SpeakingDelivery(
                            "/practice/materials/7/content", 2, 30, 60));
        };
    }

    private AnswerSpec answerSpecFor(CanonicalQuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of("opt_2"), null,
                    List.of(), ScoringPolicyCode.ALL_OR_NOTHING);
            case TRUE_FALSE_NOT_GIVEN -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of(), "NOT_GIVEN",
                    List.of(), ScoringPolicyCode.ALL_OR_NOTHING);
            case FILL_BLANK -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of(), null,
                    List.of(new AnswerSpec.BlankAnswer("blank_1", List.of("서울", "서울시"))),
                    ScoringPolicyCode.NORMALIZED_EXACT);
            case ESSAY, SPEAKING -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION, type, List.of(), null,
                    List.of(), ScoringPolicyCode.PROFILE_BASED);
        };
    }

    private static QuestionContent speakingV2(
            QuestionContent.SpeakingPromptInputType inputType,
            QuestionContent.SpeakingDeliveryMode deliveryMode,
            String promptAudioReference,
            QuestionContent.SpeakingAudioOrigin audioOrigin) {
        return QuestionContent.speakingV2(new QuestionContent.SpeakingDelivery(
                inputType,
                deliveryMode,
                promptAudioReference,
                audioOrigin,
                deliveryMode == QuestionContent.SpeakingDeliveryMode.TEXT_ONLY
                        ? null : 1,
                30,
                60));
    }
}
