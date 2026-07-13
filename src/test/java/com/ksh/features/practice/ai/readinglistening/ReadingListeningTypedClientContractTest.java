package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.OpenAiProperties;
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
    void typedProviderPayloadExcludesLearnerAnswerAndMediaReference() {
        ReadingListeningExplanationClient client = client();
        ExplanationContext context = singleChoiceContext();

        String payload = ReflectionTestUtils.invokeMethod(client, "typedUserPayload", context);

        assertThat(payload)
                .contains("answerSpec", "evidenceText")
                .doesNotContain("learnerAnswer", "selectedOptionIds", "private-audio-reference");
    }

    @Test
    void singleChoiceCleanerKeepsOnlyKnownWrongStableOptionIds() throws Exception {
        ReadingListeningExplanationClient client = client();
        String cleaned = client.cleanAndValidateJson("""
                {"meaningVi":"m","evidenceQuote":"본문","correctReasonVi":"r",\
                "relatedTranslationVi":"t","eliminatedOptions":[
                  {"optionKey":"opt_1","reasonVi":"correct must be removed"},
                  {"optionKey":"opt_2","reasonVi":"wrong"},
                  {"optionKey":"unknown","reasonVi":"unknown"}
                ]}
                """, singleChoiceContext());

        JsonNode root = objectMapper.readTree(cleaned);
        assertThat(root.path("eliminatedOptions").size()).isEqualTo(1);
        assertThat(root.path("eliminatedOptions").path(0).path("optionKey").asText())
                .isEqualTo("opt_2");
    }

    @Test
    void fillBlankCleanerRejectsFabricatedOptionEliminations() throws Exception {
        ReadingListeningExplanationClient client = client();
        String providerJson = """
                {"meaningVi":"m","evidenceQuote":"본문","correctReasonVi":"r",\
                "relatedTranslationVi":"t","eliminatedOptions":[
                  {"optionKey":"invented","reasonVi":"fabricated"}
                ]}
                """;

        JsonNode fill = objectMapper.readTree(client.cleanAndValidateJson(
                providerJson, fillBlankContext()));

        assertThat(fill.path("eliminatedOptions").size()).isZero();
    }

    @Test
    void fabricatedOrBlankEvidenceIsRejected() {
        ReadingListeningExplanationClient client = client();

        assertThat(client.cleanAndValidateJson("""
                {"meaningVi":"m","evidenceQuote":"없는 인용","correctReasonVi":"r",\
                "relatedTranslationVi":"t","eliminatedOptions":[]}
                """, singleChoiceContext())).isNull();
        assertThat(client.cleanAndValidateJson("""
                {"meaningVi":"","evidenceQuote":"본문","correctReasonVi":"r",\
                "relatedTranslationVi":"t","eliminatedOptions":[]}
                """, singleChoiceContext())).isNull();
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
                AnswerSpec.SCHEMA_VERSION, CanonicalQuestionType.SINGLE_CHOICE,
                List.of("opt_1"), null, List.of(), ScoringPolicyCode.ALL_OR_NOTHING);
        return context(CanonicalQuestionType.SINGLE_CHOICE, content, spec);
    }

    private ExplanationContext fillBlankContext() {
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(new QuestionContent.Blank("blank_1", "서울은 ___입니다.")));
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION, CanonicalQuestionType.FILL_BLANK,
                List.of(), null,
                List.of(new AnswerSpec.BlankAnswer("blank_1", List.of("도시"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        return context(CanonicalQuestionType.FILL_BLANK, content, spec);
    }

    private ExplanationContext context(CanonicalQuestionType type,
                                       QuestionContent content,
                                       AnswerSpec spec) {
        return new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                1L, 10L, 1, AssessmentSkill.READING, type, "질문",
                content, spec,
                new LearnerAnswer(LearnerAnswer.SCHEMA_VERSION, type,
                        type == CanonicalQuestionType.SINGLE_CHOICE ? List.of("opt_2") : List.of(),
                        null, Map.of(), null),
                AssessmentStimulus.readingPassage("본문", "TEACHER"),
                "teacher", "vi", "NUMERIC");
    }
}
