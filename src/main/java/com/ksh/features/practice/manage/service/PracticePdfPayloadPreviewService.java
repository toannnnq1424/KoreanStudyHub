package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.ai.OpenAiProperties;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PracticePdfPayloadPreviewService {

    private final PracticePdfAiPayloadBuilder payloadBuilder;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public PracticePdfPayloadPreviewService(PracticePdfAiPayloadBuilder payloadBuilder,
                                            OpenAiProperties properties,
                                            ObjectMapper objectMapper) {
        this.payloadBuilder = payloadBuilder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PayloadPreviewDto getPreview(PracticePdfImportSession session) {
        // Build unified payload from the builder
        PracticePdfAiPayloadBuilder.PayloadInfo info = payloadBuilder.buildPayload(session);

        // System prompt and model from orchestrator properties
        String sysPrompt = properties.apiKey() != null ? systemPrompt() : "Mock System Prompt (API Key not set)";
        String model = properties.evaluatorModel();
        String strategy = session.getExtractionStrategy();

        // Calculate statistics
        int rawChars = info.statsSummary().containsKey("rawTextCharacters") ? Integer.parseInt(info.statsSummary().get("rawTextCharacters").toString()) : 0;
        int selectedChars = info.statsSummary().containsKey("selectedTextCharacters") ? Integer.parseInt(info.statsSummary().get("selectedTextCharacters").toString()) : 0;
        int finalSentChars = info.statsSummary().containsKey("finalSentTextCharacters") ? Integer.parseInt(info.statsSummary().get("finalSentTextCharacters").toString()) : 0;
        int cropCount = info.crops().size();
        long cropBytes = info.statsSummary().containsKey("estimatedImageBytes") ? Long.parseLong(info.statsSummary().get("estimatedImageBytes").toString()) : 0L;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("rawTextChars", rawChars);
        stats.put("selectedTextChars", selectedChars);
        stats.put("finalSentTextChars", finalSentChars);
        stats.put("cropCount", cropCount);
        stats.put("cropBytes", cropBytes);
        stats.put("totalPages", session.getTotalPages());
        stats.put("selectedPagesCount", (session.getSelectedEndPage() - session.getSelectedStartPage() + 1));

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("targetTestNo", session.getTargetTestNo());
        context.put("targetSkill", session.getTargetSkill());
        context.put("targetLessonCode", session.getTargetLessonCode());
        context.put("pageFrom", session.getSelectedStartPage());
        context.put("pageTo", session.getSelectedEndPage());

        // Format regions metadata
        List<Map<String, Object>> regionsList = new ArrayList<>();
        for (com.ksh.features.practice.manage.dto.AiDocumentImportRequest.RegionPayload region
                : info.requestDto().getRegions()) {
            Map<String, Object> regMap = new LinkedHashMap<>();
            regMap.put("regionId", region.getRegionId());
            regMap.put("page", region.getPageNumber());
            regMap.put("type", region.getRegionType());
            regMap.put("note", region.getLecturerNote());
            regMap.put("includeText", Boolean.TRUE.equals(region.getSendText()));
            regMap.put("includeImage", Boolean.TRUE.equals(region.getSendImage()));
            regMap.put("saveToAssetLibrary", Boolean.TRUE.equals(region.getSaveToLibrary()));
            regionsList.add(regMap);
        }

        // Format crops info
        List<Map<String, Object>> cropsList = new ArrayList<>();
        for (PracticePdfAiPayloadBuilder.CropInfo crop : info.crops()) {
            Map<String, Object> cMap = new LinkedHashMap<>();
            cMap.put("regionId", crop.regionId());
            cMap.put("url", crop.url());
            cMap.put("sizeBytes", crop.byteSize());
            cMap.put("assetRef", crop.assetRef());
            cMap.put("placement", crop.placement());
            cMap.put("sentToAi", "YES");
            cropsList.add(cMap);
        }

        // Format request JSON preview matching exact orchestrator payload structure
        Map<String, Object> jsonPreview = new LinkedHashMap<>();
        jsonPreview.put("model", model);
        jsonPreview.put("temperature", 0.0);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", sysPrompt));

        List<Map<String, Object>> userContent = new ArrayList<>();

        // Add DTO payload to user content representation (retaining full metadata, OCR, ranges, hints)
        StringBuilder textPart = new StringBuilder();
        textPart.append("Dữ liệu base64 được ẩn trên UI nhưng request thật vẫn chứa ảnh.\n\n");
        textPart.append("Dưới đây là mô tả JSON cấu trúc annotations và text bóc tách từ PDF:\n");
        try {
            textPart.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info.requestDto()));
        } catch (Exception e) {
            textPart.append(info.requestDto().toString());
        }
        if (info.basePageRangeText() != null && !info.basePageRangeText().isBlank()) {
            textPart.append("\n\nVăn bản thô (raw text) bóc tách toàn phạm vi trang:\n")
                    .append(info.basePageRangeText());
        }
        userContent.add(Map.of("type", "text", "text", textPart.toString()));

        // Add IMAGE_REGION label markers and redacted image payloads
        for (PracticePdfAiPayloadBuilder.CropInfo crop : info.crops()) {
            String regionLabel = String.format(
                    "IMAGE_REGION\nregionId=%s\npageNumber=%d\nregionType=%s\nassetRef=%s\nplacement=%s\n",
                    crop.regionId(),
                    crop.pageNumber(),
                    crop.regionType(),
                    crop.assetRef(),
                    crop.placement()
            );
            userContent.add(Map.of("type", "text", "text", regionLabel));
            userContent.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "[REDACTED BASE64 data URL: regionId=" + crop.regionId() + ", size=" + (crop.byteSize() / 1024) + " KB]")
            ));
        }

        messages.add(Map.of("role", "user", "content", userContent));
        jsonPreview.put("messages", messages);

        String jsonPreviewStr = "";
        try {
            jsonPreviewStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonPreview);
        } catch (Exception e) {
            jsonPreviewStr = jsonPreview.toString();
        }

        return new PayloadPreviewDto(true, sysPrompt, model, strategy, context,
                stats, regionsList, cropsList, jsonPreviewStr);
    }

    private String systemPrompt() {
        return PracticePdfAiPromptRules.systemPrompt();
    }

    public record PayloadPreviewDto(
            boolean privilegedDetails,
            String systemPrompt,
            String model,
            String strategy,
            Map<String, Object> context,
            Map<String, Object> stats,
            List<Map<String, Object>> regions,
            List<Map<String, Object>> crops,
            String requestJsonPreview
    ) {
        public PayloadPreviewDto(boolean privilegedDetails, String systemPrompt, String model,
                                 String strategy, Map<String, Object> stats,
                                 List<Map<String, Object>> regions,
                                 List<Map<String, Object>> crops,
                                 String requestJsonPreview) {
            this(privilegedDetails, systemPrompt, model, strategy, Map.of(), stats,
                    regions, crops, requestJsonPreview);
        }

        public PayloadPreviewDto redacted() {
            return new PayloadPreviewDto(
                    false,
                    null,
                    null,
                    strategy,
                    context,
                    stats,
                    regions,
                    crops,
                    null
            );
        }
    }
}
