package com.ksh.features.practice.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSet;
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
public class AnswerExplanationClient {

    private static final Logger log = LoggerFactory.getLogger(AnswerExplanationClient.class);

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AnswerExplanationClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public String explain(PracticeSet set, List<PracticeQuestion> questions, Map<String, Object> answers) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return fallback(questions, answers, "Chua cau hinh API key AI.");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("response_format", responseFormat());
        request.put("messages", List.of(
                message("system", systemPrompt()),
                message("user", userPayload(set, questions, answers))
        ));

        try {
            log.info("[PracticeAnswerAI] start model={} baseUrl={} setId={} skill={} questions={} apiKey={}",
                    properties.evaluatorModel(), properties.baseUrl(), set.getId(), set.getSkill(),
                    questions.size(), maskedApiKey());
            String raw = callWithRetry(request);
            JsonNode root = objectMapper.readTree(raw);
            String content = extractOutputText(root, raw);
            log.info("[PracticeAnswerAI] done rawChars={} contentChars={} preview={}",
                    raw == null ? 0 : raw.length(), content == null ? 0 : content.length(), preview(content, 400));
            return normalize(content, questions, answers);
        } catch (Exception ex) {
            log.warn("[PracticeAnswerAI] failed setId={} type={} message={}",
                    set.getId(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return fallback(questions, answers, "AI chua tao duoc giai thich chi tiet luc nay.");
        }
    }

    private String callWithRetry(Map<String, Object> request) {
        int maxRetries = 3;
        long backoffMs = 1500;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.info("[PracticeAnswerAI] attempt={}/{} POST /chat/completions model={}",
                        attempt, maxRetries + 1, properties.evaluatorModel());
                String response = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                log.info("[PracticeAnswerAI] attempt={} success", attempt);
                return response;
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = isRetryable(status) && attempt <= maxRetries;
                log.warn("[PracticeAnswerAI] attempt={} status={} retryable={} body={}",
                        attempt, status, retryable, preview(ex.getResponseBodyAsString(), 800));
                if (retryable) {
                    sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                throw ex;
            }
        }
        throw new IllegalStateException("Max retries exceeded for answer explanation.");
    }

    private String normalize(String content, List<PracticeQuestion> questions, Map<String, Object> answers) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.path("items").isArray()) {
                return fallback(questions, answers, "AI tra ve sai cau truc JSON.");
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode node : root.path("items")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("questionId", node.path("questionId").asText(""));
                row.put("questionNo", node.path("questionNo").asInt(0));
                row.put("meaningVi", node.path("meaningVi").asText(""));
                row.put("evidenceQuote", node.path("evidenceQuote").asText(""));
                row.put("correctReasonVi", node.path("correctReasonVi").asText(""));
                row.put("relatedTranslationVi", node.path("relatedTranslationVi").asText(""));
                
                List<Map<String, Object>> elimOptions = new ArrayList<>();
                if (node.has("eliminatedOptions") && node.path("eliminatedOptions").isArray()) {
                    for (JsonNode optNode : node.path("eliminatedOptions")) {
                        Map<String, Object> opt = new LinkedHashMap<>();
                        opt.put("optionKey", optNode.path("optionKey").asText(""));
                        opt.put("reasonVi", optNode.path("reasonVi").asText(""));
                        elimOptions.add(opt);
                    }
                }
                row.put("eliminatedOptions", elimOptions);
                rows.add(row);
            }
            return objectMapper.writeValueAsString(Map.of("items", rows));
        } catch (Exception ex) {
            log.warn("[PracticeAnswerAI] normalize failed: {}", ex.getMessage());
            return fallback(questions, answers, "Khong doc duoc JSON giai thich tu AI.");
        }
    }

    private String fallback(List<PracticeQuestion> questions, Map<String, Object> answers, String reason) {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (PracticeQuestion question : questions) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("questionId", String.valueOf(question.getId()));
                row.put("questionNo", question.getQuestionNo());
                row.put("meaningVi", reason);
                row.put("evidenceQuote", "Không tìm thấy bằng chứng.");
                row.put("correctReasonVi", question.getExplanation() == null || question.getExplanation().isBlank()
                        ? "Dap an dung duoc cham theo key da luu: " + safe(question.getAnswerKey())
                        : question.getExplanation());
                row.put("relatedTranslationVi", "Không có dịch nghĩa.");
                row.put("eliminatedOptions", List.of());
                row.put("learnerAnswer", answers.getOrDefault(String.valueOf(question.getId()), ""));
                rows.add(row);
            }
            return objectMapper.writeValueAsString(Map.of("items", rows));
        } catch (Exception ex) {
            return "{\"items\":[]}";
        }
    }

    private String userPayload(PracticeSet set, List<PracticeQuestion> questions, Map<String, Object> answers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill", set.getSkill());
        payload.put("topik_level", set.getTopikLevel());
        payload.put("optionLabelMode", com.ksh.features.practice.dto.PracticeDtos.getOptionLabelMode(set.getTitle(), set.getMetadataJson()));
        payload.put("rule", "Chi giai thich dua tren de bai, lua chon, transcript/context neu co, va answerKey da set san.");
        payload.put("questions", questions.stream().map(question -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", String.valueOf(question.getId()));
            row.put("questionNo", question.getQuestionNo());
            row.put("questionType", question.getQuestionType());
            row.put("prompt", question.getPrompt());
            row.put("options", readOptions(question.getOptionsJson()));
            row.put("answerKey", safe(question.getAnswerKey()));
            row.put("learnerAnswer", answers.getOrDefault(String.valueOf(question.getId()), ""));
            row.put("existingExplanation", safe(question.getExplanation()));
            return row;
        }).toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build answer explanation payload.", ex);
        }
    }

    private List<String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String systemPrompt() {
        return """
                Bạn là giáo viên tiếng Hàn của KSH Korean Study Hub, chuyên giải thích đáp án đọc/nghe cho học viên Việt Nam.

                Quy tắc bắt buộc:
                - Chỉ dùng tiếng Việt để giải thích. Chỉ dùng tiếng Hàn khi trích bằng chứng từ đề bài, transcript, từ vựng hoặc lựa chọn.
                - Tuyệt đối không đổi answerKey. answerKey là đáp án đúng đã được giáo viên/AI set sẵn.
                - Không bịa transcript, không bịa dữ kiện ngoài prompt/options/existingExplanation.
                - Nếu prompt không có transcript riêng, coi prompt là ngữ cảnh để giải thích.
                - Mã phương án trong eliminatedOptions tuân theo optionLabelMode được cung cấp trong payload.
                - Nếu optionLabelMode=NUMERIC, chỉ dùng "1", "2", "3", "4"... làm optionKey.
                - Nếu optionLabelMode=ALPHA, chỉ dùng "A", "B", "C", "D"... làm optionKey.
                - Không chép lại toàn bộ nội dung phương án vào eliminatedOptions.
                
                Với mỗi câu hỏi, trả đúng các trường:
                1. meaningVi: dịch nghĩa/nghĩa chính của câu hỏi và ngữ cảnh cần hiểu.
                2. evidenceQuote: trích bằng chứng từ prompt/transcript/context.
                3. correctReasonVi: lý do answerKey đúng.
                4. relatedTranslationVi: dịch nghĩa đoạn liên quan.
                5. eliminatedOptions: danh sách đối tượng chứa optionKey và reasonVi giải thích lý do loại lựa chọn đó.

                Không dùng Markdown. Chỉ trả JSON đúng schema.
                """;
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", Map.of(
                "name", "ksh_answer_explanations",
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

        Map<String, Object> item = objectSchema(
                list("questionId", "questionNo", "meaningVi", "evidenceQuote", "correctReasonVi", "relatedTranslationVi", "eliminatedOptions"),
                prop("questionId", typed("string"),
                        "questionNo", typed("integer"),
                        "meaningVi", typed("string"),
                        "evidenceQuote", typed("string"),
                        "correctReasonVi", typed("string"),
                        "relatedTranslationVi", typed("string"),
                        "eliminatedOptions", arrayOf(eliminatedOptionSchema)));
        return objectSchema(list("items"), prop("items", arrayOf(item)));
    }

    private static String extractOutputText(JsonNode root, String raw) {
        JsonNode choice = root.path("choices").path(0);
        if (choice.path("message").hasNonNull("content")) {
            return choice.path("message").path("content").asText();
        }
        if (root.hasNonNull("output_text")) {
            return root.path("output_text").asText();
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

    private static Map<String, Object> arrayOf(Map<String, Object> itemSchema) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "array");
        node.put("items", itemSchema);
        return node;
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
        List<String> list = new ArrayList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
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

    private String maskedApiKey() {
        String key = properties.apiKey();
        if (key == null || key.isBlank()) {
            return "<empty>";
        }
        if (key.length() <= 10) {
            return "***";
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    private static String preview(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxChars ? compact : compact.substring(0, maxChars) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
