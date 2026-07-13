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
            audit.setErrorCode("AI_PROVIDER_CALL_FAILED");
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
                log.warn("[PdfAiOrchestrator] operation=provider-call model={} attempt={} status={} retryable={} exception={}",
                        properties.evaluatorModel(), attempt, status, retryable, ex.getClass().getSimpleName());
                if (retryable) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    backoffMs *= 2;
                    continue;
                }
                throw new IllegalStateException("AI provider trả lỗi " + status + ".");
            } catch (Exception ex) {
                log.warn("[PdfAiOrchestrator] operation=provider-call model={} exception={}",
                        properties.evaluatorModel(), ex.getClass().getSimpleName());
                throw new IllegalStateException("Không gọi được AI.");
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
        return PracticePdfAiPromptRules.systemPrompt();
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
                List.of("questionNo", "questionType", "prompt", "options", "answerKey", "explanationVi", "points", "confidence", "reviewRequired", "sourceRegionIds", "assets"),
                prop("questionNo", typed("integer"),
                        "questionType", Map.of(
                                "type", "string",
                                "enum", List.of("SINGLE_CHOICE", "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "ESSAY", "SPEAKING")),
                        "prompt", typed("string"),
                        "options", arrayOf(typed("string")),
                        "answerKey", typed("string"),
                        "explanationVi", typed("string"),
                        "points", typed("number"),
                        "confidence", typed("number"),
                        "reviewRequired", typed("boolean"),
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

        return objectSchema(
                List.of("documentTitle", "sections", "assets", "warnings"),
                prop("documentTitle", typed("string"),
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
