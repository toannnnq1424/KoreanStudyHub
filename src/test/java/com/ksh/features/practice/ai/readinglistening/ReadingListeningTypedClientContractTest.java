package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadingListeningTypedClientContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void typedProviderPayloadExcludesLearnerAnswerAndMarksTranscriptEvidenceLimit() {
        ReadingListeningExplanationClient client = client();

        String payload = ReflectionTestUtils.invokeMethod(
                client, "userPayload", listeningTfngContext(), List.of());

        assertThat(payload)
                .contains(
                        "answerSpec",
                        "evidenceText",
                        "\"evidenceSourceRole\":\"TRANSCRIPT\"",
                        "\"transcriptEvidenceScope\":\"LINGUISTIC_CONTENT_ONLY\"")
                .doesNotContain(
                        "learnerAnswer",
                        "selectedOptionIds",
                        "private-audio-reference",
                        "evidenceProvenance");
    }

    @Test
    void v3ImageEvidenceRequiresDigestIndexAndRegionFromAuthorizedImage() throws Exception {
        ReadingListeningExplanationClient client = client();
        ExplanationImageEvidence image = new ExplanationImageEvidence(
                "QUESTION",
                new AiImageEvidence(
                        7L,
                        "image/png",
                        "data:image/png;base64,cG5n",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        3));
        String payload = ReflectionTestUtils.invokeMethod(
                client, "userPayload", singleChoiceContext(), List.of(image));

        String cleaned = client.cleanAndValidateJson(
                singleChoiceJson("""
                        [{"evidenceId":"img","kind":"IMAGE_REGION",
                          "purpose":"ANSWER_RATIONALE","sourceRole":"QUESTION",
                          "assetDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                          "imageIndex":0,"regionMode":"WHOLE_IMAGE",
                          "x":null,"y":null,"width":null,"height":null}]
                        """, "[\"img\"]"),
                singleChoiceContext(),
                List.of(image));

        JsonNode root = objectMapper.readTree(cleaned);
        assertThat(root.path("schemaVersion").asText()).isEqualTo("v3");
        assertThat(root.path("questionType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(payload)
                .contains("\"imageIndex\":0", "QUESTION", "aaaaaaaa")
                .doesNotContain("data:image/png", "assetId", "mimeType", "sizeBytes");

        String missingRegion = singleChoiceJson("""
                [{"evidenceId":"img","kind":"IMAGE_REGION",
                  "purpose":"ANSWER_RATIONALE","sourceRole":"QUESTION",
                  "assetDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "imageIndex":0,"regionMode":"RECTANGLE",
                  "x":null,"y":null,"width":null,"height":null}]
                """, "[\"img\"]");
        assertThat(client.cleanAndValidateJson(
                missingRegion, singleChoiceContext(), List.of(image))).isNull();
        assertThat(client.cleanAndValidateJson(
                singleChoiceJson("[]", "[]"),
                singleChoiceContext(),
                List.of())).isNull();
    }

    @Test
    void promptAndResponseSchemaAreVersionedForTypeNativeObjectiveGeneration() {
        ReadingListeningExplanationClient client = client();
        @SuppressWarnings("unchecked")
        Map<String, Object> responseFormat = ReflectionTestUtils.invokeMethod(
                client, "responseFormat", singleChoiceContext(), List.of());
        String serialized = objectMapper.valueToTree(responseFormat).toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> fillResponseFormat = ReflectionTestUtils.invokeMethod(
                client, "responseFormat", fillBlankContext(), List.of());
        String fillSerialized = objectMapper.valueToTree(fillResponseFormat).toString();

        assertThat(client.promptVersion()).isEqualTo("v8-objective-type-native");
        assertThat(client.schemaVersion()).isEqualTo("v3");
        assertThat(serialized)
                .contains(
                        "\"type\":\"object\"",
                        "\"name\":\"rl_answer_explanation_single_choice\"",
                        "\"questionType\"",
                        "\"const\":\"SINGLE_CHOICE\"",
                        "\"optionRationales\"",
                        "\"textEvidenceRefs\"",
                        "\"imageEvidenceRefs\"",
                        "\"relevantTranslations\"")
                .doesNotContain("\"oneOf\"", "\"blankExplanations\"", "\"pageIndex\"");
        assertThat(fillSerialized)
                .contains("\"const\":\"FILL_BLANK\"", "\"blankExplanations\"")
                .doesNotContain("\"oneOf\"", "\"optionRationales\"", "\"pageIndex\"");
    }

    @Test
    void singleChoiceRequiresOneRationaleForEveryStableOptionAndRejectsCrossTypeFields() {
        ReadingListeningExplanationClient client = client();

        assertThat(client.cleanAndValidateJson(
                singleChoiceJson(exactTextEvidence(), "[\"e1\"]"),
                singleChoiceContext(),
                List.of())).isNotNull();
        assertThat(client.cleanAndValidateJson("""
                {"schemaVersion":"v3","questionType":"SINGLE_CHOICE","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "textEvidenceRefs":[],"imageEvidenceRefs":[],"relevantTranslations":[],
                  "blankExplanations":[]}}
                """, singleChoiceContext(), List.of())).isNull();
        assertThat(client.cleanAndValidateJson("""
                {"schemaVersion":"v3","questionType":"SINGLE_CHOICE","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "textEvidenceRefs":[],"imageEvidenceRefs":[],"relevantTranslations":[],
                  "optionRationales":[
                    {"optionId":"opt_1","reasonVi":"Đúng","evidenceIds":[]},
                    {"optionId":"opt_2","reasonVi":"Sai","evidenceIds":[]}]}}
                """, singleChoiceContext(), List.of())).isNull();
    }

    @Test
    void fillBlankSchemaCannotReturnAcceptedValuesOrOptionEliminations() {
        ReadingListeningExplanationClient client = client();
        String valid = """
                {"schemaVersion":"v3","questionType":"FILL_BLANK","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "textEvidenceRefs":%s,"imageEvidenceRefs":[],
                  "relevantTranslations":[],
                  "blankExplanations":[{
                    "blankId":"blank_1","contextExplanationVi":"Hợp ngữ cảnh",
                    "semanticConstraintVi":"Danh từ chỉ nơi chốn",
                    "grammarConstraintVi":"Vị trí danh từ",
                    "registerConstraintVi":"Trung tính","evidenceIds":["e1"]}]}}
                """.formatted(exactTextEvidence());

        assertThat(client.cleanAndValidateJson(
                valid, fillBlankContext(), List.of())).isNotNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "\"evidenceIds\":[\"e1\"]",
                        "\"evidenceIds\":[\"e1\"],\"acceptedValues\":[\"bịa\"]"),
                fillBlankContext(),
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                valid.replace(
                        "\"blankExplanations\"",
                        "\"eliminatedOptions\""),
                fillBlankContext(),
                List.of())).isNull();
    }

    @Test
    void tfngUsesBackendRelationAndRequiresMissingInformationForNotGiven() {
        ReadingListeningExplanationClient client = client();
        String missing = tfngJson("");

        assertThat(client.cleanAndValidateJson(
                missing, listeningTfngContext(), List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                tfngJson("Nguồn không cho biết thời điểm."),
                listeningTfngContext(),
                List.of())).isNotNull();
    }

    @Test
    void fabricatedOrNonExactTextEvidenceAndFreeTextImageMarkerAreRejected() {
        ReadingListeningExplanationClient client = client();
        String nonExactEvidence = """
                [{"evidenceId":"e1","kind":"TEXT_SPAN","purpose":"ANSWER_RATIONALE",
                  "sourceRole":"PASSAGE","exactQuoteKo":"없는 인용",
                  "startOffset":0,"endOffset":5}]
                """;
        String imageMarker = """
                [{"evidenceId":"e1","kind":"TEXT_SPAN","purpose":"ANSWER_RATIONALE",
                  "sourceRole":"PASSAGE","exactQuoteKo":"[IMAGE]",
                  "startOffset":0,"endOffset":7}]
                """;

        assertThat(client.cleanAndValidateJson(
                singleChoiceJson(nonExactEvidence, "[\"e1\"]"),
                singleChoiceContext(),
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                singleChoiceJson(imageMarker, "[\"e1\"]"),
                singleChoiceContext(),
                List.of())).isNull();
    }

    @Test
    void duplicateEvidenceIdsAndForeignOrDuplicateTranslationsAreRejected() {
        ReadingListeningExplanationClient client = client();
        String duplicateEvidence = """
                [{"evidenceId":"e1","kind":"TEXT_SPAN","purpose":"ANSWER_RATIONALE",
                  "sourceRole":"PASSAGE","exactQuoteKo":"본문","startOffset":0,"endOffset":2},
                 {"evidenceId":"e1","kind":"TEXT_SPAN","purpose":"OPTION_ELIMINATION",
                  "sourceRole":"PASSAGE","exactQuoteKo":"근거","startOffset":3,"endOffset":5}]
                """;

        assertThat(client.cleanAndValidateJson(
                singleChoiceJson(duplicateEvidence, "[\"e1\"]"),
                singleChoiceContext(),
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                singleChoiceJson(
                        exactTextEvidence(),
                        "[\"e1\"]",
                        "[{\"evidenceId\":\"e1\",\"translationVi\":\"Đoạn văn chính\"}]"),
                singleChoiceContext(),
                List.of())).isNotNull();
        assertThat(client.cleanAndValidateJson(
                singleChoiceJson(
                        exactTextEvidence(),
                        "[\"e1\"]",
                        """
                        [{"evidenceId":"e1","translationVi":"Bản dịch một"},
                         {"evidenceId":"e1","translationVi":"Bản dịch hai"}]
                        """),
                singleChoiceContext(),
                List.of())).isNull();
        assertThat(client.cleanAndValidateJson(
                singleChoiceJson(
                        exactTextEvidence(),
                        "[\"e1\"]",
                        "[{\"evidenceId\":\"foreign\",\"translationVi\":\"Bản dịch\"}]"),
                singleChoiceContext(),
                List.of())).isNull();
    }

    private String singleChoiceJson(String evidenceRefs, String evidenceIds) {
        return singleChoiceJson(evidenceRefs, evidenceIds, "[]");
    }

    private String singleChoiceJson(
            String evidenceRefs,
            String evidenceIds,
            String relevantTranslations) {
        boolean imageEvidence = evidenceRefs.contains("\"IMAGE_REGION\"");
        return """
                {"schemaVersion":"v3","questionType":"SINGLE_CHOICE","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "textEvidenceRefs":%s,"imageEvidenceRefs":%s,
                  "relevantTranslations":%s,
                  "optionRationales":[
                    {"optionId":"opt_1","reasonVi":"Đúng","evidenceIds":%s},
                    {"optionId":"opt_2","reasonVi":"Sai","evidenceIds":%s},
                    {"optionId":"opt_3","reasonVi":"Sai","evidenceIds":%s}]}}
                """.formatted(
                        imageEvidence ? "[]" : evidenceRefs,
                        imageEvidence ? evidenceRefs : "[]",
                        relevantTranslations,
                        evidenceIds,
                        evidenceIds,
                        evidenceIds);
    }

    private String exactTextEvidence() {
        return """
                [{"evidenceId":"e1","kind":"TEXT_SPAN","purpose":"ANSWER_RATIONALE",
                  "sourceRole":"PASSAGE","exactQuoteKo":"본문",
                  "startOffset":0,"endOffset":2}]
                """;
    }

    private String tfngJson(String missingInformation) {
        return """
                {"schemaVersion":"v3","questionType":"TRUE_FALSE_NOT_GIVEN","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "textEvidenceRefs":[],"imageEvidenceRefs":[],"relevantTranslations":[],
                  "relationExplanationVi":"Nguồn không nêu đủ thông tin.",
                  "whyTrueVi":"Không thể kết luận đúng.",
                  "whyFalseVi":"Không có mâu thuẫn trực tiếp.",
                  "whyNotGivenVi":"Đây là quan hệ không được nêu.",
                  "missingInformationVi":"%s"}}
                """.formatted(missingInformation);
    }

    private ReadingListeningExplanationClient client() {
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.baseUrl()).thenReturn("http://localhost");
        when(properties.apiKey()).thenReturn("");
        when(properties.evaluatorModel()).thenReturn("model");
        return new ReadingListeningExplanationClient(properties, objectMapper);
    }

    private ExplanationContext singleChoiceContext() {
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
                AssessmentStimulus.readingPassage("본문 근거", "TEACHER"));
    }

    private ExplanationContext fillBlankContext() {
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(new QuestionContent.Blank("blank_1", "서울은 ___입니다.")));
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer("blank_1", List.of("도시"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        return context(
                CanonicalQuestionType.FILL_BLANK,
                content,
                spec,
                AssessmentStimulus.readingPassage("본문", "TEACHER"));
    }

    private ExplanationContext listeningTfngContext() {
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
                        true));
    }

    private ExplanationContext context(
            CanonicalQuestionType type,
            QuestionContent content,
            AnswerSpec spec,
            AssessmentStimulus stimulus) {
        return new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                1L,
                10L,
                1,
                stimulus.type() == AssessmentStimulus.StimulusType.LISTENING_AUDIO
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
                "NUMERIC");
    }
}
