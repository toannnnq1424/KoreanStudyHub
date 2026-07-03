package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PracticeDocumentAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PracticeDocumentAnalyzer.class);
    private static final int MAX_TEXT_CHARS = 80_000;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PracticeDocumentAnalyzer(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public String analyzeText(String pdfText, String categoryHint) {
        if (pdfText == null || pdfText.isBlank()) {
            throw new IllegalArgumentException("Không đọc được chữ từ PDF.");
        }

        String truncated = pdfText.length() <= MAX_TEXT_CHARS ? pdfText : pdfText.substring(0, MAX_TEXT_CHARS);
        log.info("[DocumentAnalyzer] Start text analysis. Model={} Chars={}", properties.evaluatorModel(), truncated.length());

        Map<String, Object> request = baseRequest(List.of(
                message("system", systemPrompt()),
                message("user", String.format("Category hint: %s\n\nDocument Text:\n%s", categoryHint, truncated))
        ));

        return executeCall(request);
    }

    public String analyzeImages(List<String> imageDataUrls, String categoryHint) {
        if (imageDataUrls == null || imageDataUrls.isEmpty()) {
            throw new IllegalArgumentException("Không render được trang PDF thành ảnh.");
        }

        log.info("[DocumentAnalyzer] Start image multimodal analysis. Pages={}", imageDataUrls.size());

        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", String.format("Category hint: %s\n\nAnalyze this document pages:", categoryHint)));
        for (String url : imageDataUrls) {
            contentParts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
        }

        Map<String, Object> request = baseRequest(List.of(
                message("system", systemPrompt()),
                Map.of("role", "user", "content", contentParts)
        ));

        return executeCall(request);
    }

    private Map<String, Object> baseRequest(List<Map<String, Object>> messages) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("response_format", Map.of("type", "json_object"));
        request.put("messages", messages);
        return request;
    }

    private String executeCall(Map<String, Object> request) {
        int maxRetries = 3;
        long backoff = 1000;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                String responseBody = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode choice = root.path("choices").path(0);
                if (choice.path("message").hasNonNull("content")) {
                    return choice.path("message").path("content").asText();
                }
                return responseBody;
            } catch (HttpStatusCodeException ex) {
                log.warn("[DocumentAnalyzer] operation=provider-call model={} attempt={}/{} status={} retryable={} exception={}",
                        properties.evaluatorModel(), i, maxRetries, ex.getStatusCode().value(),
                        ex.getStatusCode().value() == 429 && i < maxRetries, ex.getClass().getSimpleName());
                if (ex.getStatusCode().value() == 429 && i < maxRetries) {
                    sleep(backoff);
                    backoff *= 2;
                } else {
                    throw sanitizedHttpException(ex);
                }
            } catch (Exception ex) {
                log.warn("[DocumentAnalyzer] operation=provider-call model={} attempt={}/{} exception={}",
                        properties.evaluatorModel(), i, maxRetries, ex.getClass().getSimpleName());
                if (i < maxRetries) {
                    sleep(backoff);
                    backoff *= 2;
                } else {
                    throw new RuntimeException("AI analyzer call failed");
                }
            }
        }
        throw new RuntimeException("AI analyzer retries exceeded");
    }

    private static HttpStatusCodeException sanitizedHttpException(HttpStatusCodeException ex) {
        return new HttpStatusCodeException(ex.getStatusCode(), ex.getStatusText()) {
        };
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String systemPrompt() {
        return "Bạn là hệ thống phân tích cấu trúc đề thi và bài luyện tiếng Hàn cho KSH.\n" +
                "Bạn không được giả định toàn bộ PDF chỉ có một kỹ năng duy nhất (như Đọc hay Nghe).\n" +
                "Trước tiên hãy phân tích xem tài liệu gồm có những phần thi (section) nào. Mỗi section phải ứng với một kỹ năng: LISTENING, READING, WRITING, hoặc SPEAKING.\n\n" +
                "Yêu cầu về cấu trúc trả về:\n" +
                "Hãy trả về JSON khớp CHÍNH XÁC với Schema sau đây:\n" +
                "{\n" +
                "  \"document\": {\n" +
                "    \"detectedCategory\": \"TOPIK_I\" | \"TOPIK_II\" | \"TOPIK_MIXED\" | \"GENERAL_KOREAN\" | \"CUSTOM\",\n" +
                "    \"title\": \"Tên bộ đề\",\n" +
                "    \"confidence\": 0.95\n" +
                "  },\n" +
                "  \"sections\": [\n" +
                "    {\n" +
                "      \"tempId\": \"section-listening-1\",\n" +
                "      \"title\": \"Phần nghe\",\n" +
                "      \"skill\": \"LISTENING\",\n" +
                "      \"displayOrder\": 0,\n" +
                "      \"durationMinutes\": 40,\n" +
                "      \"groups\": [\n" +
                "        {\n" +
                "          \"tempId\": \"group-1\",\n" +
                "          \"label\": \"1-3\",\n" +
                "          \"questionFrom\": 1,\n" +
                "          \"questionTo\": 3,\n" +
                "          \"instruction\": \"Hãy nghe và chọn bức tranh đúng.\",\n" +
                "          \"passageText\": null,\n" +
                "          \"transcriptText\": \"Nội dung bài nghe\",\n" +
                "          \"audioUrl\": null,\n" +
                "          \"exampleBox\": null,\n" +
                "          \"displayOrder\": 0,\n" +
                "          \"questions\": [\n" +
                "            {\n" +
                "              \"tempId\": \"q-1\",\n" +
                "              \"questionNo\": 1,\n" +
                "              \"questionType\": \"SINGLE_CHOICE\",\n" +
                "              \"prompt\": \"Chọn tranh phù hợp với đoạn đối thoại.\",\n" +
                "              \"options\": [\n" +
                "                { \"id\": \"1\", \"label\": \"①\", \"text\": \"Tranh 1\" },\n" +
                "                { \"id\": \"2\", \"label\": \"②\", \"text\": \"Tranh 2\" },\n" +
                "                { \"id\": \"3\", \"label\": \"③\", \"text\": \"Tranh 3\" },\n" +
                "                { \"id\": \"4\", \"label\": \"④\", \"text\": \"Tranh 4\" }\n" +
                "              ],\n" +
                "              \"answer\": { \"type\": \"SINGLE\", \"value\": \"1\" },\n" +
                "              \"explanationVi\": \"Giải thích vì sao chọn ①\",\n" +
                "              \"points\": 2.0\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"warnings\": []\n" +
                "}\n\n" +
                "Quy tắc quan trọng:\n" +
                "1. Giữ đúng thứ tự xuất hiện của section, group, và question.\n" +
                "2. Không được tự ý gom câu hỏi theo các khoảng cứng nhắc như 1-2, 3-4 mà phải phân tích theo đề thực tế. Mỗi câu có thể đứng riêng thành một group nếu nó dùng tài liệu riêng.\n" +
                "3. Các câu trắc nghiệm TOPIK dùng SINGLE_CHOICE. Các câu custom hỗ trợ MULTIPLE_CHOICE, TRUE_FALSE_NOT_GIVEN, MATCHING, GAP_FILL, ORDERING, ESSAY, SPEAKING.\n" +
                "4. Không được chứa đáp án options bên trong prompt của câu hỏi. Options phải lưu rõ ràng trong array options.\n" +
                "5. Tuyệt đối không trả về markdown block (như ```json) hay text ngoài JSON.";
    }
}
