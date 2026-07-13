package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningExplanationClient;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningMockExplanationService;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.ExplanationLearnerOverlay;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.repository.QuestionExplanationCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingListeningTypedExplanationTest {

    private QuestionExplanationCacheRepository repository;
    private ReadingListeningExplanationClient client;
    private ReadingListeningMockExplanationService fallback;
    private OpenAiProperties properties;
    private ReadingListeningExplanationService service;

    @BeforeEach
    void setUp() {
        repository = mock(QuestionExplanationCacheRepository.class);
        client = mock(ReadingListeningExplanationClient.class);
        fallback = mock(ReadingListeningMockExplanationService.class);
        properties = mock(OpenAiProperties.class);
        service = new ReadingListeningExplanationService(
                repository, client, fallback, properties, new ObjectMapper());
        when(client.model()).thenReturn("model-a");
        when(client.promptVersion()).thenReturn("prompt-v3");
        when(client.schemaVersion()).thenReturn("schema-v2");
        when(client.explanationLanguage()).thenReturn("vi");
        when(repository.findByCacheKey(anyString())).thenReturn(Optional.empty());
        when(fallback.explain(any(ExplanationContext.class), anyString()))
                .thenReturn("""
                        {"meaningVi":"limited","evidenceQuote":"","correctReasonVi":"limited",\
                        "relatedTranslationVi":"","eliminatedOptions":[],"evidenceStatus":"UNAVAILABLE"}
                        """);
    }

    @Test
    void listeningWithoutApprovedTranscriptReturnsLimitedResultWithoutProviderOrCache() {
        ExplanationContext context = context(
                100L,
                AssessmentStimulus.listeningAudio("audio-ref", null, "TEACHER_UPLOAD", false),
                answerSpec("opt_1"),
                learner("opt_2"),
                "vi");

        String result = service.getOrCreateExplanation(context, 7L);

        assertThat(result).contains("UNAVAILABLE");
        verify(client, never()).explain(any(ExplanationContext.class));
        verify(repository, never()).upsertVersioned(
                anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cacheIdentityChangesWithVersionStimulusAnswerModelSchemaAndLanguage() {
        ExplanationContext base = context(
                100L,
                AssessmentStimulus.readingPassage("본문", "TEACHER"),
                answerSpec("opt_1"),
                learner("opt_2"),
                "vi");
        String baseKey = service.buildCacheKeyParts(base).base().cacheKey();

        assertThat(service.buildCacheKeyParts(context(
                101L, base.stimulus(), base.answerSpec(), base.learnerAnswer(), "vi")).base().cacheKey())
                .isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(context(
                100L, AssessmentStimulus.readingPassage("다른 본문", "TEACHER"),
                base.answerSpec(), base.learnerAnswer(), "vi")).base().cacheKey())
                .isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(context(
                100L, base.stimulus(), answerSpec("opt_2"), base.learnerAnswer(), "vi")).base().cacheKey())
                .isNotEqualTo(baseKey);

        when(client.model()).thenReturn("model-b");
        assertThat(service.buildCacheKeyParts(base).base().cacheKey()).isNotEqualTo(baseKey);
        when(client.model()).thenReturn("model-a");
        when(client.schemaVersion()).thenReturn("schema-v3");
        assertThat(service.buildCacheKeyParts(base).base().cacheKey()).isNotEqualTo(baseKey);
        when(client.schemaVersion()).thenReturn("schema-v2");
        assertThat(service.buildCacheKeyParts(context(
                100L, base.stimulus(), base.answerSpec(), base.learnerAnswer(), "en")).base().cacheKey())
                .isNotEqualTo(baseKey);
    }

    @Test
    void malformedProviderOutputIsNotCachedAndLearnerOverlayRemainsDeterministic() {
        ExplanationContext context = context(
                100L,
                AssessmentStimulus.readingPassage("본문", "TEACHER"),
                answerSpec("opt_1"),
                learner("opt_2"),
                "vi");
        when(properties.apiKey()).thenReturn("key");
        when(client.explain(context)).thenReturn("{\"meaningVi\":\"missing required fields\"}");

        String result = service.getOrCreateExplanation(context, 7L);
        ExplanationLearnerOverlay overlay = service.learnerOverlay(context, BigDecimal.valueOf(4));

        assertThat(result).contains("limited");
        assertThat(overlay.earnedPoints()).isEqualByComparingTo("0");
        assertThat(overlay.correctUnits()).isZero();
        assertThat(overlay.totalUnits()).isEqualTo(1);
        verify(repository, never()).upsertVersioned(
                anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void fallbackConvertsStableOptionIdsToLearnerFacingLabels() throws Exception {
        String correctId = "44d0-802d-correct";
        String wrongId = "413a-b5b1-wrong";
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option(correctId, "한국어를 공부합니다."),
                        new QuestionContent.Option(wrongId, "운동을 합니다.")),
                List.of());
        ExplanationContext context = new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                51L,
                100L,
                1,
                AssessmentSkill.READING,
                CanonicalQuestionType.SINGLE_CHOICE,
                "민수는 아침에 무엇을 합니까?",
                content,
                answerSpec(correctId),
                learner(wrongId),
                AssessmentStimulus.readingPassage("민수는 아침에 한국어를 공부합니다.", "TEACHER"),
                "Đáp án A.",
                "vi",
                "ALPHA");
        when(properties.apiKey()).thenReturn("key");
        when(client.explain(context)).thenReturn(null);
        when(fallback.explain(context, "provider-unavailable")).thenReturn("""
                {"meaningVi":"limited","evidenceQuote":"evidence","correctReasonVi":"reason",\
                "relatedTranslationVi":"translation","eliminatedOptions":[\
                {"optionKey":"413a-b5b1-wrong","reasonVi":"Không đúng ngữ cảnh."}]}
                """);

        String result = service.getOrCreateExplanation(context, 7L);
        JsonNode root = new ObjectMapper().readTree(result);

        assertThat(root.path("correctAnswer").asText()).isEqualTo("A");
        assertThat(root.path("eliminatedOptions").path(0).path("optionKey").asText())
                .isEqualTo("B");
        assertThat(result).doesNotContain(correctId, wrongId);
    }

    private ExplanationContext context(Long questionVersionId,
                                       AssessmentStimulus stimulus,
                                       AnswerSpec answerSpec,
                                       LearnerAnswer learnerAnswer,
                                       String language) {
        return new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                51L,
                questionVersionId,
                1,
                stimulus.type() == AssessmentStimulus.StimulusType.LISTENING_AUDIO
                        ? AssessmentSkill.LISTENING
                        : AssessmentSkill.READING,
                CanonicalQuestionType.SINGLE_CHOICE,
                "질문",
                content(),
                answerSpec,
                learnerAnswer,
                stimulus,
                "teacher",
                language,
                "NUMERIC");
    }

    private QuestionContent content() {
        return new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("opt_1", "A"),
                        new QuestionContent.Option("opt_2", "B")),
                List.of());
    }

    private AnswerSpec answerSpec(String correctId) {
        return new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of(correctId),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
    }

    private LearnerAnswer learner(String selectedId) {
        return new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of(selectedId),
                null,
                Map.of(),
                null);
    }
}
