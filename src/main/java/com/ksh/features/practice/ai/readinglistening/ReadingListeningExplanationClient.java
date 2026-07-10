package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.features.practice.ai.OpenAiProperties;
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
public class ReadingListeningExplanationClient {

    private static final Logger log = LoggerFactory.getLogger(ReadingListeningExplanationClient.class);
    public static final String EXPLANATION_PROMPT_VERSION = "v2";
    public static final String EXPLANATION_SCHEMA_VERSION = "v1";
    public static final String EXPLANATION_LANGUAGE = "vi";

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ReadingListeningExplanationClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    /**
     * Calls the AI to generate an explanation for the given question.
     *
     * <p>Returns {@code null} (not a fallback) when the AI is unavailable (quota
     * exhausted, network error, max retries exceeded) so the calling service can
     * distinguish between a real AI response and a failure — and avoid writing
     * a temporary failure into the persistent cache.
     *
     * <p>Returns {@code null} also when {@code apiKey} is blank because the caller
     * should route to the mock service directly in that case.
     *
     * @return AI-generated JSON string, or {@code null} if AI could not be reached
     */
    public String explain(PracticeQuestion question, String passageText, String skillType, String optionLabelMode) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("[ReadingListeningAI] No API key configured — signalling caller to use mock.");
            return null;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("response_format", responseFormat());
        request.put("messages", List.of(
                message("system", systemPrompt()),
                message("user", userPayload(question, passageText, skillType, optionLabelMode))
        ));

        log.info("[ReadingListeningAI] start model={} skill={} questionId={} no={} type={}",
                properties.evaluatorModel(), skillType, question.getId(), question.getQuestionNo(), question.getQuestionType());
        String raw = callWithRetry(request, skillType);
        if (raw == null) {
            // callWithRetry already logged the reason; signal caller to use mock
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = extractOutputText(root, raw);
            log.info("[ReadingListeningAI] completed questionId={}", question.getId());
            return cleanAndValidateJson(content, question, optionLabelMode);
        } catch (Exception ex) {
            log.warn("[ReadingListeningAI] JSON parse failed questionId={} model={} exception={}",
                    question.getId(), properties.evaluatorModel(), exceptionCategory(ex));
            return null;
        }
    }

    public String cleanAndValidateJson(String aiJson, PracticeQuestion question, String optionLabelMode) {
        try {
            JsonNode root = objectMapper.readTree(aiJson);
            
            String meaningVi = root.path("meaningVi").asText("").trim();
            String evidenceQuote = root.path("evidenceQuote").asText("").trim();
            String correctReasonVi = root.path("correctReasonVi").asText("").trim();
            String relatedTranslationVi = root.path("relatedTranslationVi").asText("").trim();
            
            String correctAnswer = question.getAnswerKey() != null ? question.getAnswerKey().trim() : "";
            
            List<String> options = readOptions(question.getOptionsJson());
            
            List<Map<String, String>> cleanedEliminated = new ArrayList<>();
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            
            if (root.has("eliminatedOptions") && root.path("eliminatedOptions").isArray()) {
                for (JsonNode optNode : root.path("eliminatedOptions")) {
                    String optionKey = optNode.path("optionKey").asText("").trim();
                    String reasonVi = optNode.path("reasonVi").asText("").trim();
                    
                    if (optionKey.isEmpty()) continue;
                    if (optionKey.equals(correctAnswer)) continue;
                    if (seenKeys.contains(optionKey)) continue;
                    
                    boolean exists = false;
                    if (options.isEmpty()) {
                        exists = true;
                    } else {
                        for (int i = 0; i < options.size(); i++) {
                            String key = "ALPHA".equals(optionLabelMode) ? String.valueOf((char)('A' + i)) : String.valueOf(i + 1);
                            if (key.equals(optionKey)) {
                                exists = true;
                                break;
                            }
                        }
                    }
                    if (!exists) continue;
                    if (reasonVi.isEmpty()) continue;
                    
                    seenKeys.add(optionKey);
                    
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("optionKey", optionKey);
                    item.put("reasonVi", reasonVi);
                    cleanedEliminated.add(item);
                }
            }
            
            cleanedEliminated.sort((o1, o2) -> {
                String k1 = o1.get("optionKey");
                String k2 = o2.get("optionKey");
                if ("ALPHA".equals(optionLabelMode)) {
                    return k1.compareTo(k2);
                } else {
                    try {
                        return Integer.compare(Integer.parseInt(k1), Integer.parseInt(k2));
                    } catch (NumberFormatException e) {
                        return k1.compareTo(k2);
                    }
                }
            });
            
            Map<String, Object> cleaned = new LinkedHashMap<>();
            cleaned.put("meaningVi", meaningVi);
            cleaned.put("evidenceQuote", evidenceQuote);
            cleaned.put("correctReasonVi", correctReasonVi);
            cleaned.put("relatedTranslationVi", relatedTranslationVi);
            cleaned.put("eliminatedOptions", cleanedEliminated);
            
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            log.warn("[ReadingListeningAI] cleaning JSON failed questionId={} model={} exception={}",
                    question.getId(), properties.evaluatorModel(), exceptionCategory(e));
            return aiJson;
        }
    }

    public String model() {
        return properties.evaluatorModel();
    }

    public String promptVersion() {
        return EXPLANATION_PROMPT_VERSION;
    }

    public String schemaVersion() {
        return EXPLANATION_SCHEMA_VERSION;
    }

    public String explanationLanguage() {
        return EXPLANATION_LANGUAGE;
    }

    private String userPayload(PracticeQuestion question, String passageText, String skillType, String optionLabelMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", String.valueOf(question.getId()));
        payload.put("questionNo", question.getQuestionNo());
        payload.put("questionType", question.getQuestionType());
        payload.put("prompt", question.getPrompt());
        payload.put("options", readOptions(question.getOptionsJson()));
        payload.put("correctAnswer", question.getAnswerKey() != null ? question.getAnswerKey() : "");
        payload.put("passageText", passageText != null ? passageText : "");
        payload.put("skillType", skillType);
        payload.put("optionLabelMode", optionLabelMode != null ? optionLabelMode : "NUMERIC");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(optionsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String systemPrompt() {
        return """
                Bạn là giáo viên giải thích đáp án Reading/Listening cho học viên Việt Nam học TOPIK và tiếng Hàn.
                
                Nhiệm vụ:
                - Giải thích vì sao đáp án đúng và vì sao từng phương án còn lại không đúng.
                - Tuyệt đối không đổi correctAnswer.
                - Không chấm điểm người học, không khen/chê, không tìm lỗi cá nhân.
                - Luôn dựa vào questionType được gửi trong payload.
                - Trả về giải thích bằng tiếng Việt rõ ràng, dễ hiểu.
                
                Quy tắc phương án:
                - correctAnswer và eliminatedOptions chỉ dùng mã phương án.
                - Mã phương án tuân theo optionLabelMode được cung cấp trong payload.
                - Nếu optionLabelMode=NUMERIC, chỉ dùng "1", "2", "3", "4"... làm optionKey.
                - Nếu optionLabelMode=ALPHA, chỉ dùng "A", "B", "C", "D"... làm optionKey.
                - Không chép lại toàn bộ nội dung phương án vào eliminatedOptions.
                - Không dùng nội dung tiếng Hàn của phương án làm optionKey.
                - reasonVi chỉ giải thích lý do loại trừ bằng tiếng Việt.
                - Có thể trích dẫn một cụm tiếng Hàn ngắn từ bài đọc/nghe làm bằng chứng.
                - Không được đưa đáp án đúng vào eliminatedOptions.
                - Không tự tạo phương án không tồn tại.
                
                Hãy trả về dữ liệu JSON nghiêm ngặt theo schema sau:
                {
                  "meaningVi": "Dịch nghĩa/Giải nghĩa câu hỏi",
                  "evidenceQuote": "Trích dẫn bằng chứng tiếng Hàn từ bài đọc/nghe",
                  "correctReasonVi": "Lý do đáp án đúng (bằng tiếng Việt)",
                  "relatedTranslationVi": "Dịch nghĩa đoạn liên quan",
                  "eliminatedOptions": [
                    {
                      "optionKey": "Mã phương án (1/2/3/4 hoặc A/B/C/D)",
                      "reasonVi": "Lý do loại trừ phương án này (bằng tiếng Việt)"
                    }
                  ]
                }
                """;
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", Map.of(
                "name", "rl_answer_explanation",
                "strict", Boolean.TRUE,
                "schema", schema()
        ));
        return responseFormat;
    }

    private Map<String, Object> schema() {
        Map<String, Object> eliminatedOptionSchema = objectSchema(
                list("optionKey", "reasonVi"),
                prop("optionKey", typed("string"),
                     "reasonVi", typed("string"))
        );

        return objectSchema(
                list("meaningVi", "evidenceQuote", "correctReasonVi", "relatedTranslationVi", "eliminatedOptions"),
                prop(
                        "meaningVi", typed("string"),
                        "evidenceQuote", typed("string"),
                        "correctReasonVi", typed("string"),
                        "relatedTranslationVi", typed("string"),
                        "eliminatedOptions", arrayOf(eliminatedOptionSchema)
                )
        );
    }

    private static String extractOutputText(JsonNode root, String raw) {
        JsonNode choice = root.path("choices").path(0);
        if (choice.path("message").hasNonNull("content")) {
            return choice.path("message").path("content").asText();
        }
        return raw;
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private static Map<String, Object> typed(String type) {
        return Map.of("type", type);
    }

    private static Map<String, Object> arrayOf(Map<String, Object> itemSchema) {
        return Map.of("type", "array", "items", itemSchema);
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object> propertiesMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "object");
        node.put("additionalProperties", Boolean.FALSE);
        node.put("required", required);
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
        return List.of(values);
    }

    private String callWithRetry(Map<String, Object> request, String skillType) {
        int maxRetries = 3;
        long backoffMs = 1500;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.info("[ReadingListeningAI] attempt={}/{} POST /chat/completions model={}",
                        attempt, maxRetries + 1, properties.evaluatorModel());
                String response = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                log.info("[ReadingListeningAI] attempt={} success", attempt);
                return response;
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = isRetryable(status) && attempt <= maxRetries;
                log.warn("[ReadingListeningAI] attempt={} status={} model={} skill={} retryable={}",
                        attempt, status, properties.evaluatorModel(), skillType, retryable);
                if (retryable) {
                    sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                return null;
            } catch (Exception ex) {
                boolean retryable = attempt <= maxRetries;
                log.warn("[ReadingListeningAI] attempt={} failed model={} skill={} exception={} retryable={}",
                        attempt, properties.evaluatorModel(), skillType, exceptionCategory(ex), retryable);
                if (retryable) {
                    sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                return null;
            }
        }
        return null;
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

    private static String exceptionCategory(Exception ex) {
        return ex == null ? "unknown" : ex.getClass().getSimpleName();
    }

}
