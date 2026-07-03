package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ksh.entities.PracticeQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PracticePdfQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfQuestionGenerator.class);
    private static final int MAX_TEXT_CHARS = 80_000;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PracticePdfQuestionGenerator(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public List<GeneratedGroup> generate(String pdfText, String skill, String topikLevel) {
        return generate(pdfText, skill, topikLevel, null);
    }

    public List<GeneratedGroup> generate(String pdfText, String skill, String topikLevel, String examTemplate) {
        requireApiKey();
        if (pdfText == null || pdfText.isBlank()) {
            throw new IllegalArgumentException("Không đọc được chữ trong PDF.");
        }

        String truncatedText = truncate(pdfText, MAX_TEXT_CHARS);
        log.info("[PracticePdfAI] mode=text model={} baseUrl={} skill={} topikLevel={} examTemplate={} textChars={} sentChars={}",
                properties.evaluatorModel(), properties.baseUrl(), skill, topikLevel, safeExamTemplateForLog(examTemplate),
                pdfText.length(), truncatedText.length());

        Map<String, Object> request = baseRequest(List.of(
                message("system", systemPrompt()),
                message("user", userPayload(truncatedText, skill, topikLevel, examTemplate))
        ));
        return executeAndParse(request, "text", skill, topikLevel);
    }

    public List<GeneratedGroup> generateFromImages(List<String> pageImageDataUrls, String skill, String topikLevel) {
        return generateFromImages(pageImageDataUrls, skill, topikLevel, null);
    }

    public List<GeneratedGroup> generateFromImages(List<String> pageImageDataUrls, String skill, String topikLevel, String examTemplate) {
        requireApiKey();
        if (pageImageDataUrls == null || pageImageDataUrls.isEmpty()) {
            throw new IllegalArgumentException("Không render được trang PDF thành ảnh để AI đọc.");
        }

        long totalImageChars = pageImageDataUrls.stream().mapToLong(String::length).sum();
        log.info("[PracticePdfAI] mode=image_multimodal model={} baseUrl={} skill={} topikLevel={} examTemplate={} pages={} imagePayloadChars={}",
                properties.evaluatorModel(), properties.baseUrl(), skill, topikLevel, safeExamTemplateForLog(examTemplate),
                pageImageDataUrls.size(), totalImageChars);

        Map<String, Object> request = baseRequest(List.of(
                message("system", systemPrompt()),
                multimodalUserMessage(pageImageDataUrls, skill, topikLevel, examTemplate)
        ));
        return executeAndParse(request, "image_multimodal", skill, topikLevel);
    }

    private void requireApiKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Chưa cấu hình API key AI.");
        }
    }

    private Map<String, Object> baseRequest(List<Map<String, Object>> messages) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("response_format", responseFormat());
        request.put("messages", messages);
        return request;
    }

    private List<GeneratedGroup> executeAndParse(Map<String, Object> request, String mode,
                                                 String skill, String topikLevel) {
        try {
            String raw = callWithRetry(request, mode);
            log.info("[PracticePdfAI] mode={} rawResponseChars={}", mode, raw == null ? 0 : raw.length());

            String content = extractOutputText(objectMapper.readTree(raw), raw);
            log.info("[PracticePdfAI] mode={} contentChars={}", mode, content == null ? 0 : content.length());

            JsonNode root = objectMapper.readTree(content);
            List<GeneratedGroup> groups = new ArrayList<>();
            int rejected = 0;
            JsonNode groupsNode = root.path("groups");
            if (groupsNode.isArray()) {
                for (JsonNode node : groupsNode) {
                    GeneratedGroup group = toGeneratedGroup(node, forceTopikMcq(skill, topikLevel));
                    if (group.questions().isEmpty()) {
                        rejected++;
                    } else {
                        groups.add(group);
                    }
                }
            }
            log.info("[PracticePdfAI] mode={} parsedGroups={} rejectedGroups={}",
                    mode, groups.size(), rejected);
            if (groups.isEmpty()) {
                throw new IllegalStateException("AI chưa trích được nhóm câu hỏi nào từ PDF.");
            }
            return groups;
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            log.warn("[PracticePdfAI] operation=provider-call mode={} model={} status={} retryable={} exception={}",
                    mode, properties.evaluatorModel(), status, isRetryable(status), ex.getClass().getSimpleName());
            throw new IllegalStateException(providerMessage(ex));
        } catch (JsonProcessingException ex) {
            log.warn("[PracticePdfAI] operation=provider-parse mode={} model={} exception={}",
                    mode, properties.evaluatorModel(), ex.getClass().getSimpleName());
            throw new IllegalStateException("AI chưa tạo được câu hỏi từ PDF. Vui lòng xem log chi tiết ở console.");
        } catch (RuntimeException ex) {
            log.error("[PracticePdfAI] operation=provider-parse mode={} model={} exception={}",
                    mode, properties.evaluatorModel(), ex.getClass().getSimpleName(), ex);
            throw new IllegalStateException("AI chưa tạo được câu hỏi từ PDF. Vui lòng xem log chi tiết ở console.", ex);
        }
    }

    private String callWithRetry(Map<String, Object> request, String mode) {
        int maxRetries = 3;
        long backoffMs = 1500;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.info("[PracticePdfAI] mode={} attempt={}/{} POST /chat/completions model={}",
                        mode, attempt, maxRetries + 1, properties.evaluatorModel());
                String response = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                log.info("[PracticePdfAI] mode={} attempt={} success", mode, attempt);
                return response;
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = isRetryable(status) && attempt <= maxRetries;
                log.warn("[PracticePdfAI] operation=provider-call mode={} model={} attempt={} status={} retryable={} exception={}",
                        mode, properties.evaluatorModel(), attempt, status, retryable, ex.getClass().getSimpleName());
                if (retryable) {
                    log.info("[PracticePdfAI] mode={} sleepingMs={} beforeRetryAttempt={}",
                            mode, backoffMs, attempt + 1);
                    sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                throw ex;
            }
        }
        throw new IllegalStateException("Max retries exceeded for PDF question generation.");
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private GeneratedGroup toGeneratedGroup(JsonNode node, boolean forceMcq) {
        String id = node.path("id").asText("").trim();
        int from = node.path("from").asInt(0);
        int to = node.path("to").asInt(0);
        String instruction = node.path("instruction").asText("").trim();
        String audioUrl = node.path("audioUrl").asText("").trim();
        if (audioUrl.isEmpty()) {
            audioUrl = null;
        }

        GeneratedExampleBox exampleBox = null;
        JsonNode exNode = node.path("exampleBox");
        if (exNode.isObject() && !exNode.path("content").asText("").trim().isEmpty()) {
            String label = exNode.path("label").asText("<보기>").trim();
            String content = exNode.path("content").asText("").trim();
            List<String> choices = new ArrayList<>();
            JsonNode choicesNode = exNode.path("choices");
            if (choicesNode.isArray()) {
                choicesNode.forEach(choice -> {
                    String val = choice.asText("").trim();
                    if (!val.isEmpty()) {
                        choices.add(val);
                    }
                });
            }
            int answer = exNode.path("answer").asInt(0);
            exampleBox = new GeneratedExampleBox(label, content, choices.isEmpty() ? null : choices, answer > 0 ? answer : null);
        }

        List<GeneratedQuestion> questions = new ArrayList<>();
        JsonNode questionsNode = node.path("questions");
        if (questionsNode.isArray()) {
            questionsNode.forEach(qNode -> {
                GeneratedQuestion q = toGeneratedQuestion(qNode, forceMcq);
                if (q != null) {
                    questions.add(q);
                }
            });
        }

        if (id.isEmpty()) {
            id = from + "-" + to;
        }

        return new GeneratedGroup(id, from, to, instruction, audioUrl, exampleBox, questions);
    }

    private GeneratedQuestion toGeneratedQuestion(JsonNode node, boolean forceMcq) {
        int questionNo = node.path("questionNo").asInt(0);
        String type = normalizeType(node.path("questionType").asText(""));
        if (forceMcq) {
            type = PracticeQuestion.TYPE_MCQ;
        }
        String prompt = node.path("prompt").asText("").trim();
        if (questionNo <= 0) {
            log.warn("[PracticePdfAI] rejectQuestion reason=missing_required questionNo={}", questionNo);
            return null;
        }

        List<String> options = new ArrayList<>();
        JsonNode optionsNode = node.path("options");
        if (optionsNode.isArray()) {
            optionsNode.forEach(option -> {
                String value = option.asText("").trim();
                if (!value.isBlank()) {
                    options.add(value);
                }
            });
        }
        if (PracticeQuestion.TYPE_MCQ.equals(type) && options.size() < 2) {
            log.warn("[PracticePdfAI] rejectQuestion reason=mcq_needs_options questionNo={} optionCount={}",
                    questionNo, options.size());
            return null;
        }

        String answerKey = node.path("answerKey").asText("").trim();
        String explanation = node.path("explanationVi").asText("").trim();
        BigDecimal points = BigDecimal.valueOf(node.path("points").asDouble(defaultPoints(type)));
        log.info("[PracticePdfAI] acceptQuestion no={} type={} options={} hasKey={} points={}",
                questionNo, type, options.size(), !answerKey.isBlank(), points);
        return new GeneratedQuestion(questionNo, type, prompt, options, answerKey, explanation, points);
    }

    private static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (PracticeQuestion.TYPE_ESSAY.equals(normalized)
                || PracticeQuestion.TYPE_SPEAKING.equals(normalized)
                || PracticeQuestion.TYPE_SHORT_TEXT.equals(normalized)
                || PracticeQuestion.TYPE_TRUE_FALSE_NOT_GIVEN.equals(normalized)
                || PracticeQuestion.TYPE_MATCHING_INFORMATION.equals(normalized)
                || PracticeQuestion.TYPE_FILL_BLANK.equals(normalized)
                || PracticeQuestion.TYPE_ORDERING.equals(normalized)
                || PracticeQuestion.TYPE_TEXT_COMPLETION.equals(normalized)) {
            return normalized;
        }
        return PracticeQuestion.TYPE_MCQ;
    }

    private static boolean forceTopikMcq(String skill, String topikLevel) {
        String normalizedSkill = skill == null ? "" : skill.trim().toUpperCase();
        String normalizedLevel = topikLevel == null ? "" : topikLevel.trim().toUpperCase();
        boolean officialTopik = "TOPIK_I".equals(normalizedLevel) || "TOPIK_II".equals(normalizedLevel);
        boolean keyOnlySkill = "READING".equals(normalizedSkill) || "LISTENING".equals(normalizedSkill);
        return officialTopik && keyOnlySkill;
    }

    private static double defaultPoints(String type) {
        return PracticeQuestion.TYPE_ESSAY.equals(type) || PracticeQuestion.TYPE_SPEAKING.equals(type) ? 100.0 : 1.0;
    }

    private String userPayload(String pdfText, String skill, String topikLevel, String examTemplate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill", skill);
        payload.put("topik_level", topikLevel);
        payload.put("exam_template", examTemplate == null ? "TOPIK_MCQ" : examTemplate);
        payload.put("pdf_text", pdfText);

        List<String> instructions = new ArrayList<>(List.of(
                "Bạn PHẢI gom câu hỏi theo group đúng cấu trúc đề TOPIK (ví dụ: nhóm 1-2, 3-4, 5-8, 9-12, 13-16, 17-21, 22-26...).",
                "Mỗi group chứa instruction (chỉ dẫn), audioUrl (nếu có), exampleBox (ví dụ <보기> nếu có), và mảng questions của nhóm đó.",
                "Giữ đúng thứ tự câu hỏi xuất hiện trong PDF.",
                "questionNo là số câu thật trên đề nếu nhận diện được.",
                "Tạo answerKey nếu PDF có đáp án, hoặc nếu câu hỏi có thể suy luận chắc chắn từ nội dung đề.",
                "Nếu không chắc đáp án thì để answerKey rỗng.",
                "explanationVi dùng tiếng Việt, ngắn gọn, không dùng tiếng Anh."
        ));

        if ("TOPIK_WRITING".equals(examTemplate)) {
            instructions.add("Đây là đề viết TOPIK (câu 51-54). Dùng ESSAY cho câu 53-54. Có thể dùng FILL_BLANK hoặc SHORT_TEXT cho câu 51-52.");
        } else if ("GENERAL_EXTENDED".equals(examTemplate)) {
            instructions.add("Đây là bài tập mở rộng. Cho phép sử dụng các dạng câu hỏi: TRUE_FALSE_NOT_GIVEN, MATCHING_INFORMATION, FILL_BLANK, ORDERING, TEXT_COMPLETION, SHORT_TEXT.");
        } else if ("GENERAL_SPEAKING".equals(examTemplate)) {
            instructions.add("Đây là đề nói. Sử dụng dạng câu hỏi SPEAKING.");
        } else {
            instructions.add("Đây là đề Đọc/Nghe TOPIK chính thức. Bắt buộc tất cả câu hỏi đều là MCQ (trắc nghiệm) với 4 phương án lựa chọn.");
        }

        payload.put("instructions", instructions);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build PDF question generation payload.", ex);
        }
    }

    private Map<String, Object> multimodalUserMessage(List<String> pageImageDataUrls, String skill, String topikLevel, String examTemplate) {
        List<Map<String, Object>> content = new ArrayList<>();
        String templateDesc = examTemplate == null ? "TOPIK_MCQ" : examTemplate;
        content.add(Map.of("type", "text", "text", """
                Hãy đọc các ảnh trang PDF bên dưới và tạo các nhóm câu hỏi luyện tập theo đúng thứ tự xuất hiện (group-based).
                skill=%s
                topik_level=%s
                exam_template=%s
                Phải gom câu hỏi vào từng group đúng cấu trúc TOPIK (ví dụ 17-21). Với mỗi group phải bóc tách instruction và <보기> (ví dụ mẫu) nếu có.
                Tạo answerKey nếu ảnh có đáp án, hoặc nếu câu hỏi có thể suy luận chắc chắn từ nội dung đề.
                Nếu không chắc đáp án thì để answerKey rỗng.
                """.formatted(skill, topikLevel, templateDesc)));
        for (String dataUrl : pageImageDataUrls) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUrl)
            ));
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);
        return message;
    }

    private static String systemPrompt() {
        return """
                Bạn là bộ phân tích đề TOPIK tiếng Hàn cho hệ thống KSH Korean Study Hub.
                Nhiệm vụ của bạn là đọc PDF đề thi/luyện tập và chuyển thành danh sách CÁC NHÓM CÂU HỎI (groups) đúng cấu trúc TOPIK PDF để backend lưu DB.
                
                Quy tắc bắt buộc:
                - Chỉ dùng tiếng Việt cho giải thích, tiếng Hàn cho nội dung đề/đáp án.
                - Phải gom câu hỏi thành từng nhóm thích hợp (ví dụ: nhóm 17-21).
                - Mỗi nhóm (group) có instruction riêng (ví dụ: "[17~21] 다음을 듣고 <보기>와 같이...").
                - Nếu nhóm có ví dụ mẫu <보기>, hãy bóc tách thông tin hội thoại/nội dung vào `exampleBox.content`, 4 phương án vào `exampleBox.choices` và vị trí đáp án đúng vào `exampleBox.answer`.
                - Với câu trắc nghiệm (MCQ) hoặc các dạng câu hỏi có các lựa chọn được đánh dấu ①, ②, ③, ④: phần `prompt` của câu hỏi chỉ được chứa tiêu đề hoặc câu hỏi yêu cầu ngắn gọn, TUYỆT ĐỐI KHÔNG chép lại hoặc lặp lại nội dung các lựa chọn ①, ②, ③, ④ vào phần `prompt`. Các lựa chọn này phải được tách riêng vào mảng `options`.
                - Với đề viết, dùng questionType=ESSAY.
                - Với đề nói, dùng questionType=SPEAKING và options là mảng rỗng.
                - Không trả markdown. Chỉ trả JSON đúng schema.
                """;
    }

    private String providerMessage(HttpStatusCodeException ex) {
        int status = ex.getStatusCode().value();
        if (status == 503) {
            return "Model AI đang quá tải từ phía provider (503). Backend đã retry nhưng provider vẫn báo high demand.";
        }
        if (status == 429) {
            return "Model AI đang bị giới hạn lượt gọi (429). Backend đã retry nhưng chưa thành công.";
        }
        return "AI provider trả lỗi " + status + ".";
    }

    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static String safeExamTemplateForLog(String examTemplate) {
        if (examTemplate == null || examTemplate.isBlank()) {
            return "TOPIK_MCQ";
        }
        return switch (examTemplate) {
            case "TOPIK_WRITING", "GENERAL_EXTENDED", "GENERAL_SPEAKING" -> examTemplate;
            default -> "OTHER";
        };
    }

    private static void sleep(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", Map.of(
                "name", "ksh_practice_pdf_groups",
                "strict", Boolean.TRUE,
                "schema", schema()
        ));
        return responseFormat;
    }

    private Map<String, Object> schema() {
        // ExampleBox schema (all fields required)
        Map<String, Object> exampleBox = objectSchema(
                list("label", "content", "choices", "answer"),
                prop("label", typed("string"),
                        "content", typed("string"),
                        "choices", arrayOf(typed("string")),
                        "answer", typed("integer")));

        // Question schema (all fields required)
        Map<String, Object> question = objectSchema(
                list("questionNo", "questionType", "prompt", "options", "answerKey", "explanationVi", "points"),
                prop("questionNo", typed("integer"),
                        "questionType", typed("string"),
                        "prompt", typed("string"),
                        "options", arrayOf(typed("string")),
                        "answerKey", typed("string"),
                        "explanationVi", typed("string"),
                        "points", typed("number")));

        // Group schema (all fields required)
        Map<String, Object> group = objectSchema(
                list("id", "from", "to", "instruction", "audioUrl", "exampleBox", "questions"),
                prop("id", typed("string"),
                        "from", typed("integer"),
                        "to", typed("integer"),
                        "instruction", typed("string"),
                        "audioUrl", typed("string"),
                        "exampleBox", exampleBox,
                        "questions", arrayOf(question)));

        return objectSchema(list("groups"), prop("groups", arrayOf(group)));
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

    private static Map<String, Object> message(String role, String content) {
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

    public record GeneratedExampleBox(String label, String content, List<String> choices, Integer answer) {
    }

    public record GeneratedQuestion(Integer questionNo, String questionType, String prompt,
                                    List<String> options, String answerKey,
                                    String explanationVi, BigDecimal points) {
    }

    public record GeneratedGroup(String id, Integer from, Integer to, String instruction,
                                 String audioUrl, GeneratedExampleBox exampleBox,
                                 List<GeneratedQuestion> questions) {
    }
}
