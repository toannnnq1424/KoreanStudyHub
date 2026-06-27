package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PracticePdfPayloadPreviewService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfPayloadPreviewService.class);

    private final PracticePdfAiPayloadBuilder payloadBuilder;
    private final PracticePdfRegionAnnotationRepository annotationRepository;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public PracticePdfPayloadPreviewService(PracticePdfAiPayloadBuilder payloadBuilder,
                                            PracticePdfRegionAnnotationRepository annotationRepository,
                                            OpenAiProperties properties,
                                            ObjectMapper objectMapper) {
        this.payloadBuilder = payloadBuilder;
        this.annotationRepository = annotationRepository;
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

        // Format regions metadata
        List<Map<String, Object>> regionsList = new ArrayList<>();
        List<PracticePdfRegionAnnotation> allAnnos = annotationRepository.findBySessionIdOrderByPageNumberAscDisplayOrderAsc(session.getId());
        for (PracticePdfRegionAnnotation ann : allAnnos) {
            if (ann.getPageNumber() >= session.getSelectedStartPage() && ann.getPageNumber() <= session.getSelectedEndPage()) {
                if ("IGNORE".equalsIgnoreCase(ann.getRegionType())) continue;

                Map<String, Object> regMap = new LinkedHashMap<>();
                regMap.put("regionId", "region-" + ann.getId());
                regMap.put("page", ann.getPageNumber());
                regMap.put("type", ann.getRegionType());
                regMap.put("note", ann.getLecturerNote());
                regMap.put("includeText", ann.getIncludeTextInAi() != false);
                regMap.put("includeImage", ann.getIncludeImageInAi() != false);
                regMap.put("saveToAssetLibrary", ann.getSaveToAssetLibrary() == true);
                regionsList.add(regMap);
            }
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

        return new PayloadPreviewDto(sysPrompt, model, strategy, stats, regionsList, cropsList, jsonPreviewStr);
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

    public record PayloadPreviewDto(
            String systemPrompt,
            String model,
            String strategy,
            Map<String, Object> stats,
            List<Map<String, Object>> regions,
            List<Map<String, Object>> crops,
            String requestJsonPreview
    ) {}
}
