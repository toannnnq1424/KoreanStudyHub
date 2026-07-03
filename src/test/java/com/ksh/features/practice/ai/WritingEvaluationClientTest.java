package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WritingEvaluationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingRuleEngine ruleEngine = new WritingRuleEngine();
    private final WritingEvaluationNormalizer normalizer = new WritingEvaluationNormalizer(objectMapper);

    private static final Long USER_ID = 42L;
    private static final String MOCK_JSON_TEMPLATE = "{" +
            "\"task_type\":\"Q53\"," +
            "\"student_text\":\"%s\"," +
            "\"summary\":\"[MOCK_EVALUATION] Mocked fallback\"," +
            "\"rubric_scores\":[" +
            "  {\"name\":\"Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)\",\"score\":4.0,\"feedback\":\"\"}," +
            "  {\"name\":\"Cấu trúc & Bố cục đoạn văn (글의 전개 구조)\",\"score\":4.0,\"feedback\":\"\"}," +
            "  {\"name\":\"Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)\",\"score\":4.0,\"feedback\":\"\"}" +
            "]," +
            "\"strengths\":[]," +
            "\"needs_improvement\":[]," +
            "\"upgraded_answer\":\"\"," +
            "\"upgraded_answer_annotated\":\"\"," +
            "\"sample_answer\":\"\"," +
            "\"sentence_rewrites\":[]" +
            "}";

    @Test
    void testEmptyInputIsDefinitelyInvalid() {
        var analysis = new WritingRuleEngine.RuleAnalysis("Q53", 0, "글자 수: 0자.", List.of());
        assertTrue(WritingEvaluationClient.isDefinitelyInvalid(null, analysis));
        assertTrue(WritingEvaluationClient.isDefinitelyInvalid("", analysis));
        assertTrue(WritingEvaluationClient.isDefinitelyInvalid("   ", analysis));
    }

    @Test
    void testNoHangulIsDefinitelyInvalid() {
        var analysis = new WritingRuleEngine.RuleAnalysis("Q53", 10, "글자 수: 10자.", List.of());
        assertTrue(WritingEvaluationClient.isDefinitelyInvalid("asdfasdf", analysis));
        assertTrue(WritingEvaluationClient.isDefinitelyInvalid("hello world 123", analysis));
    }

    @Test
    void testValidKoreanNotInvalid() {
        var analysis = new WritingRuleEngine.RuleAnalysis("Q53", 10, "OK: 글자 수 250자.", List.of());
        assertFalse(WritingEvaluationClient.isDefinitelyInvalid("한국어를 공부합니다", analysis));
    }

    @Test
    void testQ51_52ShortAnswerNotInvalid() {
        var analysis = new WritingRuleEngine.RuleAnalysis("Q51_52", 3, "글자 수: 3자.", List.of());
        assertFalse(WritingEvaluationClient.isDefinitelyInvalid("있다", analysis));
    }

    @Test
    void testSameInputProducesSameCacheKey() {
        String key1 = WritingEvaluationCacheService.key("prompt", "answer", "Q53", "model", "v2.0", "v2.0", "v2.0");
        String key2 = WritingEvaluationCacheService.key("prompt", "answer", "Q53", "model", "v2.0", "v2.0", "v2.0");
        assertEquals(key1, key2);
    }

    @Test
    void testDifferentRubricVersionCacheMiss() {
        String key1 = WritingEvaluationCacheService.key("p", "a", "Q53", "m", "v2.0", "v1.0", "v2.0");
        String key2 = WritingEvaluationCacheService.key("p", "a", "Q53", "m", "v2.0", "v2.0", "v2.0");
        assertNotEquals(key1, key2);
    }

    @Test
    void testUnifiedPromptContainsAllSections() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q53", false);
        assertTrue(prompt.contains("RUBRIC SCORES"));
        assertTrue(prompt.contains("STRENGTHS & NEEDS"));
        assertTrue(prompt.contains("KHÔNG trả score, raw_score, raw_score_max"));
    }

    @Test
    void testUnifiedPromptAuditMode() {
        String promptNormal = WritingPromptRules.buildUnifiedPrompt("Q53", false);
        String promptAudit = WritingPromptRules.buildUnifiedPrompt("Q53", true);
        assertFalse(promptNormal.contains("AUDIT MODE"));
        assertTrue(promptAudit.contains("AUDIT MODE"));
    }

    @Test
    void testVersionConstantsExist() {
        assertNotNull(WritingPromptRules.PROMPT_VERSION);
        assertNotNull(WritingPromptRules.RUBRIC_VERSION);
        assertNotNull(WritingPromptRules.EVALUATION_SCHEMA_VERSION);
    }

    @Test
    void testCacheHitBeforeApiKeyAndRehydratesStudentText() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        String cachedValue = "{\"score\":8.0,\"raw_score\":24.0,\"raw_score_max\":30.0,\"strengths\":[],\"needs_improvement\":[],\"sentence_rewrites\":[],\"engine\":\"KSH_WRITING_EVALUATOR_V2\"}";
        when(cacheService.get(eq(USER_ID), anyString(), anyString(), anyString(), eq("model"), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(cachedValue));

        OpenAiProperties properties = properties("", "model");
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, setupMockRestClient("{}", new AtomicInteger())
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertTrue(result.contains("\"student_text\":\"한국어\""));
        assertTrue(result.contains("\"score\":8.0"));
        verifyNoInteractions(mockEvaluator);
    }

    @Test
    void testApiKeyEmptyCacheMissUsesMockAndDoesNotPersist() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        OpenAiProperties properties = properties("", "model");
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, "한국어"));
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, null
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertNotNull(result);
        verify(mockEvaluator, times(1)).evaluate(any(), any(), any(), any());
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testProviderExceptionReturnsFallbackAndDoesNotPersist() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        OpenAiProperties properties = properties("valid-key", "model");
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, "한국어"));
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, throwingRestClient()
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertNotNull(result);
        verify(mockEvaluator, times(1)).evaluate(any(), any(), any(), any());
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testAiSuccessPersistsSanitizedResult() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        AtomicInteger callCount = new AtomicInteger(0);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class), setupMockRestClient(aiResponse(), callCount)
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어를 공부합니다", false);

        assertNotNull(result);
        assertEquals(1, callCount.get());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(cacheService).put(eq(USER_ID), anyString(), anyString(), eq("Q53"), eq("model"),
                eq("v2.0"), eq("v2.0"), eq("v2.0"), payload.capture());
        JsonNode cached = objectMapper.readTree(payload.getValue());
        assertFalse(cached.has("student_text"));
        assertEquals("KSH_WRITING_EVALUATOR_V2", cached.path("engine").asText());
    }

    @Test
    void testReEvaluateBypassesReadAndOverwritesOnSuccess() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        AtomicInteger callCount = new AtomicInteger(0);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class), setupMockRestClient(aiResponse(), callCount)
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어를 공부합니다", true);

        assertNotNull(result);
        assertEquals(1, callCount.get());
        verify(cacheService, never()).get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(cacheService).put(eq(USER_ID), anyString(), anyString(), eq("Q53"), eq("model"),
                eq("v2.0"), eq("v2.0"), eq("v2.0"), anyString());
    }

    @Test
    void testReEvaluateFailureDoesNotOverwriteOldCache() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, "한국어"));
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, throwingRestClient()
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", true);

        assertTrue(result.contains("\"score\":4.0"));
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testReadFailureTreatsAsMissAndProviderStillRuns() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db read down"));
        AtomicInteger callCount = new AtomicInteger(0);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class), setupMockRestClient(aiResponse(), callCount)
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertNotNull(result);
        assertEquals(1, callCount.get());
    }

    @Test
    void testWriteFailureReturnsValidProviderResult() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("db write down")).when(cacheService)
                .put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class), setupMockRestClient(aiResponse(), new AtomicInteger())
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertTrue(result.contains("\"engine\":\"KSH_WRITING_EVALUATOR_V2\""));
    }

    @Test
    void testMalformedCachedJsonIgnoredDeletedAndProviderTried() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("{malformed"));
        AtomicInteger callCount = new AtomicInteger(0);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class), setupMockRestClient(aiResponse(), callCount)
        );

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertNotNull(result);
        assertEquals(1, callCount.get());
        verify(cacheService).delete(eq(USER_ID), anyString(), anyString(), eq("Q53"), eq("model"),
                eq("v2.0"), eq("v2.0"), eq("v2.0"));
    }

    @Test
    void testOldOverloadBypassesPersistentCacheScope() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(isNull(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, "한국어"));
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("", "model"), objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, null
        );

        String result = client.evaluate("Bài 53 viết", "한국어", false);

        assertNotNull(result);
        verify(cacheService).get(isNull(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private OpenAiProperties properties(String apiKey, String model) {
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn(model);
        when(properties.apiKey()).thenReturn(apiKey);
        when(properties.baseUrl()).thenReturn("http://localhost");
        return properties;
    }

    private String aiResponse() {
        return """
        {
          "summary": "Good",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 7.0, "feedback": "Good"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 7.0, "feedback": "Good"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 7.0, "feedback": "Good"}
          ],
          "strengths": [],
          "needs_improvement": [],
          "upgraded_answer": "",
          "upgraded_answer_annotated": "",
          "sample_answer": "",
          "sentence_rewrites": []
        }
        """;
    }

    private RestClient throwingRestClient() {
        return new RestClient() {
            @Override public RequestBodyUriSpec post() { throw new RuntimeException("API connection error"); }
            @Override public RequestHeadersUriSpec<?> get() { return null; }
            @Override public RequestHeadersUriSpec<?> head() { return null; }
            @Override public RequestBodyUriSpec put() { return null; }
            @Override public RequestBodyUriSpec patch() { return null; }
            @Override public RequestHeadersUriSpec<?> delete() { return null; }
            @Override public RequestHeadersUriSpec<?> options() { return null; }
            @Override public RequestBodyUriSpec method(org.springframework.http.HttpMethod m) { return null; }
            @Override public Builder mutate() { return null; }
        };
    }

    private RestClient setupMockRestClient(String responseJson, AtomicInteger postCallCount) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(), any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenAnswer(inv -> {
            postCallCount.incrementAndGet();
            try {
                return "{\"choices\":[{\"message\":{\"content\":"
                        + objectMapper.writeValueAsString(responseJson) + "}}]}";
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return restClient;
    }
}
