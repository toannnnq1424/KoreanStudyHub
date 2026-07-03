package com.ksh.features.practice.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.QuestionExplanationCache;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.ReadingListeningExplanationClient;
import com.ksh.features.practice.ai.ReadingListeningMockExplanationService;
import com.ksh.features.practice.repository.QuestionExplanationCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingListeningExplanationServiceTest {

    private static final String VALID_JSON = """
            {
              "meaningVi": "meaning",
              "evidenceQuote": "quote",
              "correctReasonVi": "reason",
              "relatedTranslationVi": "translation",
              "eliminatedOptions": [
                {"optionKey": "2", "reasonVi": "wrong"}
              ]
            }
            """;

    private QuestionExplanationCacheRepository repository;
    private ReadingListeningExplanationClient client;
    private ReadingListeningMockExplanationService mockExplanationService;
    private OpenAiProperties properties;
    private ReadingListeningExplanationService service;

    @BeforeEach
    void setUp() {
        repository = mock(QuestionExplanationCacheRepository.class);
        client = mock(ReadingListeningExplanationClient.class);
        mockExplanationService = mock(ReadingListeningMockExplanationService.class);
        properties = mock(OpenAiProperties.class);
        service = new ReadingListeningExplanationService(
                repository,
                client,
                mockExplanationService,
                properties,
                new ObjectMapper()
        );

        when(client.model()).thenReturn("model-a");
        when(client.promptVersion()).thenReturn("prompt-v1");
        when(client.schemaVersion()).thenReturn("schema-v1");
        when(client.explanationLanguage()).thenReturn("vi");
        when(properties.apiKey()).thenReturn("key");
        when(mockExplanationService.explain(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("{\"meaningVi\":\"mock\",\"evidenceQuote\":\"\",\"correctReasonVi\":\"\",\"relatedTranslationVi\":\"\",\"eliminatedOptions\":[]}");
    }

    @Test
    void sameContentModelVersionLanguageHitsCacheWithoutProviderCall() {
        PracticeQuestion question = question(51L, "a", "[\"b\"]", "1", "MCQ");
        ReadingListeningExplanationService.CacheKeyParts keyParts =
                service.buildCacheKeyParts(question, "passage", "READING", "NUMERIC");
        when(repository.findByCacheKey(keyParts.cacheKey()))
                .thenReturn(Optional.of(cacheRow(keyParts, question, VALID_JSON)));

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).contains("\"meaningVi\"");
        verify(client, never()).explain(any(), anyString(), anyString(), anyString());
    }

    @Test
    void differentUsersShareCacheBecauseUserIsNotPartOfKey() {
        PracticeQuestion question = question(51L, "a", "[\"b\"]", "1", "MCQ");
        ReadingListeningExplanationService.CacheKeyParts first =
                service.buildCacheKeyParts(question, "passage", "READING", "NUMERIC");
        ReadingListeningExplanationService.CacheKeyParts second =
                service.buildCacheKeyParts(question, "passage", "READING", "NUMERIC");

        assertThat(second.cacheKey()).isEqualTo(first.cacheKey());
    }

    @Test
    void userACreatesCacheAndUserBReusesQuestionLevelEntryWithoutProviderRecall() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        Map<String, QuestionExplanationCache> rows = new HashMap<>();
        when(repository.findByCacheKey(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(rows.get(invocation.getArgument(0, String.class))));
        doAnswer(invocation -> {
            String cacheKey = invocation.getArgument(0, String.class);
            rows.put(cacheKey, new QuestionExplanationCache(
                    cacheKey,
                    question.getId(),
                    invocation.getArgument(2, Long.class),
                    invocation.getArgument(3, String.class),
                    invocation.getArgument(4, String.class),
                    invocation.getArgument(5, String.class),
                    invocation.getArgument(6, String.class),
                    invocation.getArgument(7, String.class),
                    invocation.getArgument(8, String.class),
                    invocation.getArgument(9, String.class),
                    invocation.getArgument(10, String.class),
                    invocation.getArgument(11, String.class)
            ));
            return null;
        }).when(repository).upsert(anyString(), anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String userA = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");
        String userB = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(userA).isEqualTo(VALID_JSON);
        assertThat(userB).isEqualTo(VALID_JSON);
        assertThat(rows).hasSize(1);
        verify(client, times(1)).explain(question, "passage", "READING", "NUMERIC");
    }

    @Test
    void contentAndVersionChangesMissByProducingDifferentCacheKeys() {
        PracticeQuestion base = question(51L, "prompt", "[\"a\",\"b\"]", "1", "MCQ");

        String baseKey = service.buildCacheKeyParts(base, "passage", "READING", "NUMERIC").cacheKey();
        assertThat(service.buildCacheKeyParts(question(51L, "changed", "[\"a\",\"b\"]", "1", "MCQ"),
                "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(question(51L, "prompt", "[\"x\",\"b\"]", "1", "MCQ"),
                "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(question(51L, "prompt", "[\"a\",\"b\"]", "2", "MCQ"),
                "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(base, "changed", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(base, "passage", "LISTENING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(question(51L, "prompt", "[\"a\",\"b\"]", "1", "SHORT_TEXT"),
                "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        assertThat(service.buildCacheKeyParts(base, "passage", "READING", "ALPHA").cacheKey()).isNotEqualTo(baseKey);

        when(client.model()).thenReturn("model-b");
        assertThat(service.buildCacheKeyParts(base, "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        when(client.model()).thenReturn("model-a");
        when(client.promptVersion()).thenReturn("prompt-v2");
        assertThat(service.buildCacheKeyParts(base, "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        when(client.promptVersion()).thenReturn("prompt-v1");
        when(client.schemaVersion()).thenReturn("schema-v2");
        assertThat(service.buildCacheKeyParts(base, "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
        when(client.schemaVersion()).thenReturn("schema-v1");
        when(client.explanationLanguage()).thenReturn("en");
        assertThat(service.buildCacheKeyParts(base, "passage", "READING", "NUMERIC").cacheKey()).isNotEqualTo(baseKey);
    }

    @Test
    void delimiterCollisionTuplesProduceDifferentKeys() {
        PracticeQuestion tupleA = question(51L, "a", "b|c", "1", "MCQ");
        PracticeQuestion tupleB = question(51L, "a|b", "c", "1", "MCQ");

        String keyA = service.buildCacheKeyParts(tupleA, "", "READING", "NUMERIC").cacheKey();
        String keyB = service.buildCacheKeyParts(tupleB, "", "READING", "NUMERIC").cacheKey();

        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    void lineEndingNormalizationKeepsExistingContract() {
        PracticeQuestion crlf = question(51L, " a\r\nb ", "[\"x\"]", "1", "MCQ");
        PracticeQuestion lf = question(51L, "a\nb", "[\"x\"]", "1", "MCQ");

        assertThat(service.buildCacheKeyParts(crlf, " p\rq ", "READING", "NUMERIC").cacheKey())
                .isEqualTo(service.buildCacheKeyParts(lf, "p\nq", "READING", "NUMERIC").cacheKey());
    }

    @Test
    void apiKeyEmptyProviderMissUsesFallbackAndDoesNotPersist() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        when(properties.apiKey()).thenReturn("");
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(null);

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).contains("mock");
        verify(repository, never()).upsert(anyString(), anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void providerSuccessPersistsAndRecreatedServiceHitsSameRepositoryRow() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).isEqualTo(VALID_JSON);
        verify(repository).upsert(anyString(), eq(51L), eq(7L), eq("READING"), eq("MCQ"),
                anyString(), eq("1"), eq(VALID_JSON), eq("model-a"), eq("prompt-v1"), eq("schema-v1"), eq("vi"));
    }

    @Test
    void resultDetailAfterOverviewHitDoesNotCallProviderAgain() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        Map<String, QuestionExplanationCache> rows = new HashMap<>();
        when(repository.findByCacheKey(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(rows.get(invocation.getArgument(0, String.class))));
        doAnswer(invocation -> {
            String cacheKey = invocation.getArgument(0, String.class);
            String questionHash = invocation.getArgument(5, String.class);
            String explanationJson = invocation.getArgument(7, String.class);
            rows.put(cacheKey, new QuestionExplanationCache(
                    cacheKey,
                    question.getId(),
                    invocation.getArgument(2, Long.class),
                    invocation.getArgument(3, String.class),
                    invocation.getArgument(4, String.class),
                    questionHash,
                    invocation.getArgument(6, String.class),
                    explanationJson,
                    invocation.getArgument(8, String.class),
                    invocation.getArgument(9, String.class),
                    invocation.getArgument(10, String.class),
                    invocation.getArgument(11, String.class)
            ));
            return null;
        }).when(repository).upsert(anyString(), anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String overview = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");
        String detail = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");
        String reopenOverview = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(overview).isEqualTo(VALID_JSON);
        assertThat(detail).isEqualTo(VALID_JSON);
        assertThat(reopenOverview).isEqualTo(VALID_JSON);
        assertThat(rows).hasSize(1);
        verify(client, times(1)).explain(question, "passage", "READING", "NUMERIC");
    }

    @Test
    void providerFailureDoesNotPersistFallback() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(null);

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).contains("mock");
        verify(repository, never()).upsert(anyString(), anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cacheReadFailureTreatsAsMissAndContinuesProviderFlow() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        when(repository.findByCacheKey(anyString())).thenThrow(new RuntimeException("db down"));
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).isEqualTo(VALID_JSON);
    }

    @Test
    void cacheReadFailureLogOmitsCacheKeyAndSensitiveExceptionMessage() {
        PracticeQuestion question = question(51L, "PRIVATE_PROMPT_TEXT", "[\"a\"]", "1", "MCQ");
        when(repository.findByCacheKey(anyString()))
                .thenThrow(new RuntimeException("PRIVATE_CACHE_JSON cache key leaked"));
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String logs = captureLogs(ReadingListeningExplanationService.class, () ->
                service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC"));

        assertThat(logs).doesNotContain("PRIVATE_CACHE_JSON");
        assertThat(logs).doesNotContain("PRIVATE_PROMPT_TEXT");
        assertThat(logs).contains("operation=cache-read");
        assertThat(logs).contains("questionId=51");
        assertThat(logs).contains("exception=RuntimeException");
    }

    @Test
    void cacheWriteFailureStillReturnsValidProviderExplanation() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);
        doThrow(new RuntimeException("write failed")).when(repository).upsert(anyString(), anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).isEqualTo(VALID_JSON);
    }

    @Test
    void cacheWriteFailureLogOmitsExplanationJson() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        String privateExplanation = """
                {
                  "meaningVi": "PRIVATE_CACHE_JSON",
                  "evidenceQuote": "quote",
                  "correctReasonVi": "reason",
                  "relatedTranslationVi": "translation",
                  "eliminatedOptions": []
                }
                """;
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(privateExplanation);
        doThrow(new RuntimeException("PRIVATE_CACHE_JSON write failed")).when(repository).upsert(anyString(), anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        String logs = captureLogs(ReadingListeningExplanationService.class, () ->
                service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC"));

        assertThat(logs).doesNotContain("PRIVATE_CACHE_JSON");
        assertThat(logs).contains("operation=cache-write");
        assertThat(logs).contains("questionId=51");
        assertThat(logs).contains("exception=RuntimeException");
    }

    @Test
    void malformedCachedJsonIsDeletedAndProviderRetried() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        ReadingListeningExplanationService.CacheKeyParts keyParts =
                service.buildCacheKeyParts(question, "passage", "READING", "NUMERIC");
        when(repository.findByCacheKey(keyParts.cacheKey()))
                .thenReturn(Optional.of(cacheRow(keyParts, question, "not-json")))
                .thenReturn(Optional.empty());
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).isEqualTo(VALID_JSON);
        verify(repository).deleteByCacheKey(keyParts.cacheKey());
    }

    @Test
    void cacheDeleteFailureStillTreatsMalformedRowAsMiss() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        ReadingListeningExplanationService.CacheKeyParts keyParts =
                service.buildCacheKeyParts(question, "passage", "READING", "NUMERIC");
        when(repository.findByCacheKey(keyParts.cacheKey()))
                .thenReturn(Optional.of(cacheRow(keyParts, question, "not-json")))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("delete failed")).when(repository).deleteByCacheKey(keyParts.cacheKey());
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).isEqualTo(VALID_JSON);
    }

    @Test
    void cacheDeleteFailureLogOmitsCorrectAnswerPromptAndExceptionMessage() {
        PracticeQuestion question = question(51L, "PRIVATE_PROMPT_TEXT", "[\"a\"]", "PRIVATE_CORRECT_ANSWER", "MCQ");
        ReadingListeningExplanationService.CacheKeyParts keyParts =
                service.buildCacheKeyParts(question, "passage", "READING", "NUMERIC");
        when(repository.findByCacheKey(keyParts.cacheKey()))
                .thenReturn(Optional.of(cacheRow(keyParts, question, "not-json")))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("PRIVATE_CACHE_JSON delete failed")).when(repository).deleteByCacheKey(keyParts.cacheKey());
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn(VALID_JSON);

        String logs = captureLogs(ReadingListeningExplanationService.class, () ->
                service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC"));

        assertThat(logs).doesNotContain("PRIVATE_CACHE_JSON");
        assertThat(logs).doesNotContain("PRIVATE_PROMPT_TEXT");
        assertThat(logs).doesNotContain("PRIVATE_CORRECT_ANSWER");
        assertThat(logs).contains("operation=cache-delete");
        assertThat(logs).contains("questionId=51");
        assertThat(logs).contains("exception=RuntimeException");
    }

    @Test
    void malformedProviderJsonFallsBackAndDoesNotPersist() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");
        when(client.explain(question, "passage", "READING", "NUMERIC")).thenReturn("not-json");

        String result = service.getOrCreateExplanation(question, "passage", "READING", 7L, "NUMERIC");

        assertThat(result).contains("mock");
        verify(repository, never()).upsert(anyString(), anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void invalidPayloadContractIsRejected() {
        assertThat(service.isValidExplanationJson("{\"meaningVi\":\"x\"}")).isFalse();
        assertThat(service.isValidExplanationJson("plain error")).isFalse();
        assertThat(service.isValidExplanationJson(VALID_JSON)).isTrue();
    }

    @Test
    void readingAndListeningKeysDoNotCollide() {
        PracticeQuestion question = question(51L, "prompt", "[\"a\"]", "1", "MCQ");

        String reading = service.buildCacheKeyParts(question, "passage", "READING", "NUMERIC").cacheKey();
        String listening = service.buildCacheKeyParts(question, "passage", "LISTENING", "NUMERIC").cacheKey();

        assertThat(reading).isNotEqualTo(listening);
    }

    private static PracticeQuestion question(Long id, String prompt, String optionsJson, String answerKey, String questionType) {
        PracticeQuestion question = new PracticeQuestion(
                10L,
                1,
                questionType,
                prompt,
                optionsJson,
                answerKey,
                "stored explanation",
                BigDecimal.ONE,
                0
        );
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }

    private static QuestionExplanationCache cacheRow(ReadingListeningExplanationService.CacheKeyParts keyParts,
                                                     PracticeQuestion question,
                                                     String explanationJson) {
        return new QuestionExplanationCache(
                keyParts.cacheKey(),
                question.getId(),
                7L,
                "READING",
                question.getQuestionType(),
                keyParts.questionHash(),
                question.getAnswerKey(),
                explanationJson,
                keyParts.model(),
                keyParts.promptVersion(),
                keyParts.schemaVersion(),
                keyParts.language()
        );
    }

    private static String captureLogs(Class<?> loggerClass, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        StringBuilder logs = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            logs.append(event.getFormattedMessage()).append('\n');
        }
        return logs.toString();
    }
}
