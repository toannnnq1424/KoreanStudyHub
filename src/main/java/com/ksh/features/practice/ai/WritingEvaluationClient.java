package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

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
    private final WritingEvaluationCacheService cacheService;
    private final WritingMockEvaluatorService mockEvaluatorService;

    public WritingEvaluationClient(OpenAiProperties properties,
                                   ObjectMapper objectMapper,
                                   WritingEvaluationNormalizer normalizer,
                                   WritingRuleEngine ruleEngine,
                                   WritingEvaluationCacheService cacheService,
                                   WritingMockEvaluatorService mockEvaluatorService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
        this.ruleEngine = ruleEngine;
        this.cacheService = cacheService;
        this.mockEvaluatorService = mockEvaluatorService;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public String evaluate(String prompt, String learnerAnswer) {
        return evaluate(prompt, learnerAnswer, false);
    }

    public String evaluate(String prompt, String learnerAnswer, boolean isReEvaluation) {
        WritingRuleEngine.RuleAnalysis ruleAnalysis = ruleEngine.analyze(prompt, learnerAnswer);
        log.info("KSH writing evaluation started: model={}, taskType={}, charCount={}, violations={}, reEvaluation={}",
                properties.evaluatorModel(), ruleAnalysis.taskType(), ruleAnalysis.characterCount(),
                ruleAnalysis.ruleViolations().size(), isReEvaluation);

        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("KSH writing evaluation switched to mock mode: missing API key");
            return normalizer.normalize(mockEvaluatorService.evaluate(
                    prompt,
                    learnerAnswer,
                    ruleAnalysis,
                    "chưa cấu hình OPENAI_API_KEY."
            ));
        }

        if (!isReEvaluation) {
            var cached = cacheService.get(prompt, learnerAnswer);
            if (cached.isPresent()) {
                log.info("KSH writing evaluation cache hit: taskType={}, charCount={}",
                        ruleAnalysis.taskType(), ruleAnalysis.characterCount());
                return cached.get();
            }
        }

        try {
            JsonNode overview = evaluateOverview(prompt, learnerAnswer, ruleAnalysis, isReEvaluation);
            String summary = overview.path("summary").asText("");
            log.info("KSH writing evaluation pass 1 complete: taskType={}, score={}, summaryPrefix={}",
                    ruleAnalysis.taskType(), overview.path("score").asDouble(1.0),
                    summary.substring(0, Math.min(40, summary.length())));

            ObjectNode merged;
            if (summary.contains("[SPAM_DETECTED]")) {
                log.info("KSH writing evaluation skipped details/upgrade because spam was detected.");
                merged = merge(overview, emptyDetails(), emptyUpgrade(), ruleAnalysis, learnerAnswer);
            } else {
                JsonNode details = evaluateDetails(prompt, learnerAnswer, ruleAnalysis, overview);
                log.info("KSH writing evaluation pass 2 complete: strengths={}, needs={}",
                        details.path("strengths").size(), details.path("needs_improvement").size());

                JsonNode upgrade = evaluateUpgrade(prompt, learnerAnswer, ruleAnalysis, details);
                log.info("KSH writing evaluation pass 3 complete: rewrites={}",
                        upgrade.path("sentence_rewrites").size());

                merged = merge(overview, details, upgrade, ruleAnalysis, learnerAnswer);
            }

            String normalized = normalizer.normalize(objectMapper.writeValueAsString(merged));
            if (!isReEvaluation) {
                cacheService.put(prompt, learnerAnswer, normalized);
            }
            return normalized;
        } catch (Exception ex) {
            log.warn("Writing AI evaluation failed, switching to mock mode: {}", ex.getMessage());
            return normalizer.normalize(mockEvaluatorService.evaluate(
                    prompt,
                    learnerAnswer,
                    ruleAnalysis,
                    "AI tạm thời không khả dụng hoặc quá tải."
            ));
        }
    }

    private JsonNode evaluateOverview(String prompt,
                                      String learnerAnswer,
                                      WritingRuleEngine.RuleAnalysis ruleAnalysis,
                                      boolean isReEvaluation) throws Exception {
        return callPass(
                "overview",
                WritingPromptRules.buildOverviewPrompt(ruleAnalysis.taskType(), isReEvaluation),
                userPayload(prompt, learnerAnswer, ruleAnalysis, null, null, isReEvaluation),
                overviewResponseFormat()
        );
    }

    private JsonNode evaluateDetails(String prompt,
                                     String learnerAnswer,
                                     WritingRuleEngine.RuleAnalysis ruleAnalysis,
                                     JsonNode overview) throws Exception {
        return callPass(
                "details",
                WritingPromptRules.buildDetailsPrompt(ruleAnalysis.taskType()),
                userPayload(prompt, learnerAnswer, ruleAnalysis, overview, null, false),
                detailsResponseFormat(),
                learnerAnswer  // passed through for annotation index computation
        );
    }

    private JsonNode evaluateUpgrade(String prompt,
                                     String learnerAnswer,
                                     WritingRuleEngine.RuleAnalysis ruleAnalysis,
                                     JsonNode details) throws Exception {
        return callPass(
                "upgrade",
                WritingPromptRules.buildUpgradePrompt(ruleAnalysis.taskType()),
                userPayload(prompt, learnerAnswer, ruleAnalysis, null, details, false),
                upgradeResponseFormat()
        );
    }

    private JsonNode callPass(String passName,
                              String systemPrompt,
                              String userPayload,
                              Map<String, Object> responseFormat) throws Exception {
        return callPass(passName, systemPrompt, userPayload, responseFormat, null);
    }

    private JsonNode callPass(String passName,
                              String systemPrompt,
                              String userPayload,
                              Map<String, Object> responseFormat,
                              String learnerAnswerForIndex) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("response_format", responseFormat);
        request.put("messages", List.of(
                message("system", systemPrompt),
                message("user", userPayload)
        ));

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

    private String userPayload(String prompt,
                               String learnerAnswer,
                               WritingRuleEngine.RuleAnalysis ruleAnalysis,
                               JsonNode overview,
                               JsonNode details,
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
        payload.put("allowed_rubric", allowedRubric());
        payload.put("score_matrix", WritingScoreMatrix.bands());
        if (overview != null) {
            payload.put("overview_result", overview);
        }
        if (details != null) {
            payload.put("details_result", details);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build writing evaluator payload.", ex);
        }
    }

    private ObjectNode merge(JsonNode overview,
                             JsonNode details,
                             JsonNode upgrade,
                             WritingRuleEngine.RuleAnalysis ruleAnalysis,
                             String learnerAnswer) {
        ObjectNode merged = objectMapper.createObjectNode();
        merged.setAll((ObjectNode) overview);
        merged.set("strengths", details.path("strengths"));
        merged.set("needs_improvement", details.path("needs_improvement"));
        merged.put("student_text", learnerAnswer == null ? "" : learnerAnswer);
        merged.put("upgraded_answer", upgrade.path("upgraded_answer").asText(""));
        merged.put("upgraded_answer_annotated", upgrade.path("upgraded_answer_annotated").asText(""));
        merged.put("sample_answer", upgrade.path("sample_answer").asText(""));
        merged.set("sentence_rewrites", upgrade.path("sentence_rewrites"));
        merged.put("task_type", ruleAnalysis.taskType());
        if (!merged.has("raw_score_max")) {
            merged.put("raw_score_max", WritingScoreMatrix.rawScoreMax(ruleAnalysis.taskType()));
        }
        if (!merged.has("raw_score")) {
            merged.put("raw_score", WritingScoreMatrix.rawScoreFromNormalized(
                    merged.path("score").asDouble(1.0),
                    ruleAnalysis.taskType()
            ));
        }
        return merged;
    }

    private JsonNode emptyDetails() {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("strengths", objectMapper.createArrayNode());
        node.set("needs_improvement", objectMapper.createArrayNode());
        return node;
    }

    private JsonNode emptyUpgrade() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("upgraded_answer", "");
        node.put("upgraded_answer_annotated", "");
        node.put("sample_answer", "");
        node.set("sentence_rewrites", objectMapper.createArrayNode());
        return node;
    }

    private static List<Map<String, Object>> allowedRubric() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WritingRubricCriterion criterion : WritingRubricCriterion.values()) {
            rows.add(Map.of(
                    "criterionId", criterion.id(),
                    "vietnameseLabel", criterion.vietnameseLabel(),
                    "koreanLabel", criterion.koreanLabel(),
                    "polarity", criterion.polarity().name(),
                    "weight", criterion.weight(),
                    "rule", criterion.rule()
            ));
        }
        return rows;
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Map<String, Object> overviewResponseFormat() {
        return responseFormat("ksh_writing_overview", overviewSchema());
    }

    private Map<String, Object> detailsResponseFormat() {
        return responseFormat("ksh_writing_details", detailsSchema());
    }

    private Map<String, Object> upgradeResponseFormat() {
        return responseFormat("ksh_writing_upgrade", upgradeSchema());
    }

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

    private Map<String, Object> overviewSchema() {
        Map<String, Object> schema = baseObject(list("score", "raw_score", "raw_score_max", "summary", "rubric_scores"));
        schema.put("properties", prop(
                "score", typed("number"),
                "raw_score", typed("number"),
                "raw_score_max", typed("number"),
                "summary", typed("string"),
                "rubric_scores", arrayOf(objectSchema(
                        list("name", "score", "feedback"),
                        prop("name", typed("string"), "score", typed("number"), "feedback", typed("string"))))
        ));
        return schema;
    }

    private Map<String, Object> detailsSchema() {
        // No longer requests XML-tagged student_*_annotated strings.
        // Backend builds annotations[] from evidence start/end indices.
        Map<String, Object> schema = baseObject(list("strengths", "needs_improvement"));
        schema.put("properties", prop(
                "strengths", arrayOf(findingSchema()),
                "needs_improvement", arrayOf(findingSchema())
        ));
        return schema;
    }

    private Map<String, Object> upgradeSchema() {
        Map<String, Object> schema = baseObject(list("upgraded_answer", "upgraded_answer_annotated", "sample_answer", "sentence_rewrites"));
        schema.put("properties", prop(
                "upgraded_answer", typed("string"),
                "upgraded_answer_annotated", typed("string"),
                "sample_answer", typed("string"),
                "sentence_rewrites", arrayOf(objectSchema(
                        list("original", "upgraded", "reason"),
                        prop("original", typed("string"), "upgraded", typed("string"), "reason", typed("string"))))
        ));
        return schema;
    }

    private Map<String, Object> findingSchema() {
        return objectSchema(
                list("criterionId", "evidence", "explanationVi", "correction"),
                prop("criterionId", typed("string"),
                        "evidence", typed("string"),
                        "explanationVi", typed("string"),
                        "correction", typed("string")));
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

    private static Map<String, Object> typed(String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
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
