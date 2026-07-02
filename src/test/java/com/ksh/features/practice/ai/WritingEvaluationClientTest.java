package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

    // ---- Spam short-circuit ----

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
    void testQ51_52SingleCharHangulNotInvalid() {
        var analysis = new WritingRuleEngine.RuleAnalysis("Q51_52", 1, "글자 수: 1자.", List.of());
        assertFalse(WritingEvaluationClient.isDefinitelyInvalid("가", analysis));
    }

    // ---- Cache key behavior (via cache service) ----

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
    void testDifferentModelCacheMiss() {
        String key1 = WritingEvaluationCacheService.key("p", "a", "Q53", "gemini-2.5-flash", "v2.0", "v2.0", "v2.0");
        String key2 = WritingEvaluationCacheService.key("p", "a", "Q53", "gpt-4o", "v2.0", "v2.0", "v2.0");
        assertNotEquals(key1, key2);
    }

    // ---- Unified prompt structure ----

    @Test
    void testUnifiedPromptContainsAllSections() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q53", false);
        assertTrue(prompt.contains("RUBRIC SCORES"));
        assertTrue(prompt.contains("STRENGTHS & NEEDS"));
        assertTrue(prompt.contains("BÀI NÂNG CẤP"));
        assertTrue(prompt.contains("9.0 Xuất sắc"));
        assertTrue(prompt.contains("5.0 Đang phát triển"));
        assertTrue(prompt.contains("1.0 Không phản hồi"));
        assertTrue(prompt.contains("FEW-SHOT"));
        assertTrue(prompt.contains("KHÔNG trả score, raw_score, raw_score_max"));
    }

    @Test
    void testUnifiedPromptQ51_52NoEssayStructure() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q51_52", false);
        assertTrue(prompt.contains("hoàn thành câu") || prompt.contains("điền chỗ trống"));
        assertTrue(prompt.contains("Không yêu cầu mở bài"));
    }

    @Test
    void testUnifiedPromptQ54HasArgumentRules() {
        String prompt = WritingPromptRules.buildUnifiedPrompt("Q54", false);
        assertTrue(prompt.contains("NGHỊ LUẬN"));
        assertTrue(prompt.contains("mở-thân-kết"));
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
    void testRubricNamesForTaskQ51_52() {
        List<String> names = WritingPromptRules.rubricNamesForTask("Q51_52");
        assertEquals(3, names.size());
        assertTrue(names.get(0).contains("적절성"));
        assertTrue(names.get(1).contains("문법"));
        assertTrue(names.get(2).contains("자연스러움"));
    }

    // ==================================================
    // BEHAVIORAL TESTS (Section IV requirements)
    // ==================================================

    @Test
    void testBehavioral_1_CacheHitBeforeApiKey() {
        WritingEvaluationCacheService cacheService = new WritingEvaluationCacheService();
        String cachedValue = "{\"score\":8.0,\"student_text\":\"한국어\"}";
        cacheService.put("Bài 53 viết", "한국어", "Q53", "model", "v2.0", "v2.0", "v2.0", cachedValue);

        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn("model");
        when(properties.apiKey()).thenReturn(""); // Empty API Key!

        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        RestClient restClient = setupMockRestClient("{}", new AtomicInteger());

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, restClient
        );

        String result = client.evaluate("Bài 53 viết", "한국어", false);
        assertEquals(cachedValue, result);

        verifyNoInteractions(mockEvaluator);
    }

    @Test
    void testBehavioral_2_ApiKeyEmptyCacheMiss() {
        WritingEvaluationCacheService cacheService = new WritingEvaluationCacheService();
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn("model");
        when(properties.apiKey()).thenReturn(""); // Empty API Key!

        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn("{\"score\":5.0,\"student_text\":\"한국어\"}");

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, null
        );

        String result = client.evaluate("Bài 53 viết", "한국어", false);
        assertNotNull(result);

        verify(mockEvaluator, times(1)).evaluate(any(), any(), any(), any());

        Optional<String> cached = cacheService.get("Bài 53 viết", "한국어", "Q53", "model", "v2.0", "v2.0", "v2.0");
        assertTrue(cached.isEmpty(), "Mock output should not be cached");
    }

    @Test
    void testBehavioral_3_ProviderExceptionNoCacheFallback() {
        WritingEvaluationCacheService cacheService = new WritingEvaluationCacheService();
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn("model");
        when(properties.apiKey()).thenReturn("valid-key");

        RestClient restClient = new RestClient() {
            @Override
            public RequestBodyUriSpec post() {
                throw new RuntimeException("API connection error");
            }
            @Override public RequestHeadersUriSpec<?> get() { return null; }
            @Override public RequestHeadersUriSpec<?> head() { return null; }
            @Override public RequestBodyUriSpec put() { return null; }
            @Override public RequestBodyUriSpec patch() { return null; }
            @Override public RequestHeadersUriSpec<?> delete() { return null; }
            @Override public RequestHeadersUriSpec<?> options() { return null; }
            @Override public RequestBodyUriSpec method(org.springframework.http.HttpMethod m) { return null; }
            @Override public Builder mutate() { return null; }
        };

        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn("{\"score\":3.0,\"student_text\":\"한국어\"}");

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, restClient
        );

        String result = client.evaluate("Bài 53 viết", "한국어", false);
        assertNotNull(result);

        verify(mockEvaluator, times(1)).evaluate(any(), any(), any(), any());

        Optional<String> cached = cacheService.get("Bài 53 viết", "한국어", "Q53", "model", "v2.0", "v2.0", "v2.0");
        assertTrue(cached.isEmpty(), "Fallback output should not be cached");
    }

    @Test
    void testBehavioral_4_ReEvaluateSuccess() {
        WritingEvaluationCacheService cacheService = new WritingEvaluationCacheService();
        String oldResult = "{\"score\":5.0,\"student_text\":\"한국어\"}";
        cacheService.put("Bài 53 viết", "한국어", "Q53", "model", "v2.0", "v2.0", "v2.0", oldResult);

        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn("model");
        when(properties.apiKey()).thenReturn("valid-key");

        AtomicInteger callCount = new AtomicInteger(0);
        RestClient restClient = setupMockRestClient(
                "{\"summary\":\"Good\",\"rubric_scores\":[],\"strengths\":[],\"needs_improvement\":[],\"upgraded_answer\":\"\",\"upgraded_answer_annotated\":\"\",\"sample_answer\":\"\",\"sentence_rewrites\":[]}",
                callCount
        );
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, restClient
        );

        String result = client.evaluate("Bài 53 viết", "한국어", true);
        assertNotNull(result);
        assertNotEquals(oldResult, result);
        assertEquals(1, callCount.get());

        Optional<String> cached = cacheService.get("Bài 53 viết", "한국어", "Q53", "model", "v2.0", "v2.0", "v2.0");
        assertTrue(cached.isPresent());
        assertTrue(cached.get().contains("\"score\":1.0"));
    }

    @Test
    void testBehavioral_5_ReEvaluateProviderException() {
        WritingEvaluationCacheService cacheService = new WritingEvaluationCacheService();
        String oldResult = "{\"score\":7.0,\"student_text\":\"한국어\"}";
        cacheService.put("Bài 53 viết", "한국어", "Q53", "model", "v2.0", "v2.0", "v2.0", oldResult);

        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn("model");
        when(properties.apiKey()).thenReturn("valid-key");

        RestClient restClient = new RestClient() {
            @Override
            public RequestBodyUriSpec post() {
                throw new RuntimeException("API quota exceeded");
            }
            @Override public RequestHeadersUriSpec<?> get() { return null; }
            @Override public RequestHeadersUriSpec<?> head() { return null; }
            @Override public RequestBodyUriSpec put() { return null; }
            @Override public RequestBodyUriSpec patch() { return null; }
            @Override public RequestHeadersUriSpec<?> delete() { return null; }
            @Override public RequestHeadersUriSpec<?> options() { return null; }
            @Override public RequestBodyUriSpec method(org.springframework.http.HttpMethod m) { return null; }
            @Override public Builder mutate() { return null; }
        };

        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        String mockOutput = "{" +
                "\"task_type\":\"GENERAL\"," +
                "\"score\":4.0," +
                "\"overall_score\":4.0," +
                "\"rubric_scores\":[" +
                "  {\"name\":\"Hoàn thành nhiệm vụ & Nội dung (태도 및 내용)\",\"score\":4.0,\"feedback\":\"\"}," +
                "  {\"name\":\"Cấu trúc & Bố cục đoạn văn (구조 및 조직)\",\"score\":4.0,\"feedback\":\"\"}," +
                "  {\"name\":\"Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)\",\"score\":4.0,\"feedback\":\"\"}" +
                "]" +
                "}";
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(mockOutput);

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, restClient
        );

        String result = client.evaluate("Bài 53 viết", "한국어", true);
        assertTrue(result.contains("\"score\":4.0"), "Should return fallback output");

        Optional<String> cached = cacheService.get("Bài 53 viết", "한국어", "Q53", "model", "v2.0", "v2.0", "v2.0");
        assertTrue(cached.isPresent());
        assertEquals(oldResult, cached.get(), "Old cache must be preserved on re-evaluation failure");
    }

    @Test
    void testBehavioral_6_EmptyQ53() {
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.apiKey()).thenReturn("valid-key");

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, new WritingEvaluationCacheService(), null, null
        );

        String result = client.evaluate("Bài 53 viết 200-300자", "", false);
        assertTrue(result.contains("\"score\":0.0"));
        assertTrue(result.contains("\"raw_score\":0.0"));
        assertTrue(result.contains("\"raw_score_max\":30.0"));
        assertTrue(result.contains("[SPAM_DETECTED]"));
    }

    @Test
    void testBehavioral_7_EmptyQ54() {
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.apiKey()).thenReturn("valid-key");

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, new WritingEvaluationCacheService(), null, null
        );

        String result = client.evaluate("Bài 54 nghị luận 600-700자", "", false);
        assertTrue(result.contains("\"score\":0.0"));
        assertTrue(result.contains("\"raw_score\":0.0"));
        assertTrue(result.contains("\"raw_score_max\":50.0"));
        assertTrue(result.contains("[SPAM_DETECTED]"));
    }

    @Test
    void testBehavioral_10_AiSuccessCacheMissAndHit() {
        WritingEvaluationCacheService cacheService = new WritingEvaluationCacheService();
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn("model");
        when(properties.apiKey()).thenReturn("valid-key");

        AtomicInteger callCount = new AtomicInteger(0);
        String aiResponse = "{\"summary\":\"Good\",\"rubric_scores\":[],\"strengths\":[],\"needs_improvement\":[],\"upgraded_answer\":\"\",\"upgraded_answer_annotated\":\"\",\"sample_answer\":\"\",\"sentence_rewrites\":[]}";
        RestClient restClient = setupMockRestClient(aiResponse, callCount);
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);

        WritingEvaluationClient client = new WritingEvaluationClient(
                properties, objectMapper, normalizer, ruleEngine, cacheService, mockEvaluator, restClient
        );

        String result1 = client.evaluate("Bài 53 viết", "한국어", false);
        assertNotNull(result1);
        assertEquals(1, callCount.get());

        String result2 = client.evaluate("Bài 53 viết", "한국어", false);
        assertEquals(result1, result2);
        assertEquals(1, callCount.get()); 
    }

    // ---- Helpers ----

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
