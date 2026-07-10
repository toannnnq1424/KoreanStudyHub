package com.ksh.features.practice.service;

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
import com.ksh.features.practice.assessment.ProfileReference;
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
                answerSpec(List.of("opt_1")),
                learner(List.of("opt_2")));

        String result = service.getOrCreateExplanation(context, 7L);

        assertThat(result).contains("UNAVAILABLE");
        verify(client, never()).explain(any(ExplanationContext.class));
        verify(repository, never()).upsertVersioned(
                anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    @Test
    void cacheIdentityChangesWithVersionStimulusAnswerProfileModelSchemaAndLanguage() {
        ExplanationContext base = context(
                100L,
                AssessmentStimulus.readingPassage("본문", "TEACHER"),
                answerSpec(List.of("opt_1")),
                learner(List.of("opt_2")));
        String baseKey = service.buildCacheKeyParts(base).base().cacheKey();

        assertThat(service.buildCacheKeyParts(context(
                101L, base.stimulus(), base.answerSpec(), base.learnerAnswer())).base().cacheKey())
                .isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(context(
                100L, AssessmentStimulus.readingPassage("다른 본문", "TEACHER"),
                base.answerSpec(), base.learnerAnswer())).base().cacheKey())
                .isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(context(
                100L, base.stimulus(), answerSpec(List.of("opt_2")), base.learnerAnswer())).base().cacheKey())
                .isNotEqualTo(baseKey);

        ExplanationContext changedProfile = new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION, 51L, 100L, 1, "TOPIK",
                AssessmentSkill.READING, CanonicalQuestionType.MULTIPLE_CHOICE, "질문",
                content(), base.answerSpec(), base.learnerAnswer(), base.stimulus(), "teacher",
                "vi", "NUMERIC", new ProfileReference("PROMPT_B", 2));
        assertThat(service.buildCacheKeyParts(changedProfile).base().cacheKey()).isNotEqualTo(baseKey);

        when(client.model()).thenReturn("model-b");
        assertThat(service.buildCacheKeyParts(base).base().cacheKey()).isNotEqualTo(baseKey);
        when(client.model()).thenReturn("model-a");
        when(client.schemaVersion()).thenReturn("schema-v3");
        assertThat(service.buildCacheKeyParts(base).base().cacheKey()).isNotEqualTo(baseKey);

        ExplanationContext english = new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION, 51L, 100L, 1, "TOPIK",
                AssessmentSkill.READING, CanonicalQuestionType.MULTIPLE_CHOICE, "질문",
                content(), base.answerSpec(), base.learnerAnswer(), base.stimulus(), "teacher",
                "en", "NUMERIC", new ProfileReference("PROMPT_A", 1));
        assertThat(service.buildCacheKeyParts(english).base().cacheKey()).isNotEqualTo(baseKey);
    }

    @Test
    void malformedProviderOutputIsNotCachedAndLearnerOverlayRemainsDeterministic() {
        ExplanationContext context = context(
                100L,
                AssessmentStimulus.readingPassage("본문", "TEACHER"),
                answerSpec(List.of("opt_1", "opt_3")),
                learner(List.of("opt_1")));
        when(properties.apiKey()).thenReturn("key");
        when(client.explain(context)).thenReturn("{\"meaningVi\":\"missing required fields\"}");

        String result = service.getOrCreateExplanation(context, 7L);
        ExplanationLearnerOverlay overlay = service.learnerOverlay(context, BigDecimal.valueOf(4));

        assertThat(result).contains("limited");
        assertThat(overlay.earnedPoints()).isEqualByComparingTo("2");
        assertThat(overlay.correctUnits()).isEqualTo(1);
        assertThat(overlay.totalUnits()).isEqualTo(2);
        verify(repository, never()).upsertVersioned(
                anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    private ExplanationContext context(Long questionVersionId,
                                       AssessmentStimulus stimulus,
                                       AnswerSpec answerSpec,
                                       LearnerAnswer learnerAnswer) {
        return new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                51L,
                questionVersionId,
                1,
                "TOPIK",
                stimulus.type() == AssessmentStimulus.StimulusType.LISTENING_AUDIO
                        ? AssessmentSkill.LISTENING
                        : AssessmentSkill.READING,
                CanonicalQuestionType.MULTIPLE_CHOICE,
                "질문",
                content(),
                answerSpec,
                learnerAnswer,
                stimulus,
                "teacher",
                "vi",
                "NUMERIC",
                new ProfileReference("PROMPT_A", 1)
        );
    }

    private QuestionContent content() {
        return new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("opt_1", "A"),
                        new QuestionContent.Option("opt_2", "B"),
                        new QuestionContent.Option("opt_3", "C")
                ),
                List.of(), List.of(), List.of());
    }

    private AnswerSpec answerSpec(List<String> correctIds) {
        return new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.MULTIPLE_CHOICE,
                correctIds,
                null,
                List.of(),
                Map.of(),
                ScoringPolicyCode.PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO,
                null, null, null
        );
    }

    private LearnerAnswer learner(List<String> selectedIds) {
        return new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.MULTIPLE_CHOICE,
                selectedIds,
                null,
                Map.of(),
                Map.of(),
                null
        );
    }
}
