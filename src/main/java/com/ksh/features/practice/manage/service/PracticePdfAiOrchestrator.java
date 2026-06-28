package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAiRequestAudit;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PracticePdfAiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfAiOrchestrator.class);

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final PracticeAiRequestAuditRepository auditRepository;

    public PracticePdfAiOrchestrator(OpenAiProperties properties, ObjectMapper objectMapper,
                                     PracticeAiRequestAuditRepository auditRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditRepository = auditRepository;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public String callAi(PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo, Long sessionId, String strategy) {
        requireApiKey();

        log.info("[PdfAiOrchestrator] Preparing Chat Completions payload for sessionId={}", sessionId);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt()));
        messages.add(multimodalMessage(payloadInfo));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("response_format", responseFormat());
        request.put("messages", messages);

        // Prepare audit record
        PracticeAiRequestAudit audit = new PracticeAiRequestAudit();
        audit.setSessionId(sessionId);
        audit.setPromptVersion("practice-import-v3");
        audit.setModel(properties.evaluatorModel());
        audit.setStrategy(strategy);

        int sentText = payloadInfo.statsSummary().containsKey("finalSentTextCharacters")
                ? Integer.parseInt(payloadInfo.statsSummary().get("finalSentTextCharacters").toString()) : 0;
        long sentImageBytes = payloadInfo.statsSummary().containsKey("estimatedImageBytes")
                ? Long.parseLong(payloadInfo.statsSummary().get("estimatedImageBytes").toString()) : 0L;

        audit.setSentTextChars(sentText);
        audit.setSentImageCount(payloadInfo.crops().size());
        audit.setSentImageBytes(sentImageBytes);
        audit.setCreatedAt(LocalDateTime.now());

        try {
            // Serialize summary config
            Map<String, Object> summaryConfig = new LinkedHashMap<>();
            summaryConfig.put("strategy", strategy);
            summaryConfig.put("cropsCount", payloadInfo.crops().size());
            if (payloadInfo.requestDto().getRequestMeta() != null) {
                summaryConfig.put("requestId", payloadInfo.requestDto().getRequestMeta().getRequestId());
                summaryConfig.put("schemaVersion", payloadInfo.requestDto().getRequestMeta().getSchemaVersion());
            }
            audit.setPayloadSummaryJson(objectMapper.writeValueAsString(summaryConfig));
        } catch (Exception e) {
            log.warn("[PdfAiOrchestrator] Failed to serialize summary info", e);
        }

        try {
            String response = executeWithRetry(request);
            audit.setStatus("SUCCESS");
            auditRepository.save(audit);
            return response;
        } catch (Exception e) {
            audit.setStatus("FAILED");
            audit.setErrorCode(e.getMessage());
            auditRepository.save(audit);
            throw e;
        }
    }



    private void requireApiKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Chưa cấu hình API key AI cho hệ thống.");
        }
    }

    private String executeWithRetry(Map<String, Object> request) {
        int maxRetries = 2;
        long backoffMs = 1500;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                String response = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(response);
                JsonNode choice = root.path("choices").path(0);
                if (choice.path("message").hasNonNull("content")) {
                    return choice.path("message").path("content").asText();
                }
                if (root.hasNonNull("output_text")) {
                    return root.path("output_text").asText();
                }
                return response;
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = (status == 429 || status == 500 || status == 502 || status == 503 || status == 504) && attempt <= maxRetries;
                log.warn("[PdfAiOrchestrator] Attempt={} failed status={} body={}", attempt, status, ex.getResponseBodyAsString());
                if (retryable) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    backoffMs *= 2;
                    continue;
                }
                throw new IllegalStateException("AI provider trả lỗi " + status + ": " + ex.getResponseBodyAsString(), ex);
            } catch (Exception ex) {
                log.error("[PdfAiOrchestrator] Non-HTTP error during execution", ex);
                throw new IllegalStateException("Không gọi được AI: " + ex.getMessage(), ex);
            }
        }
        throw new IllegalStateException("Đã thử lại nhiều lần gọi AI nhưng không thành công.");
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private Map<String, Object> multimodalMessage(PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo) {
        List<Map<String, Object>> content = new ArrayList<>();

        StringBuilder textPrompt = new StringBuilder();
        textPrompt.append("IMPORTANT: pageContexts are context only. Do not create sections, groups, questions, options, answers or assets from pageContexts unless the same content is tied to sourceRegionIds from regions.\n");
        textPrompt.append("Dưới đây là mô tả JSON cấu trúc annotations và text bóc tách từ PDF:\n");
        try {
            textPrompt.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloadInfo.requestDto()));
        } catch (Exception e) {
            textPrompt.append(payloadInfo.requestDto().toString());
        }

        if (payloadInfo.basePageRangeText() != null && !payloadInfo.basePageRangeText().isBlank()) {
            textPrompt.append("\n\nVăn bản thô (raw text) bóc tách toàn phạm vi trang:\n")
                    .append(payloadInfo.basePageRangeText());
        }

        content.add(Map.of("type", "text", "text", textPrompt.toString()));

        // Add each crop image preceded by an IMAGE_REGION label block
        for (PracticePdfAiPayloadBuilder.CropInfo crop : payloadInfo.crops()) {
            // Build the image region label
            String regionLabel = String.format(
                    "IMAGE_REGION\nregionId=%s\npageNumber=%d\nregionType=%s\nassetRef=%s\nplacement=%s\n",
                    crop.regionId(),
                    crop.pageNumber(),
                    crop.regionType(),
                    crop.assetRef(),
                    crop.placement()
            );

            content.add(Map.of("type", "text", "text", regionLabel));
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", crop.base64DataUrl())
            ));
        }

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", content);
        return msg;
    }

    private String systemPrompt() {
        return """
                Bạn là bộ phân tích tài liệu thi tiếng Hàn của KSH Korean Study Hub.

                Bạn chỉ nhận các vùng tài liệu đã được giảng viên lựa chọn từ PDF.
                Bạn không nhận toàn bộ PDF và không được suy diễn nội dung ngoài các vùng được cung cấp.

                Mục tiêu của bạn là chuyển các vùng được cung cấp thành bản nháp đề thi có cấu trúc:
                Document -> Sections -> Groups -> Questions -> Options / Answers / Assets

                NGUYÊN TẮC NGUỒN SỰ THẬT:
                1. Chỉ sử dụng các region có trong input.
                2. Bỏ qua hoàn toàn region có regionType=IGNORE.
                3. Metadata đã bị lecturer khóa là nguồn sự thật cao nhất.
                4. Không tự di chuyển region sang section/group khác nếu region đã có sectionTempId hoặc groupTempId.
                5. Không tự thay đổi expectedQuestionType nếu lecturer đã chọn khác AUTO_DETECT.
                6. Không thay đổi expectedQuestionFrom và expectedQuestionTo trừ khi nội dung mâu thuẫn rõ ràng; nếu mâu thuẫn phải tạo warning.
                7. Không hardcode các nhóm như 1–2, 3–4, 17–21.
                8. Group có thể là 1–5, 6–8, 11–14 hoặc phạm vi bất kỳ.
                9. Giữ thứ tự pageNumber, displayOrder và thứ tự câu trên đề.
                10. Không tạo section/group/question nếu không có sourceRegionIds.

                LIÊN KẾT ẢNH:
                11. Mỗi ảnh được gửi ngay sau một nhãn IMAGE_REGION.
                12. Nhãn IMAGE_REGION xác định regionId của ảnh ngay sau nó.
                13. Không gán ảnh sang region khác.
                14. IMAGE_ASSET chỉ được tham chiếu bằng assetRef.
                15. Không trả base64, data URL hoặc mô tả thay thế file ảnh.
                16. placement của asset phải giữ theo dữ liệu lecturer.
                17. Nếu ảnh bị cắt thiếu hoặc không đọc được, tạo warning; không bịa nội dung.

                XỬ LÝ REGION:
                18. INSTRUCTION là hướng dẫn, không phải câu hỏi.
                19. PASSAGE là bài đọc dùng chung.
                20. TRANSCRIPT là lời thoại hoặc ngữ liệu nghe dùng chung.
                21. QUESTION_BLOCK có thể chứa một hoặc nhiều câu hỏi.
                22. QUESTION_PROMPT chỉ chứa nội dung câu hỏi.
                23. OPTIONS chỉ chứa các lựa chọn.
                24. ANSWER_KEY chỉ chứa đáp án.
                25. EXAMPLE_BOX là ví dụ mẫu, không tính là câu thật.
                26. AUTO_DETECT chỉ được tự xác định dựa trên crop và OCR text của chính region đó.
                27. Không chuyển nội dung từ region này sang region khác nếu không có bằng chứng rõ ràng.

                MCQ:
                28. Prompt không được lặp lại options.
                29. Các phương án ①, ②, ③, ④ phải tách riêng.
                30. answerKey dùng chỉ số nhất quán 1, 2, 3, 4.
                31. Nếu không chắc answerKey, để chuỗi rỗng và tạo warning.
                32. Không đoán đáp án chỉ để làm đầy dữ liệu.

                TOPIK:
                33. TOPIK I/II Reading và Listening chính thức mặc định là MCQ bốn phương án.
                34. TOPIK Writing có thể gồm câu 51, 52, 53, 54 với loại khác nhau.
                35. Không áp quy tắc TOPIK chính thức cho EXTENDED_PRACTICE hoặc CUSTOM nếu lecturer chọn loại câu khác.
                36. Section kỹ năng có thể là LISTENING, READING, WRITING, SPEAKING hoặc MIXED.
                37. Một document có thể chứa nhiều section kỹ năng.

                NGÔN NGỮ:
                38. Giữ nguyên nội dung tiếng Hàn.
                39. explanationVi và warning phải dùng tiếng Việt.
                40. Không dùng tiếng Anh trong phần giải thích dành cho học viên.

                TRACEABILITY:
                41. Mỗi section phải có sourceRegionIds.
                42. Mỗi group phải có sourceRegionIds.
                43. Mỗi question phải có sourceRegionIds.
                44. Mỗi asset usage phải có sourceRegionId và assetRef.
                45. sourceRegionIds không được chứa ID không tồn tại trong input.

                CHỐNG HALLUCINATION:
                46. Không thêm câu hỏi không xuất hiện trong region.
                47. Không tự viết thêm passage hoặc transcript.
                48. Không tự tạo options còn thiếu.
                49. Không tạo answerKey nếu không đủ bằng chứng.
                50. Không hợp nhất hai vùng chỉ vì chúng nằm gần nhau nếu lecturer đã gắn group khác nhau.
                51. Nếu dữ liệu không đủ, trả draft chưa hoàn chỉnh kèm warning thay vì bịa.

                Trả về JSON đúng response schema, không markdown và không thêm văn bản ngoài JSON.
                """;
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", Map.of(
                "name", "pdf_import_response",
                "strict", Boolean.TRUE,
                "schema", schema()
        ));
        return responseFormat;
    }

    private Map<String, Object> schema() {
        Map<String, Object> asset = objectSchema(
                List.of("assetRef", "sourceRegionId", "placement", "caption"),
                prop("assetRef", typed("string"),
                        "sourceRegionId", typed("string"),
                        "placement", typed("string"),
                        "caption", typed("string"))
        );

        Map<String, Object> question = objectSchema(
                List.of("questionNo", "questionType", "prompt", "options", "answerKey", "explanationVi", "points", "sourceRegionIds", "assets"),
                prop("questionNo", typed("integer"),
                        "questionType", typed("string"),
                        "prompt", typed("string"),
                        "options", arrayOf(typed("string")),
                        "answerKey", typed("string"),
                        "explanationVi", typed("string"),
                        "points", typed("number"),
                        "sourceRegionIds", arrayOf(typed("string")),
                        "assets", arrayOf(asset))
        );

        Map<String, Object> group = objectSchema(
                List.of("clientId", "label", "displayOrder", "questionFrom", "questionTo", "instruction", "passage", "transcript", "audioRef", "sourceRegionIds", "assets", "questions"),
                prop("clientId", typed("string"),
                        "label", typed("string"),
                        "displayOrder", typed("integer"),
                        "questionFrom", nullableTyped("integer"),
                        "questionTo", nullableTyped("integer"),
                        "instruction", typed("string"),
                        "passage", typed("string"),
                        "transcript", typed("string"),
                        "audioRef", nullableTyped("string"),
                        "sourceRegionIds", arrayOf(typed("string")),
                        "assets", arrayOf(asset),
                        "questions", arrayOf(question))
        );

        Map<String, Object> section = objectSchema(
                List.of("clientId", "label", "skill", "displayOrder", "durationMinutes", "sourceRegionIds", "groups"),
                prop("clientId", typed("string"),
                        "label", typed("string"),
                        "skill", typed("string"),
                        "displayOrder", typed("integer"),
                        "durationMinutes", nullableTyped("integer"),
                        "sourceRegionIds", arrayOf(typed("string")),
                        "groups", arrayOf(group))
        );

        Map<String, Object> warning = objectSchema(
                List.of("code", "severity", "regionIds", "messageVi"),
                prop("code", typed("string"),
                        "severity", typed("string"),
                        "regionIds", arrayOf(typed("string")),
                        "messageVi", typed("string"))
        );

        Map<String, Object> documentAssessment = objectSchema(
                List.of("categoryFit", "sourceQuality", "needsLecturerReview", "messageVi"),
                prop("categoryFit", typed("string"),
                        "sourceQuality", typed("string"),
                        "needsLecturerReview", typed("boolean"),
                        "messageVi", typed("string"))
        );

        return objectSchema(
                List.of("documentTitle", "examCategory", "documentAssessment", "sections", "assets", "warnings"),
                prop("documentTitle", typed("string"),
                        "examCategory", typed("string"),
                        "documentAssessment", documentAssessment,
                        "sections", arrayOf(section),
                        "assets", arrayOf(asset),
                        "warnings", arrayOf(warning))
        );
    }

    private static Map<String, Object> typed(String type) {
        return Map.of("type", type);
    }

    private static Map<String, Object> nullableTyped(String type) {
        return Map.of("type", List.of(type, "null"));
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
}
