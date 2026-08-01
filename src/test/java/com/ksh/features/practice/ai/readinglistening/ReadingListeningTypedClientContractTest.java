package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import com.ksh.features.practice.ai.transport.TestPracticeStructuredGenerationPort;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingListeningTypedClientContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void typedProviderPayloadExcludesLearnerAnswerAndLocksLecturerStrategy() {
        ReadingListeningExplanationClient client = client();

        String payload = ReflectionTestUtils.invokeMethod(
                client, "userPayload", listeningTfngContext(), List.of());

        assertThat(payload)
                .contains(
                        "answerSpec",
                        "evidenceText",
                        "\"evidenceSourceRole\":\"TRANSCRIPT\"",
                        "\"transcriptEvidenceScope\":\"LINGUISTIC_CONTENT_ONLY\"",
                        "\"strategyCode\":\"CLAIM_EVIDENCE_RELATION\"")
                .doesNotContain(
                        "learnerAnswer",
                        "selectedOptionIds",
                        "private-audio-reference",
                        "evidenceProvenance");
    }

    @Test
    void v4ImageEvidenceRequiresDigestIndexAndAuthoritativeRegion()
            throws Exception {
        ReadingListeningExplanationClient client = client();
        ExplanationContext context = singleChoiceContext(
                ObjectiveExplanationStrategyRegistry.Code.EVIDENCE_ONLY);
        ExplanationImageEvidence image = new ExplanationImageEvidence(
                "QUESTION",
                new AiImageEvidence(
                        7L,
                        "image/png",
                        "data:image/png;base64,cG5n",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        3));
        String payload = ReflectionTestUtils.invokeMethod(
                client, "userPayload", context, List.of(image));

        String cleaned = client.cleanAndValidateJson(
                evidenceOnlyJson(
                        context,
                        "[]",
                        """
                        [{"evidenceId":"img","kind":"IMAGE_REGION",
                          "purpose":"ANSWER_RATIONALE","sourceRole":"QUESTION",
                          "assetDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                          "imageIndex":0,"regionMode":"WHOLE_IMAGE",
                          "x":null,"y":null,"width":null,"height":null}]
                        """,
                        "img"),
                context,
                List.of(image));

        JsonNode root = objectMapper.readTree(cleaned);
        assertThat(root.path("schemaVersion").asText()).isEqualTo("v4");
        assertThat(payload)
                .contains("\"imageIndex\":0", "QUESTION", "aaaaaaaa")
                .doesNotContain("data:image/png", "assetId", "mimeType",
                        "sizeBytes");

        String incompleteRegion = evidenceOnlyJson(
                context,
                "[]",
                """
                [{"evidenceId":"img","kind":"IMAGE_REGION",
                  "purpose":"ANSWER_RATIONALE","sourceRole":"QUESTION",
                  "assetDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "imageIndex":0,"regionMode":"RECTANGLE",
                  "x":null,"y":null,"width":null,"height":null}]
                """,
                "img");
        assertThat(client.cleanAndValidateJson(
                incompleteRegion, context, List.of(image))).isNull();
    }

    @Test
    void responseSchemaUsesStrictVersionedStrategyDiscriminator() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext eliminate = singleChoiceContext(
                ObjectiveExplanationStrategyRegistry.Code
                        .ELIMINATE_ALL_INCORRECT);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseFormat = ReflectionTestUtils.invokeMethod(
                client, "responseFormat", eliminate, List.of());
        String serialized = objectMapper.valueToTree(responseFormat).toString();

        assertThat(client.promptVersion())
                .isEqualTo("v9-objective-lecturer-strategy");
        assertThat(client.schemaVersion()).isEqualTo("v4");
        assertThat(serialized)
                .contains(
                        "\"additionalProperties\":false",
                        "\"strategyRegistryVersion\"",
                        "\"const\":\"ELIMINATE_ALL_INCORRECT\"",
                        "\"strategyVersion\"",
                        "\"optionRationales\"",
                        "\"textEvidenceRefs\"")
                .doesNotContain("\"oneOf\"", "\"blankExplanations\"");
    }

    @Test
    void evidenceOnlyAcceptsGroundedClaimsAndRejectsFreeTextOrWrongStrategy() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext context = singleChoiceContext(
                ObjectiveExplanationStrategyRegistry.Code.EVIDENCE_ONLY);
        String valid = evidenceOnlyJson(
                context, exactReadingEvidence(), "[]", "e1");

        assertThat(client.cleanAndValidateJson(
                valid, context, List.of())).isNotNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "\"strategyBlock\":",
                        "\"meaningVi\":\"khen chung chung\",\"strategyBlock\":"),
                context,
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "\"strategyCode\":\"EVIDENCE_ONLY\"",
                        "\"strategyCode\":\"HYBRID\""),
                context,
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace("[\"e1\"]", "[]"),
                context,
                List.of())).isNull();
    }

    @Test
    void eliminateStrategyRequiresEveryStableOptionExactlyOnce() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext context = singleChoiceContext(
                ObjectiveExplanationStrategyRegistry.Code
                        .ELIMINATE_ALL_INCORRECT);
        String valid = eliminateJson(context);

        assertThat(client.cleanAndValidateJson(
                valid, context, List.of())).isNotNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "\"optionId\":\"opt_3\"",
                        "\"optionId\":\"foreign\""),
                context,
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace("\"claimId\":\"c2\"", "\"claimId\":\"c1\""),
                context,
                List.of())).isNull();
    }

    @Test
    void fillBlankIsTypedByStableBlankIdAndRejectsAcceptedValueLeakage() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext context = fillBlankContext();
        String valid = wrap(
                context,
                exactReadingEvidence(),
                "[]",
                """
                {"blankExplanations":[{
                  "claimId":"blank-claim-1","blankId":"blank_1",
                  "contextExplanationVi":"Hợp ngữ cảnh",
                  "semanticConstraintVi":"Danh từ chỉ nơi chốn",
                  "grammarConstraintVi":"Vị trí danh từ",
                  "registerConstraintVi":"Trung tính",
                  "evidenceIds":["e1"]}]}
                """);

        assertThat(client.cleanAndValidateJson(
                valid, context, List.of())).isNotNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "\"evidenceIds\":[\"e1\"]",
                        "\"evidenceIds\":[\"e1\"],\"acceptedValues\":[\"bịa\"]"),
                context,
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace("\"blank_1\"", "\"foreign_blank\""),
                context,
                List.of())).isNull();
    }

    @Test
    void tfngRequiresTypedEvidenceLinkedRelationClaims() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext context = listeningTfngContext();
        String valid = wrap(
                context,
                exactTranscriptEvidence(),
                "[]",
                """
                {
                  "claim":{"claimId":"claim","textVi":"Mệnh đề hỏi về thời điểm.","evidenceIds":["e1"]},
                  "whyTrue":{"claimId":"true","textVi":"Nguồn không xác nhận đúng.","evidenceIds":["e1"]},
                  "whyFalse":{"claimId":"false","textVi":"Nguồn không nêu mâu thuẫn.","evidenceIds":["e1"]},
                  "whyNotGiven":{"claimId":"ng","textVi":"Thiếu thời điểm để kết luận.","evidenceIds":["e1"]},
                  "missingInformation":{"claimId":"missing","textVi":"Nguồn không cho biết thời điểm.","evidenceIds":["e1"]}
                }
                """);

        assertThat(client.cleanAndValidateJson(
                valid, context, List.of())).isNotNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "Nguồn không cho biết thời điểm.",
                        ""),
                context,
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace("\"claimId\":\"missing\"", "\"claimId\":\"ng\""),
                context,
                List.of())).isNull();
    }

    @Test
    void currentGenericStrategiesUseTypeNativeFillAndTfngPayloads() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext fill = fillBlankContext(
                ObjectiveExplanationStrategyRegistry.Code
                        .KEYWORD_PARAPHRASE_BRIDGE,
                ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION);
        String fillJson = wrap(
                fill,
                exactReadingEvidence(),
                "[]",
                """
                {"blankExplanations":[{
                  "claimId":"blank-claim-1","blankId":"blank_1",
                  "contextExplanationVi":"Hợp ngữ cảnh",
                  "semanticConstraintVi":"Danh từ chỉ nơi chốn",
                  "grammarConstraintVi":"Vị trí danh từ",
                  "registerConstraintVi":"Trung tính",
                  "evidenceIds":["e1"]}]}
                """);

        assertThat(client.cleanAndValidateJson(
                fillJson, fill, List.of())).isNotNull();

        ExplanationContext tfng = listeningTfngContext(
                ObjectiveExplanationStrategyRegistry.Code
                        .FULL_SOURCE_INLINE_HIGHLIGHT,
                ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION);
        String tfngJson = wrap(
                tfng,
                exactTranscriptEvidence(),
                "[]",
                """
                {
                  "claim":{"claimId":"claim","textVi":"Mệnh đề hỏi về thời điểm.","evidenceIds":["e1"]},
                  "whyTrue":{"claimId":"true","textVi":"Nguồn không xác nhận đúng.","evidenceIds":["e1"]},
                  "whyFalse":{"claimId":"false","textVi":"Nguồn không nêu mâu thuẫn.","evidenceIds":["e1"]},
                  "whyNotGiven":{"claimId":"ng","textVi":"Thiếu thời điểm để kết luận.","evidenceIds":["e1"]},
                  "missingInformation":{"claimId":"missing","textVi":"Nguồn không cho biết thời điểm.","evidenceIds":["e1"]}
                }
                """);

        assertThat(client.cleanAndValidateJson(
                tfngJson, tfng, List.of())).isNotNull();
    }

    @Test
    void exactOffsetsDuplicateEvidenceIdsAndForeignTranslationsFailClosed() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext context = singleChoiceContext(
                ObjectiveExplanationStrategyRegistry.Code.EVIDENCE_ONLY);
        String valid = evidenceOnlyJson(
                context, exactReadingEvidence(), "[]", "e1");

        assertThat(client.cleanAndValidateJson(
                valid.replace("\"startOffset\":0", "\"startOffset\":1"),
                context,
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                evidenceOnlyJson(
                        context,
                        """
                        [{"evidenceId":"e1","kind":"TEXT_SPAN",
                          "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                          "exactQuoteKo":"본문","startOffset":0,"endOffset":2},
                         {"evidenceId":"e1","kind":"TEXT_SPAN",
                          "purpose":"SUPPORTING","sourceRole":"PASSAGE",
                          "exactQuoteKo":"근거","startOffset":3,"endOffset":5}]
                        """,
                        "[]",
                        "e1"),
                context,
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "\"relevantTranslations\":[]",
                        """
                        "relevantTranslations":[
                          {"evidenceId":"foreign","translationVi":"Bịa"}]
                        """),
                context,
                List.of())).isNull();
    }

    private String evidenceOnlyJson(
            ExplanationContext context,
            String textEvidence,
            String imageEvidence,
            String evidenceId) {
        return wrap(
                context,
                textEvidence,
                imageEvidence,
                """
                {"evidenceClaims":[{
                  "claimId":"claim-1","textVi":"Bằng chứng xác nhận đáp án.",
                  "evidenceIds":["%s"]}]}
                """.formatted(evidenceId));
    }

    private String eliminateJson(ExplanationContext context) {
        return wrap(
                context,
                exactReadingEvidence(),
                "[]",
                """
                {"optionRationales":[
                  {"claimId":"c1","optionId":"opt_1",
                    "reasonVi":"Đúng","evidenceIds":["e1"]},
                  {"claimId":"c2","optionId":"opt_2",
                    "reasonVi":"Sai","evidenceIds":["e1"]},
                  {"claimId":"c3","optionId":"opt_3",
                    "reasonVi":"Sai","evidenceIds":["e1"]}
                ]}
                """);
    }

    private String wrap(
            ExplanationContext context,
            String textEvidence,
            String imageEvidence,
            String strategyBlock) {
        return """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"%s",
                  "strategyCode":"%s",
                  "strategyVersion":"v1",
                  "questionType":"%s",
                  "explanation":{
                    "textEvidenceRefs":%s,
                    "imageEvidenceRefs":%s,
                    "relevantTranslations":[],
                    "strategyBlock":%s
                  }
                }
                """.formatted(
                        context.explanationStrategy().registryVersion(),
                        context.explanationStrategy().strategyCode(),
                        context.questionType().name(),
                        textEvidence,
                        imageEvidence,
                        strategyBlock);
    }

    private static String exactReadingEvidence() {
        return """
                [{"evidenceId":"e1","kind":"TEXT_SPAN",
                  "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                  "exactQuoteKo":"본문","startOffset":0,"endOffset":2}]
                """;
    }

    private static String exactTranscriptEvidence() {
        return """
                [{"evidenceId":"e1","kind":"TRANSCRIPT_SPAN",
                  "purpose":"MISSING_INFORMATION","sourceRole":"TRANSCRIPT",
                  "exactQuoteKo":"승객은","startOffset":0,"endOffset":3}]
                """;
    }

    private ReadingListeningExplanationClient client() {
        return new ReadingListeningExplanationClient(
                TestPracticeStructuredGenerationPort.unavailable(
                        "openai-primary", "model"),
                objectMapper);
    }

    private ExplanationContext singleChoiceContext(
            ObjectiveExplanationStrategyRegistry.Code code) {
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("opt_1", "A"),
                        new QuestionContent.Option("opt_2", "B"),
                        new QuestionContent.Option("opt_3", "C")),
                List.of());
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("opt_1"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        return context(
                CanonicalQuestionType.SINGLE_CHOICE,
                content,
                spec,
                AssessmentStimulus.readingPassage(
                        "본문 근거", "TEACHER"),
                code);
    }

    private ExplanationContext fillBlankContext() {
        return fillBlankContext(
                ObjectiveExplanationStrategyRegistry.Code
                        .CONSTRAINTS_AND_EVIDENCE,
                ObjectiveExplanationStrategyRegistry.REGISTRY_VERSION);
    }

    private ExplanationContext fillBlankContext(
            ObjectiveExplanationStrategyRegistry.Code strategy,
            String registryVersion) {
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(new QuestionContent.Blank(
                        "blank_1", "서울은 ___입니다.")));
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer(
                        "blank_1", List.of("도시"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        return context(
                CanonicalQuestionType.FILL_BLANK,
                content,
                spec,
                AssessmentStimulus.readingPassage(
                        "본문 근거", "TEACHER"),
                strategy,
                registryVersion);
    }

    private ExplanationContext listeningTfngContext() {
        return listeningTfngContext(
                ObjectiveExplanationStrategyRegistry.Code
                        .CLAIM_EVIDENCE_RELATION,
                ObjectiveExplanationStrategyRegistry.REGISTRY_VERSION);
    }

    private ExplanationContext listeningTfngContext(
            ObjectiveExplanationStrategyRegistry.Code strategy,
            String registryVersion) {
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                List.of(),
                "NOT_GIVEN",
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        return context(
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                QuestionContent.empty(),
                spec,
                AssessmentStimulus.listeningAudio(
                        "private-audio-reference",
                        "승객은 서울에 갑니다.",
                        "LECTURER",
                        true),
                strategy,
                registryVersion);
    }

    private ExplanationContext context(
            CanonicalQuestionType type,
            QuestionContent content,
            AnswerSpec spec,
            AssessmentStimulus stimulus,
            ObjectiveExplanationStrategyRegistry.Code strategy) {
        return context(
                type,
                content,
                spec,
                stimulus,
                strategy,
                ObjectiveExplanationStrategyRegistry.REGISTRY_VERSION);
    }

    private ExplanationContext context(
            CanonicalQuestionType type,
            QuestionContent content,
            AnswerSpec spec,
            AssessmentStimulus stimulus,
            ObjectiveExplanationStrategyRegistry.Code strategy,
            String registryVersion) {
        return new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                1L,
                10L,
                1,
                stimulus.type()
                        == AssessmentStimulus.StimulusType.LISTENING_AUDIO
                        ? AssessmentSkill.LISTENING
                        : AssessmentSkill.READING,
                type,
                "질문",
                "그룹 지시문",
                content,
                spec,
                new LearnerAnswer(
                        LearnerAnswer.SCHEMA_VERSION,
                        type,
                        type == CanonicalQuestionType.SINGLE_CHOICE
                                ? List.of("opt_2")
                                : List.of(),
                        null,
                        Map.of(),
                        null),
                stimulus,
                "teacher",
                "vi",
                "NUMERIC",
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        type,
                        registryVersion,
                        strategy.name(),
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION));
    }
}
