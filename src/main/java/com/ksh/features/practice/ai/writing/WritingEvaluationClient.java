package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import com.ksh.features.practice.ai.media.AiQuestionImageResolver;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
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
    private final PracticeAiMetrics metrics;
    private final AiQuestionImageResolver imageResolver;
    private final WritingProviderResponseDecoder responseDecoder;

    public WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService,
                (RestClient) null, (AiQuestionImageResolver) null, PracticeAiMetrics.noop());
    }

    /**
     * Source-compatible test seam retained while the unreachable mock fallback
     * is removed from the live dependency graph.
     */
    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService ignoredMockEvaluatorService) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService);
    }

    @Autowired
    public WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            AiQuestionImageResolver imageResolver,
            PracticeAiMetrics metrics) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService,
                null, imageResolver, metrics);
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingEvaluationCacheService cacheService,
            RestClient restClient) {
        this(properties, objectMapper, normalizer, ruleEngine, new WritingTaskResolver(),
                cacheService, restClient, null, PracticeAiMetrics.noop());
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService ignoredMockEvaluatorService,
            RestClient restClient) {
        this(properties, objectMapper, normalizer, ruleEngine, cacheService, restClient);
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            RestClient restClient) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService,
                restClient, null, PracticeAiMetrics.noop());
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService ignoredMockEvaluatorService,
            RestClient restClient) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService,
                restClient);
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            RestClient restClient,
            PracticeAiMetrics metrics) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService,
                restClient, null, metrics);
    }

    WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            WritingMockEvaluatorService ignoredMockEvaluatorService,
            RestClient restClient,
            PracticeAiMetrics metrics) {
        this(properties, objectMapper, normalizer, ruleEngine, taskResolver, cacheService,
                restClient, metrics);
    }

    private WritingEvaluationClient(OpenAiProperties properties,
            ObjectMapper objectMapper,
            WritingEvaluationNormalizer normalizer,
            WritingRuleEngine ruleEngine,
            WritingTaskResolver taskResolver,
            WritingEvaluationCacheService cacheService,
            RestClient restClient,
            AiQuestionImageResolver imageResolver,
            PracticeAiMetrics metrics) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
        this.ruleEngine = ruleEngine;
        this.taskResolver = taskResolver;
        this.cacheService = cacheService;
        this.imageResolver = imageResolver;
        this.metrics = metrics == null ? PracticeAiMetrics.noop() : metrics;
        this.responseDecoder =
                new WritingProviderResponseDecoder(objectMapper);
        if (restClient != null) {
            this.restClient = restClient;
        } else {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .requestFactory(requestFactory(
                            properties.connectTimeout(),
                            properties.readTimeout()));
            if (properties.hasEvaluatorCredential()) {
                builder.defaultHeader(
                        "Authorization",
                        "Bearer " + properties.apiKey());
            }
            this.restClient = builder.build();
        }
    }

    public String evaluate(String prompt, String learnerAnswer) {
        return evaluate(null, prompt, learnerAnswer, false);
    }

    public String evaluationContractIdentity() {
        return String.join(
                "|",
                "ksh-writing-evaluation-v2",
                properties.baseUrl(),
                properties.evaluatorModel(),
                properties.connectTimeout().toString(),
                properties.readTimeout().toString(),
                "max-output-tokens="
                        + properties.evaluatorMaxOutputTokens(),
                "structured-output="
                        + properties.evaluatorStructuredOutputEnabled(),
                "reasoning-effort="
                        + (properties.evaluatorReasoningEffortEnabled()
                        ? properties.evaluatorReasoningEffort()
                        : "disabled"),
                "max-retries=5",
                WritingPromptRules.PROMPT_VERSION,
                WritingPromptRules.RUBRIC_VERSION,
                cacheSchemaVersion());
    }

    public String evaluate(String prompt, String learnerAnswer, boolean isReEvaluation) {
        return evaluate(null, prompt, learnerAnswer, isReEvaluation);
    }

    public String evaluate(Long userId, String prompt, String learnerAnswer, boolean isReEvaluation) {
        return evaluate(userId, prompt, learnerAnswer, isReEvaluation, null);
    }

    public String evaluate(Long userId, String prompt, String learnerAnswer, boolean isReEvaluation,
                           WritingTaskType explicitTaskType) {
        return evaluate(userId, prompt, learnerAnswer, isReEvaluation, explicitTaskType, null);
    }

    public String evaluate(Long userId, String prompt, String learnerAnswer, boolean isReEvaluation,
                           WritingTaskType explicitTaskType, String imageReference) {
        AiImageEvidence imageEvidence = imageResolver == null
                ? null
                : imageResolver.resolve(imageReference, userId).orElse(null);
        String cachePrompt = cachePrompt(prompt, imageEvidence);
        String resolvedTaskType = taskResolver.resolve(explicitTaskType, prompt);
        WritingRuleEngine.RuleAnalysis ruleAnalysis = ruleEngine.analyze(prompt, learnerAnswer, resolvedTaskType);
        log.info("KSH writing evaluation started: model={}, taskType={}, charCount={}, violations={}, reEvaluation={}",
                properties.evaluatorModel(), ruleAnalysis.taskType(), ruleAnalysis.characterCount(),
                ruleAnalysis.ruleViolations().size(), isReEvaluation);

        // 1. Deterministic spam short-circuit — task-aware
        if (isDefinitelyInvalid(learnerAnswer, ruleAnalysis)) {
            log.info("KSH writing evaluation deterministic invalid-response short-circuit: taskType={}", ruleAnalysis.taskType());
            return normalizer.spamResponse(ruleAnalysis.taskType(), learnerAnswer);
        }

        // 2. Cache lookup (skip for re-evaluation)
        if (!isReEvaluation) {
            try {
                var cached = cacheService.get(userId, cachePrompt, learnerAnswer,
                        ruleAnalysis.taskType(), properties.evaluatorModel(),
                        WritingPromptRules.PROMPT_VERSION, WritingPromptRules.RUBRIC_VERSION,
                        cacheSchemaVersion());
                if (cached.isPresent()) {
                    long parseStart = PracticeAiMetrics.startNanos();
                    try {
                        String rehydrated = normalizer.rehydrateCachedResult(
                                cached.get(), learnerAnswer, ruleAnalysis.taskType());
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
                        deleteCacheEntry(userId, cachePrompt, learnerAnswer, ruleAnalysis);
                    }
                }
            } catch (Exception ex) {
                log.warn("KSH writing evaluation cache read failed; treating as miss: operation=cache-read taskType={} exception={}",
                        ruleAnalysis.taskType(), exceptionCategory(ex));
            }
        }

        // 3. Fail closed when provider credentials are unavailable
        if (!properties.hasEvaluatorCredential()) {
            long providerStart = PracticeAiMetrics.startNanos();
            log.warn(
                    "Writing AI evaluator configuration unavailable: operation=provider-preflight model={} reason=MISSING_API_KEY credentialSource=VERTEX_ACCESS_TOKEN_or_OPENAI_API_KEY",
                    properties.evaluatorModel());
            String unavailable = normalizer.providerUnavailable(
                    "MISSING_API_KEY",
                    ruleAnalysis.taskType(),
                    learnerAnswer,
                    false);
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FAILURE, providerStart);
            return unavailable;
        }

        // 4. Single unified provider call
        JsonNode response;
        long providerStart = PracticeAiMetrics.startNanos();
        try {
            String systemPrompt = WritingPromptRules.buildUnifiedPrompt(
                    ruleAnalysis.taskType(), isReEvaluation);
            String userPayload = userPayload(
                    prompt, learnerAnswer, ruleAnalysis, isReEvaluation, imageEvidence);

            response = callPass(
                    "unified", systemPrompt, userPayload, imageEvidence);
            log.info("KSH writing evaluation unified call complete: taskType={}",
                    ruleAnalysis.taskType());
        } catch (WritingProviderResponseDecoder.DecodingException ex) {
            log.warn(
                    "Writing AI evaluation contract failed: operation=provider-contract provider=openai-compatible model={} taskType={} reason={} finishReason={} contentLength={} requestId={} exception={}",
                    properties.evaluatorModel(),
                    ruleAnalysis.taskType(),
                    ex.reason(),
                    ex.finishReason(),
                    ex.contentLength(),
                    ex.requestId(),
                    exceptionCategory(ex));
            String failure = normalizer.contractFailure(ex.reason(), ruleAnalysis.taskType(), learnerAnswer);
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FAILURE, providerStart);
            return failure;
        } catch (EvaluationInterruptedException ex) {
            // Worker interruption is lifecycle control, not a provider
            // availability outcome. Let the durable job remain reclaimable.
            throw ex;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Writing AI evaluation failed: operation=provider-call status={} model={} taskType={} retryable={} exception={}",
                    status, properties.evaluatorModel(), ruleAnalysis.taskType(), isRetryable(status), exceptionCategory(ex));
            String unavailable = normalizer.providerUnavailable(
                    "PROVIDER_HTTP_ERROR",
                    ruleAnalysis.taskType(),
                    learnerAnswer,
                    isRetryable(status));
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FAILURE, providerStart);
            return unavailable;
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            log.warn("Writing AI evaluation failed: operation=provider-call model={} taskType={} category=transport exception={}",
                    properties.evaluatorModel(), ruleAnalysis.taskType(), exceptionCategory(ex));
            String unavailable = normalizer.providerUnavailable(
                    "PROVIDER_TRANSPORT_ERROR",
                    ruleAnalysis.taskType(),
                    learnerAnswer,
                    true);
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FAILURE, providerStart);
            return unavailable;
        } catch (Exception ex) {
            log.warn("Writing AI evaluation failed: operation=provider-call model={} taskType={} category=unexpected exception={}",
                    properties.evaluatorModel(), ruleAnalysis.taskType(), exceptionCategory(ex));
            String unavailable = normalizer.providerUnavailable(
                    "PROVIDER_UNEXPECTED_ERROR",
                    ruleAnalysis.taskType(),
                    learnerAnswer,
                    false);
            recordWritingProvider(PracticeAiMetrics.ProviderOutcome.FAILURE, providerStart);
            return unavailable;
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
                cacheService.put(userId, cachePrompt, learnerAnswer,
                        ruleAnalysis.taskType(), properties.evaluatorModel(),
                        WritingPromptRules.PROMPT_VERSION, WritingPromptRules.RUBRIC_VERSION,
                        cacheSchemaVersion(),
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
                        cacheSchemaVersion());
        } catch (Exception ex) {
            log.warn("KSH writing evaluation malformed cache cleanup failed: operation=cache-delete taskType={} exception={}",
                    ruleAnalysis.taskType(), exceptionCategory(ex));
        }
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

    private String cacheSchemaVersion() {
        String base = WritingPromptRules.EVALUATION_SCHEMA_VERSION
                + ":"
                + WritingPromptRules.EVALUATION_CONTRACT_VERSION;
        if (properties.evaluatorMaxOutputTokens() == 4096
                && properties.evaluatorStructuredOutputEnabled()
                && !properties.evaluatorReasoningEffortEnabled()) {
            return base;
        }
        return base
                + ":mt" + properties.evaluatorMaxOutputTokens()
                + ":so" + (properties
                .evaluatorStructuredOutputEnabled() ? "1" : "0")
                + ":re" + (properties.evaluatorReasoningEffortEnabled()
                ? properties.evaluatorReasoningEffort()
                : "disabled");
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
            AiImageEvidence imageEvidence) {
        Map<String, Object> request = buildProviderRequest(
                systemPrompt,
                userPayload,
                imageEvidence);

        log.info("KSH writing evaluation pass '{}' request prepared: model={}", passName, properties.evaluatorModel());
        String raw = callWithRetry(request);
        return responseDecoder.decode(raw);
    }

    private Map<String, Object> buildProviderRequest(
            String systemPrompt,
            String userPayload,
            AiImageEvidence imageEvidence) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("top_p", 1.0);
        request.put(
                "max_tokens",
                properties.evaluatorMaxOutputTokens());
        if (properties.evaluatorStructuredOutputEnabled()) {
            request.put(
                    "response_format",
                    unifiedResponseFormat());
        }
        if (properties.evaluatorReasoningEffortEnabled()) {
            request.put(
                    "reasoning_effort",
                    properties.evaluatorReasoningEffort());
        }
        request.put("messages", List.of(
                message("system", systemPrompt),
                message("user", multimodalContent(userPayload, imageEvidence))));
        return request;
    }

    private String callWithRetry(Map<String, Object> request) {
        int maxRetries = 5;
        long backoffMs = 3000;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            requireEvaluationThreadActive();
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

    static SimpleClientHttpRequestFactory requestFactory(
            Duration connectTimeout,
            Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis(connectTimeout, Duration.ofSeconds(5)));
        factory.setReadTimeout(timeoutMillis(readTimeout, Duration.ofSeconds(60)));
        return factory;
    }

    private static int timeoutMillis(Duration configured, Duration fallback) {
        Duration resolved = configured == null ? fallback : configured;
        long millis = resolved.toMillis();
        if (millis <= 0) {
            return Math.toIntExact(fallback.toMillis());
        }
        return Math.toIntExact(Math.min(millis, Integer.MAX_VALUE));
    }

    private static void sleep(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EvaluationInterruptedException(interrupted);
        }
    }

    private static void requireEvaluationThreadActive() {
        if (Thread.currentThread().isInterrupted()) {
            throw new EvaluationInterruptedException(null);
        }
    }

    private static final class EvaluationInterruptedException
            extends RuntimeException {
        private EvaluationInterruptedException(Throwable cause) {
            super("Writing evaluation was interrupted.", cause);
        }
    }

    // ---- Payload ----

    private String userPayload(String prompt,
            String learnerAnswer,
            WritingRuleEngine.RuleAnalysis ruleAnalysis,
            boolean isReEvaluation,
            AiImageEvidence imageEvidence) {
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
        payload.put("question_image", imageEvidence == null
                ? Map.of("available", false)
                : Map.of(
                        "available", true,
                        "asset_id", imageEvidence.assetId(),
                        "mime_type", imageEvidence.mimeType(),
                        "sha256", imageEvidence.sha256(),
                        "size_bytes", imageEvidence.sizeBytes()));
        payload.put("is_re_evaluation", isReEvaluation);
        payload.put("audit_mode", isReEvaluation);
        payload.put("allowed_rubric", Map.of(
                "scoring_criteria", scoringCriteria(ruleAnalysis.taskType()),
                "finding_criteria", allowedRubric(ruleAnalysis.taskType())));
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

    static List<Map<String, Object>> scoringCriteria(String taskType) {
        return WritingPromptRules.scoringCriteriaForTask(taskType).stream()
                .map(row -> Map.<String, Object>of(
                        "criterionId", row.criterionId(),
                        "displayName", row.displayName(),
                        "max_score", row.maxScore(),
                        "order", row.order()))
                .toList();
    }

    // ---- Response format / schema ----

    private Map<String, Object> unifiedResponseFormat() {
        return responseFormat("ksh_writing_unified", unifiedSchema());
    }

    private Map<String, Object> unifiedSchema() {
        Map<String, Object> schema = baseObject(list(
                "summary", "rubric_scores", "strengths", "needs_improvement",
                "upgraded_answer", "upgraded_answer_annotated", "sentence_rewrites"));
        schema.put("properties", prop(
                "summary", typed("string"),
                "rubric_scores", arrayOf(objectSchema(
                        list("criterionId", "name", "score", "maxScore", "feedback"),
                        prop("criterionId", typed("string"), "name", typed("string"), "score", typed("number"),
                                "maxScore", typed("number"), "feedback", typed("string")))),
                "strengths", arrayOf(findingSchema()),
                "needs_improvement", arrayOf(findingSchema()),
                "upgraded_answer", typed("string"),
                "upgraded_answer_annotated", typed("string"),
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

    private static List<Map<String, Object>> multimodalContent(
            String payload,
            AiImageEvidence imageEvidence
    ) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", payload));
        if (imageEvidence != null) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", imageEvidence.dataUrl(), "detail", "high")));
        }
        return content;
    }

    private static String cachePrompt(String prompt, AiImageEvidence imageEvidence) {
        if (imageEvidence == null) {
            return prompt;
        }
        return (prompt == null ? "" : prompt)
                + "\n[KSH_QUESTION_IMAGE_SHA256:" + imageEvidence.sha256() + "]";
    }

    private static Map<String, Object> message(String role, Object content) {
        Map<String, Object> message = new LinkedHashMap<>();
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
