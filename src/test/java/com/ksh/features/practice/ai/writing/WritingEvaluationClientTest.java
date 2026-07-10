package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
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
    void deterministicBlankReturnsInvalidRawZeroWithoutProviderCacheOrMock() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        RestClient restClient = mock(RestClient.class);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, restClient
        );

        JsonNode root = objectMapper.readTree(client.evaluate(USER_ID, "Bai 53 viet", "   ", false, WritingTaskType.Q53));

        assertEquals("INVALID_LEARNER_RESPONSE", root.path("evaluation_status").asText());
        assertEquals("BACKEND_RULE", root.path("evaluation_source").asText());
        assertEquals("BLANK_ANSWER", root.path("evaluation_reason").asText());
        assertTrue(root.path("score_available").asBoolean(false));
        assertEquals(0.0, root.path("raw_score").asDouble());
        assertEquals(30.0, root.path("raw_score_max").asDouble());
        assertTrue(root.path("summary").asText().startsWith("[INVALID_LEARNER_RESPONSE]"));
        verifyNoInteractions(restClient, mockEvaluator);
        verify(cacheService, never()).get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void deterministicNoHangulReturnsInvalidRawZeroWithoutProviderCacheOrMock() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        RestClient restClient = mock(RestClient.class);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, restClient
        );

        JsonNode root = objectMapper.readTree(client.evaluate(USER_ID, "Bai 51 viet", "hello world 123", false, WritingTaskType.Q51));

        assertEquals("INVALID_LEARNER_RESPONSE", root.path("evaluation_status").asText());
        assertEquals("BACKEND_RULE", root.path("evaluation_source").asText());
        assertEquals("NO_HANGUL", root.path("evaluation_reason").asText());
        assertTrue(root.path("score_available").asBoolean(false));
        assertEquals(0.0, root.path("raw_score").asDouble());
        assertEquals(10.0, root.path("raw_score_max").asDouble());
        verifyNoInteractions(restClient, mockEvaluator);
        verify(cacheService, never()).get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
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
    void exactQ51AndQ52IdentityCreatesDifferentCacheKeys() {
        String q51 = WritingEvaluationCacheService.key("same", "same", "Q51", "model", "v3.0", "v3.0", "v3.0");
        String q52 = WritingEvaluationCacheService.key("same", "same", "Q52", "model", "v3.0", "v3.0", "v3.0");
        assertNotEquals(q51, q52);
        assertEquals(WritingPromptRules.rubricNamesForTask("Q51"), WritingPromptRules.rubricNamesForTask("Q52"));
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
        assertEquals("v4.1", WritingPromptRules.PROMPT_VERSION);
        assertEquals("v4.1", WritingPromptRules.RUBRIC_VERSION);
        assertEquals("v4.1", WritingPromptRules.EVALUATION_SCHEMA_VERSION);
    }

    @Test
    void providerAllowlistUsesExactTaskAndExcludesLegacyCriteria() {
        List<String> q51 = WritingEvaluationClient.allowedRubric("Q51").stream()
                .map(row -> (String) row.get("criterionId"))
                .toList();
        List<String> q54 = WritingEvaluationClient.allowedRubric("Q54").stream()
                .map(row -> (String) row.get("criterionId"))
                .toList();

        assertTrue(q51.contains("W_CLOZE_CONTEXT_FIT"));
        assertFalse(q51.contains("W_CLEAR_THESIS_OR_MAIN_IDEA"));
        assertFalse(q51.contains("W_WON_GO_JI"));
        assertTrue(q54.contains("W_CLEAR_THESIS_OR_MAIN_IDEA"));
        assertFalse(q54.contains("W_Q53_DATA_FLOW_ISSUES"));
        assertTrue(WritingEvaluationClient.allowedRubric("Q53").stream()
                .allMatch(row -> row.containsKey("category") && row.containsKey("evidenceScopes")));
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
    void cacheHitPreservesScoreStatusReasonAndMarksSourceCache() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        String cachedValue = """
                {
                  "score":8.0,
                  "overall_score":8.0,
                  "raw_score":24.0,
                  "raw_score_max":30.0,
                  "task_type":"Q53",
                  "summary":"Cached provider result",
                  "evaluation_status":"EVALUATED",
                  "evaluation_source":"PROVIDER",
                  "evaluation_reason":"NONE",
                  "score_available":true,
                  "strengths":[],
                  "needs_improvement":[],
                  "sentence_rewrites":[],
                  "engine":"KSH_WRITING_EVALUATOR_V2"
                }
                """;
        when(cacheService.get(eq(USER_ID), anyString(), anyString(), eq("Q53"), eq("model"), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(cachedValue));

        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, setupMockRestClient("{}", new AtomicInteger())
        );

        String learnerAnswer = "\uD55C\uAD6D\uC5B4\uB97C \uACF5\uBD80\uD569\uB2C8\uB2E4";
        JsonNode root = objectMapper.readTree(client.evaluate(USER_ID, "Bai 53 viet", learnerAnswer, false, WritingTaskType.Q53));

        assertEquals(learnerAnswer, root.path("student_text").asText());
        assertEquals(8.0, root.path("score").asDouble());
        assertEquals(24.0, root.path("raw_score").asDouble());
        assertEquals(30.0, root.path("raw_score_max").asDouble());
        assertEquals("EVALUATED", root.path("evaluation_status").asText());
        assertEquals("CACHE", root.path("evaluation_source").asText());
        assertEquals("NONE", root.path("evaluation_reason").asText());
        assertTrue(root.path("score_available").asBoolean(false));
        verifyNoInteractions(mockEvaluator);
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testApiKeyEmptyCacheMissReturnsUnavailableAndDoesNotPersist() throws Exception {
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

        JsonNode root = objectMapper.readTree(result);
        assertEquals("EVALUATION_UNAVAILABLE", root.path("evaluation_status").asText());
        assertEquals("MISSING_API_KEY", root.path("evaluation_reason").asText());
        assertFalse(root.path("score_available").asBoolean(true));
        assertFalse(root.has("raw_score"));
        verifyNoInteractions(mockEvaluator);
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testProviderExceptionReturnsUnavailableAndDoesNotPersist() throws Exception {
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

        JsonNode root = objectMapper.readTree(result);
        assertEquals("EVALUATION_UNAVAILABLE", root.path("evaluation_status").asText());
        assertEquals("PROVIDER_UNEXPECTED_ERROR", root.path("evaluation_reason").asText());
        assertFalse(root.path("score_available").asBoolean(true));
        assertFalse(root.has("raw_score"));
        verifyNoInteractions(mockEvaluator);
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void httpErrorLogOmitsProviderBodyPromptAndLearnerAnswerButKeepsSafeMetadata() {
        String prompt = "PRIVATE_PROMPT_TEXT 쓰기 문제";
        String learnerAnswer = "LEARNER_PRIVATE_ANSWER 한국어";
        String providerBody = "PRIVATE_PROVIDER_RESPONSE " + prompt + " " + learnerAnswer;
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, learnerAnswer));
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("SECRET_API_KEY_VALUE", "safe-model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, httpErrorRestClient(providerBody)
        );

        String logs = captureLogs(WritingEvaluationClient.class, () ->
                client.evaluate(USER_ID, prompt, learnerAnswer, false));

        assertFalse(logs.contains("PRIVATE_PROVIDER_RESPONSE"));
        assertFalse(logs.contains("PRIVATE_PROMPT_TEXT"));
        assertFalse(logs.contains("LEARNER_PRIVATE_ANSWER"));
        assertFalse(logs.contains("SECRET_API_KEY_VALUE"));
        assertTrue(logs.contains("status=400"));
        assertTrue(logs.contains("model=safe-model"));
        assertTrue(logs.contains("taskType="));
    }

    @Test
    void transportFailureLogOmitsExceptionMessageButKeepsSafeMetadata() {
        String prompt = "PRIVATE_PROMPT_TEXT 쓰기 문제";
        String learnerAnswer = "LEARNER_PRIVATE_ANSWER 한국어";
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, learnerAnswer));
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("SECRET_API_KEY_VALUE", "safe-model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, resourceAccessErrorRestClient("PRIVATE_PROVIDER_RESPONSE " + prompt + " " + learnerAnswer)
        );

        String logs = captureLogs(WritingEvaluationClient.class, () ->
                client.evaluate(USER_ID, prompt, learnerAnswer, false));

        assertFalse(logs.contains("PRIVATE_PROVIDER_RESPONSE"));
        assertFalse(logs.contains("PRIVATE_PROMPT_TEXT"));
        assertFalse(logs.contains("LEARNER_PRIVATE_ANSWER"));
        assertFalse(logs.contains("SECRET_API_KEY_VALUE"));
        assertTrue(logs.contains("category=transport"));
        assertTrue(logs.contains("model=safe-model"));
        assertTrue(logs.contains("taskType="));
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
                eq("v4.1"), eq("v4.1"), eq("v4.1:v6.0"), payload.capture());
        JsonNode cached = objectMapper.readTree(payload.getValue());
        assertFalse(cached.has("student_text"));
        assertEquals("KSH_WRITING_EVALUATOR_V2", cached.path("engine").asText());
        assertEquals("EVALUATED", cached.path("evaluation_status").asText());
        assertEquals("PROVIDER", cached.path("evaluation_source").asText());
        assertEquals("NONE", cached.path("evaluation_reason").asText());
        assertTrue(cached.path("score_available").asBoolean(false));
        assertTrue(cached.path("raw_score").isNumber());
        assertTrue(cached.path("raw_score_max").isNumber());
    }

    @Test
    void contractFailureDoesNotPersistCache() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class),
                setupMockRestClient("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}", new AtomicInteger())
        );

        JsonNode root = objectMapper.readTree(client.evaluate(USER_ID, "Bai 53 viet",
                "\uD55C\uAD6D\uC5B4\uB97C \uACF5\uBD80\uD569\uB2C8\uB2E4", false, WritingTaskType.Q53));

        assertEquals("EVALUATION_CONTRACT_FAILED", root.path("evaluation_status").asText());
        assertEquals("PROVIDER_CONTRACT_INVALID", root.path("evaluation_reason").asText());
        assertFalse(root.path("score_available").asBoolean(true));
        assertFalse(root.has("raw_score"));
        verify(cacheService, never()).put(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void explicitMetadataOverridesConflictingPromptOnProviderSuccess() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class), setupMockRestClient(aiResponse(), new AtomicInteger())
        );

        String result = client.evaluate(USER_ID, "Bài 51 điền chỗ trống", "한국어를 공부합니다", false,
                WritingTaskType.Q53);
        JsonNode root = objectMapper.readTree(result);

        assertEquals("Q53", root.path("task_type").asText());
        assertEquals(30.0, root.path("raw_score_max").asDouble());
        verify(cacheService).put(eq(USER_ID), anyString(), anyString(), eq("Q53"), eq("model"),
                eq("v4.1"), eq("v4.1"), eq("v4.1:v6.0"), anyString());
    }

    @Test
    void explicitQ52KeepsIdentityInCacheAndResult() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        String cachedValue = "{\"score\":8.0,\"raw_score\":8.9,\"raw_score_max\":10.0,\"task_type\":\"Q52\",\"strengths\":[],\"needs_improvement\":[],\"sentence_rewrites\":[],\"engine\":\"KSH_WRITING_EVALUATOR_V2\"}";
        when(cacheService.get(eq(USER_ID), anyString(), anyString(), eq("Q52"), eq("model"), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(cachedValue));
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, setupMockRestClient("{}", new AtomicInteger())
        );

        String result = client.evaluate(USER_ID, "Bài 53 biểu đồ", "있다", false, WritingTaskType.Q52);
        JsonNode root = objectMapper.readTree(result);

        assertEquals("Q52", root.path("task_type").asText());
        assertEquals(10.0, root.path("raw_score_max").asDouble());
        verifyNoInteractions(mockEvaluator);
    }

    @Test
    void explicitMetadataControlsSpamShortcutProfile() throws Exception {
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                mock(WritingEvaluationCacheService.class), mock(WritingMockEvaluatorService.class), throwingRestClient()
        );

        String result = client.evaluate(USER_ID, "Bài viết chung", "asdf", false, WritingTaskType.Q54);
        JsonNode root = objectMapper.readTree(result);

        assertEquals("Q54", root.path("task_type").asText());
        assertEquals(50.0, root.path("raw_score_max").asDouble());
    }

    @Test
    void explicitMetadataControlsProviderFailureUnavailableProfile() throws Exception {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, "한국어"));
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mockEvaluator, throwingRestClient()
        );

        String result = client.evaluate(USER_ID, "Bài 53 biểu đồ", "한국어", false, WritingTaskType.Q54);
        JsonNode root = objectMapper.readTree(result);

        assertEquals("Q54", root.path("task_type").asText());
        assertEquals("EVALUATION_UNAVAILABLE", root.path("evaluation_status").asText());
        assertEquals("PROVIDER_UNEXPECTED_ERROR", root.path("evaluation_reason").asText());
        assertFalse(root.path("score_available").asBoolean(true));
        assertFalse(root.has("raw_score"));
        assertFalse(root.has("raw_score_max"));
        verifyNoInteractions(mockEvaluator);
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
                eq("v4.1"), eq("v4.1"), eq("v4.1:v6.0"), anyString());
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

        assertTrue(result.contains("\"evaluation_status\":\"EVALUATION_UNAVAILABLE\""));
        assertTrue(result.contains("\"evaluation_reason\":\"PROVIDER_UNEXPECTED_ERROR\""));
        assertFalse(result.contains("\"raw_score\""));
        verifyNoInteractions(mockEvaluator);
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
                eq("v4.1"), eq("v4.1"), eq("v4.1:v6.0"));
    }

    @Test
    void malformedCacheDeleteFailureDoesNotBlockProviderEvaluation() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("{malformed"));
        doThrow(new RuntimeException("db delete down")).when(cacheService)
                .delete(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        AtomicInteger callCount = new AtomicInteger(0);
        WritingEvaluationClient client = new WritingEvaluationClient(
                properties("valid-key", "model"), objectMapper, normalizer, ruleEngine,
                cacheService, mock(WritingMockEvaluatorService.class), setupMockRestClient(aiResponse(), callCount)
        );

        String result = client.evaluate(USER_ID, "Bai 53 viet",
                "\uD55C\uAD6D\uC5B4\uB97C \uACF5\uBD80\uD569\uB2C8\uB2E4", false, WritingTaskType.Q53);

        assertNotNull(result);
        assertTrue(result.contains("\"engine\":\"KSH_WRITING_EVALUATOR_V2\""));
        assertEquals(1, callCount.get());
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

    @Test
    void cacheHitRecordsNoProviderMetric() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        String cachedValue = "{\"score\":8.0,\"raw_score\":24.0,\"raw_score_max\":30.0,\"strengths\":[],\"needs_improvement\":[],\"sentence_rewrites\":[],\"engine\":\"KSH_WRITING_EVALUATOR_V2\"}";
        when(cacheService.get(eq(USER_ID), anyString(), anyString(), anyString(), eq("model"), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(cachedValue));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WritingEvaluationClient client = clientWithMetrics(
                properties("", "model"), cacheService, mock(WritingMockEvaluatorService.class),
                setupMockRestClient("{}", new AtomicInteger()), registry);

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertNotNull(result);
        assertTrue(registry.find(PracticeAiMetrics.PROVIDER_OPERATIONS).meters().isEmpty());
    }

    @Test
    void missingApiKeyRecordsOneProviderFailureMetric() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        WritingMockEvaluatorService mockEvaluator = mock(WritingMockEvaluatorService.class);
        when(mockEvaluator.evaluate(any(), any(), any(), any())).thenReturn(String.format(MOCK_JSON_TEMPLATE, "한국어"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WritingEvaluationClient client = clientWithMetrics(
                properties("", "model"), cacheService, mockEvaluator, null, registry);

        client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        verifyNoInteractions(mockEvaluator);
        assertEquals(1.0, registry.counter(PracticeAiMetrics.PROVIDER_OPERATIONS,
                "feature", "writing", "outcome", "failure").count());
        assertEquals(1L, registry.timer(PracticeAiMetrics.PROVIDER_DURATION,
                "feature", "writing", "outcome", "failure").count());
    }

    @Test
    void acceptedProviderOutputRecordsOneProviderSuccessMetric() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WritingEvaluationClient client = clientWithMetrics(
                properties("valid-key", "model"), cacheService, mock(WritingMockEvaluatorService.class),
                setupMockRestClient(aiResponse(), new AtomicInteger()), registry);

        String result = client.evaluate(USER_ID, "Bài 53 viết", "한국어를 공부합니다", false);

        assertNotNull(result);
        assertEquals(1.0, registry.counter(PracticeAiMetrics.PROVIDER_OPERATIONS,
                "feature", "writing", "outcome", "success").count());
        assertEquals(1L, registry.timer(PracticeAiMetrics.PROVIDER_DURATION,
                "feature", "writing", "outcome", "success").count());
    }

    @Test
    void malformedCachedJsonRecordsParseMalformedMetric() {
        WritingEvaluationCacheService cacheService = mock(WritingEvaluationCacheService.class);
        when(cacheService.get(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("{malformed"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WritingEvaluationClient client = clientWithMetrics(
                properties("valid-key", "model"), cacheService, mock(WritingMockEvaluatorService.class),
                setupMockRestClient(aiResponse(), new AtomicInteger()), registry);

        client.evaluate(USER_ID, "Bài 53 viết", "한국어", false);

        assertEquals(1.0, registry.counter(PracticeAiMetrics.CACHE_OPERATIONS,
                "cache", "writing", "operation", "parse", "outcome", "malformed").count());
    }

    private OpenAiProperties properties(String apiKey, String model) {
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.evaluatorModel()).thenReturn(model);
        when(properties.apiKey()).thenReturn(apiKey);
        when(properties.baseUrl()).thenReturn("http://localhost");
        return properties;
    }

    private WritingEvaluationClient clientWithMetrics(OpenAiProperties properties,
                                                      WritingEvaluationCacheService cacheService,
                                                      WritingMockEvaluatorService mockEvaluator,
                                                      RestClient restClient,
                                                      SimpleMeterRegistry registry) {
        return new WritingEvaluationClient(
                properties,
                objectMapper,
                normalizer,
                ruleEngine,
                new WritingTaskResolver(),
                cacheService,
                mockEvaluator,
                restClient,
                new PracticeAiMetrics(registry));
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

    private RestClient resourceAccessErrorRestClient(String message) {
        return new RestClient() {
            @Override public RequestBodyUriSpec post() { throw new org.springframework.web.client.ResourceAccessException(message); }
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

    private RestClient httpErrorRestClient(String responseBody) {
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
        when(responseSpec.body(any(Class.class))).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));
        return restClient;
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
