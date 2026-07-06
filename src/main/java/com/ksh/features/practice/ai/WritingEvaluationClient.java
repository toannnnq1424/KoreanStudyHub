package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WritingEvaluationClient {

    private static final Logger log = LoggerFactory.getLogger(WritingEvaluationClient.class);

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final WritingEvaluationNormalizer normalizer;
    private final WritingRuleEngine ruleEngine;
    private final WritingTaskResolver taskResolver;
    private final WritingEvaluationCacheService cacheService;
    private final WritingMockEvaluatorService mockEvaluatorService;
    private final PracticeAiMetrics metrics;

    public WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService mockEvaluatorService) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService, mockEvaluatorService,
                null, PracticeAiMetrics.noop());
    }

    @Autowired
    public WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService mockEvaluatorService,
            PracticeAiMetrics metrics) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService, mockEvaluatorService,
                null, metrics);
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService mockEvaluatorService,
            RestClient restClient) {
        this(properties, objectMapper, normalizer, ruleEngine, new WritingTaskResolver(),
                cacheService, mockEvaluatorService, restClient, PracticeAiMetrics.noop());
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService mockEvaluatorService,
            RestClient restClient) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService,
                mockEvaluatorService, restClient, PracticeAiMetrics.noop());
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService mockEvaluatorService,
            RestClient restClient,
            PracticeAiMetrics metrics) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
        this.ruleEngine = ruleEngine;
        this.taskResolver = taskResolver;
        this.cacheService = cacheService;
        this.mockEvaluatorService = mockEvaluatorService;
        this.metrics = metrics == null ? PracticeAiMetrics.noop() : metrics;
        if (restClient != null) {
            this.restClient = restClient;
        } else {
            this.restClient = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                    .build();
        }
    }

    public String evaluate(String prompt, String learnerAnswer) {
        return evaluate(null, prompt, learnerAnswer, false);
    }

    public String evaluate(String prompt, String learnerAnswer, boolean isReEvaluation) {
        return evaluate(null, prompt, learnerAnswer, isReEvaluation);
    }

    public String evaluate(Long userId, String prompt, String learnerAnswer, boolean isReEvaluation) {
        return evaluate(userId, prompt, learnerAnswer, isReEvaluation, null);
    }

    public String evaluate(Long userId, String prompt, String learnerAnswer, boolean isReEvaluation,
                           WritingTaskType explicitTaskType) {
        String resolvedTaskType = taskResolver.resolve(explicitTaskType, prompt);
        WritingRuleEngine.RuleAnalysis ruleAnalysis = ruleEngine.analyze(prompt, learnerAnswer, resolvedTaskType);
        log.info("KSH writing evaluation started: model={}, taskType={}, charCount={}, violations={}, reEvaluation={}",
                properties.evaluatorModel(), ruleAnalysis.taskType(), ruleAnalysis.characterCount(),
                ruleAnalysis.ruleViolations().size(), isReEvaluation);

        // 1. Deterministic spam short-circuit — task-aware
        if (isDefinitelyInvalid(learnerAnswer, ruleAnalysis)) {
            log.info("KSH writing evaluation deterministic spam short-circuit: taskType={}", ruleAnalysis.taskType());
            return normalizer.spamResponse(ruleAnalysis.taskType(), learnerAnswer);
        }

        // 2. Cache lookup (skip for re-evaluation)
        if (!isReEvaluation) {
            try {
                var cached = cacheService.get(userId, prompt, learnerAnswer,
                        ruleAnalysis.taskType(), properties.evaluatorModel(),
                        WritingPromptRules.PROMPT_VERSION, WritingPromptRules.RUBRIC_VERSION,
                        WritingPromptRules.EVALUATION_SCHEMA_VERSION);
                if (cached.isPresent()) {
                    long parseStart = PracticeAiMetrics.startNanos();
                    try {
                        String rehydrated = normalizer.rehydrateCachedResult(cached.get(), learnerAnswer);
                        log.info("KSH writing evaluation cache hit: taskType={}, charCount={}",
                                ruleAnalysis.taskType(), ruleAnalysis.characterCount());
                        return rehydrated;
                    } catch (Exception ex) {
                        metrics.recordCacheOperation(
                                PracticeAiMetrics.CacheType.WRITING,
                                PracticeAiMetrics.CacheOperation.PARSE,
                                PracticeAiMetrics.CacheOutcome.MALFORMED,
                                PracticeAiMetrics.elapsedSince(parseStart));
                        log.warn("KSH writing evaluation cache entry ignored because payload is malformed: taskType={}",
                                ruleAnalysis.taskType());
                        deleteCacheEntry(userId, prompt, learnerAnswer, ruleAnalysis);
                    }
                }
            } catch (Exception ex) {
                log.warn("KSH writing evaluation cache read failed; treating as miss: operation=cache-read taskType={} exception={}",
                        ruleAnalysis.taskType(), exceptionCategory(ex));
            }
        }

        // 3. Mock mode when API key is missing
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            long providerStart = PracticeAiMetrics.startNanos();
            String fallback = fallbackToMock(prompt, learnerAnswer, ruleAnalysis, "missing API key");
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FALLBACK, providerStart);
            return fallback;
        }

        // 4. Single unified provider call
        JsonNode response;
        long providerStart = PracticeAiMetrics.startNanos();
        try {
            String systemPrompt = WritingPromptRules.buildUnifiedPrompt(
                    ruleAnalysis.taskType(), isReEvaluation);
            String userPayload = userPayload(prompt, learnerAnswer, ruleAnalysis, isReEvaluation);

            response = callPass("unified", systemPrompt, userPayload, unifiedResponseFormat());
            log.info("KSH writing evaluation unified call complete: taskType={}",
                    ruleAnalysis.taskType());
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Writing AI evaluation failed: operation=provider-call status={} model={} taskType={} retryable={} exception={}",
                    status, properties.evaluatorModel(), ruleAnalysis.taskType(), isRetryable(status), exceptionCategory(ex));
            String fallback = fallbackToMock(prompt, learnerAnswer, ruleAnalysis, "lỗi HTTP " + ex.getStatusCode().value());
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FALLBACK, providerStart);
            return fallback;
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            log.warn("Writing AI evaluation failed: operation=provider-call model={} taskType={} category=transport exception={}",
                    properties.evaluatorModel(), ruleAnalysis.taskType(), exceptionCategory(ex));
            String fallback = fallbackToMock(prompt, learnerAnswer, ruleAnalysis, "lỗi kết nối mạng");
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FALLBACK, providerStart);
            return fallback;
        } catch (Exception ex) {
            log.warn("Writing AI evaluation failed: operation=provider-call model={} taskType={} category=unexpected exception={}",
                    properties.evaluatorModel(), ruleAnalysis.taskType(), exceptionCategory(ex));
            String fallback = fallbackToMock(prompt, learnerAnswer, ruleAnalysis, "lỗi xử lý hệ thống");
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FALLBACK, providerStart);
            return fallback;
        }

        // 5. Normalize — normalizer is sole source of score/raw_score/raw_score_max
        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FAILURE, providerStart);
            throw new IllegalStateException("Internal error serializing JSON response", e);
        }

        String normalized = normalizer.normalize(
                responseJson,
                ruleAnalysis.taskType(),
                learnerAnswer,
                ruleAnalysis);
        recordWritingProvider(normalizer.isCacheableAiResult(normalized)
                ? PracticeAiMetrics.ProviderOutcome.SUCCESS
                : PracticeAiMetrics.ProviderOutcome.FALLBACK, providerStart);

        // 6. Cache result (both submit and re-evaluate overwrite cache)
        if (normalizer.isCacheableAiResult(normalized)) {
            try {
                cacheService.put(userId, prompt, learnerAnswer,
                        ruleAnalysis.taskType(), properties.evaluatorModel(),
                        WritingPromptRules.PROMPT_VERSION, WritingPromptRules.RUBRIC_VERSION,
                        WritingPromptRules.EVALUATION_SCHEMA_VERSION,
                        normalizer.sanitizeForCache(normalized));
            } catch (Exception ex) {
                log.warn("KSH writing evaluation cache write failed; returning provider result: operation=cache-write taskType={} exception={}",
                        ruleAnalysis.taskType(), exceptionCategory(ex));
            }
        }

        return normalized;
    }

    private void deleteCacheEntry(Long userId, String prompt, String learnerAnswer,
                                  WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        try {
            cacheService.delete(userId, prompt, learnerAnswer,
                    ruleAnalysis.taskType(), properties.evaluatorModel(),
                    WritingPromptRules.PROMPT_VERSION, WritingPromptRules.RUBRIC_VERSION,
                    WritingPromptRules.EVALUATION_SCHEMA_VERSION);
        } catch (Exception ex) {
            log.warn("KSH writing evaluation malformed cache cleanup failed: operation=cache-delete taskType={} exception={}",
                    ruleAnalysis.taskType(), exceptionCategory(ex));
        }
    }

    private String fallbackToMock(String prompt, String learnerAnswer, WritingRuleEngine.RuleAnalysis ruleAnalysis, String reason) {
        log.info("KSH writing evaluation falling back to mock: {}", reason);
        String mockJson = mockEvaluatorService.evaluate(prompt, learnerAnswer, ruleAnalysis, reason);
        return normalizer.normalize(mockJson, ruleAnalysis.taskType(), learnerAnswer, ruleAnalysis);
    }

    private void recordWritingProvider(PracticeAiMetrics.ProviderOutcome outcome, long startNanos) {
        metrics.recordProviderOperation(
                PracticeAiMetrics.ProviderFeature.WRITING,
                outcome,
                PracticeAiMetrics.elapsedSince(startNanos));
    }

    private static String exceptionCategory(Exception ex) {
        return ex == null ? "unknown" : ex.getClass().getSimpleName();
    }

    // ---- Spam detection — task-aware ----

    /**
     * Deterministic check for clearly invalid answers.
     * Task-aware: Q51/Q52 allows short answers, so length alone does not
     * disqualify.
     * Only short-circuits when answer is empty/whitespace-only, or contains no
     * Hangul at all.
     */
    static boolean isDefinitelyInvalid(String answer, WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        if (answer == null || answer.trim().isEmpty()) {
            return true;
        }
        String trimmed = answer.trim();
        // No Hangul characters at all — definitely not a Korean writing answer
        // Use (?s) flag so '.' matches newlines in multi-line answers
        if (!trimmed.matches("(?s).*[가-힣].*")) {
            return true;
        }
        return false;
    }

    // ---- Provider call ----

    private JsonNode callPass(String passName,
            String systemPrompt,
            String userPayload,
            Map<String, Object> responseFormat) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("top_p", 1.0);
        request.put("max_tokens", 4096);
        request.put("response_format", responseFormat);
        request.put("messages", List.of(
                message("system", systemPrompt),
                message("user", userPayload)));

        log.info("KSH writing evaluation pass '{}' request prepared: model={}", passName, properties.evaluatorModel());
        String raw = callWithRetry(request);
        JsonNode root = objectMapper.readTree(raw);
        String content = extractOutputText(root, raw);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI returned empty content for pass " + passName);
        }
        return objectMapper.readTree(content);
    }

    private String callWithRetry(Map<String, Object> request) {
        int maxRetries = 5;
        long backoffMs = 3000;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                if (isRetryable(status) && attempt < maxRetries) {
                    log.warn("Writing AI retry {}/{} after HTTP {}. Waiting {} ms.",
                            attempt + 1, maxRetries, status, backoffMs);
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 30000);
                    continue;
                }
                throw ex;
            }
        }
        throw new IllegalStateException("Max retries exceeded for writing evaluation.");
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static void sleep(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Payload ----

    private String userPayload(String prompt,
            String learnerAnswer,
            WritingRuleEngine.RuleAnalysis ruleAnalysis,
            boolean isReEvaluation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_type", "WRITING");
        payload.put("platform", "KSH Korean Study Hub");
        payload.put("level", "TOPIK");
        payload.put("prompt", prompt);
        payload.put("learner_answer", learnerAnswer == null ? "" : learnerAnswer);
        payload.put("task_type", ruleAnalysis.taskType());
        payload.put("character_count", ruleAnalysis.characterCount());
        payload.put("char_count_warning", ruleAnalysis.charCountWarning());
        payload.put("rule_violations", ruleAnalysis.ruleViolations());
        payload.put("audio_evidence_available", false);
        payload.put("is_re_evaluation", isReEvaluation);
        payload.put("audit_mode", isReEvaluation);
        payload.put("allowed_rubric", allowedRubric(ruleAnalysis.taskType()));
        payload.put("score_matrix", WritingScoreMatrix.bands());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build writing evaluator payload.", ex);
        }
    }

    // ---- Rubric info ----

    static List<Map<String, Object>> allowedRubric(String taskType) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WritingRubricCriterion criterion : WritingRubricCriterion.activeForTask(taskType)) {
            rows.add(Map.of(
                    "criterionId", criterion.id(),
                    "vietnameseLabel", criterion.vietnameseLabel(),
                    "koreanLabel", criterion.koreanLabel(),
                    "polarity", criterion.polarity().name(),
                    "category", criterion.category().name(),
                    "evidenceScopes", criterion.evidenceScopes().stream().map(Enum::name).sorted().toList(),
                    "rule", criterion.rule()));
        }
        return rows;
    }

    // ---- Response format / schema ----

    private Map<String, Object> unifiedResponseFormat() {
        return responseFormat("ksh_writing_unified", unifiedSchema());
    }

    private Map<String, Object> unifiedSchema() {
        Map<String, Object> schema = baseObject(list(
                "summary", "rubric_scores", "strengths", "needs_improvement",
                "upgraded_answer", "upgraded_answer_annotated", "sample_answer", "sentence_rewrites"));
        schema.put("properties", prop(
                "summary", typed("string"),
                "rubric_scores", arrayOf(objectSchema(
                        list("name", "score", "feedback"),
                        prop("name", typed("string"), "score", typed("number"), "feedback", typed("string")))),
                "strengths", arrayOf(findingSchema()),
                "needs_improvement", arrayOf(findingSchema()),
                "upgraded_answer", typed("string"),
                "upgraded_answer_annotated", typed("string"),
                "sample_answer", typed("string"),
                "sentence_rewrites", arrayOf(objectSchema(
                        list("original", "upgraded", "reason"),
                        prop("original", typed("string"), "upgraded", typed("string"), "reason", typed("string"))))));
        return schema;
    }

    private Map<String, Object> findingSchema() {
        return objectSchema(
                list("criterionId", "evidenceScope", "evidence", "explanationVi", "correction"),
                prop("criterionId", typed("string"),
                        "evidenceScope", enumSchema("TEXT_SPAN", "WHOLE_ANSWER", "TASK_METADATA"),
                        "evidence", typed("string"),
                        "explanationVi", typed("string"),
                        "correction", typed("string")));
    }

    // ---- Schema helpers ----

    private static Map<String, Object> responseFormat(String name, Map<String, Object> schema) {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", name);
        jsonSchema.put("strict", Boolean.TRUE);
        jsonSchema.put("schema", schema);
        responseFormat.put("json_schema", jsonSchema);
        return responseFormat;
    }

    private static String extractOutputText(JsonNode root, String raw) {
        JsonNode choice = root.path("choices").path(0);
        if (choice.path("message").hasNonNull("content")) {
            return choice.path("message").path("content").asText();
        }
        if (root.hasNonNull("output_text")) {
            return root.path("output_text").asText();
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode contentItem : content) {
                        if (contentItem.has("text")) {
                            builder.append(contentItem.path("text").asText());
                        }
                    }
                }
            }
            if (!builder.isEmpty()) {
                return builder.toString();
            }
        }
        return raw;
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static Map<String, Object> typed(String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        return node;
    }

    private static Map<String, Object> enumSchema(String... values) {
        Map<String, Object> node = typed("string");
        node.put("enum", List.of(values));
        return node;
    }

    private static Map<String, Object> arrayOf(Map<String, Object> itemSchema) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "array");
        node.put("items", itemSchema);
        return node;
    }

    private static Map<String, Object> baseObject(List<String> required) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "object");
        node.put("additionalProperties", Boolean.FALSE);
        node.put("required", required);
        return node;
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object> propertiesMap) {
        Map<String, Object> node = baseObject(required);
        node.put("properties", propertiesMap);
        return node;
    }

    private static Map<String, Object> prop(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static List<String> list(String... values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }
}
