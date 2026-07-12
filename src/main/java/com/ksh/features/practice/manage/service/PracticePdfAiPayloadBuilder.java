package com.ksh.features.practice.manage.service;

import com.ksh.entities.*;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest.*;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator.ValidationError;
import com.ksh.features.practice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PracticePdfAiPayloadBuilder {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfAiPayloadBuilder.class);

    private final PracticePdfRegionAnnotationRepository annotationRepository;
    private final PracticePdfImportSectionDraftRepository sectionDraftRepository;
    private final PracticePdfImportGroupDraftRepository groupDraftRepository;
    private final PracticePdfPageExtractionService pageExtractionService;
    private final PracticePdfCropService cropService;
    private final LecturerAssetRepository assetRepository;
    private final AssetStorageService assetStorage;
    private final ImportAiPayloadValidator payloadValidator;

    public PracticePdfAiPayloadBuilder(PracticePdfRegionAnnotationRepository annotationRepository,
                                       PracticePdfImportSectionDraftRepository sectionDraftRepository,
                                       PracticePdfImportGroupDraftRepository groupDraftRepository,
                                       PracticePdfPageExtractionService pageExtractionService,
                                       PracticePdfCropService cropService,
                                       LecturerAssetRepository assetRepository,
                                       AssetStorageService assetStorage,
                                       ImportAiPayloadValidator payloadValidator) {
        this.annotationRepository = annotationRepository;
        this.sectionDraftRepository = sectionDraftRepository;
        this.groupDraftRepository = groupDraftRepository;
        this.pageExtractionService = pageExtractionService;
        this.cropService = cropService;
        this.assetRepository = assetRepository;
        this.assetStorage = assetStorage;
        this.payloadValidator = payloadValidator;
    }

    public PayloadInfo buildPayload(PracticePdfImportSession session) {
        Long sessionId = session.getId();
        String pdfPath = session.getStoredPdfPath();
        int startPage = session.getSelectedStartPage();
        int endPage = session.getSelectedEndPage();
        String strategy = session.getExtractionStrategy() != null ? session.getExtractionStrategy() : "HYBRID";

        // 1. Load section & group drafts
        List<PracticePdfImportSectionDraft> sectionDrafts = sectionDraftRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        List<PracticePdfImportGroupDraft> groupDrafts = groupDraftRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);

        // 2. Load active regions within page range
        List<PracticePdfRegionAnnotation> allAnnos = annotationRepository.findBySessionIdOrderByPageNumberAscDisplayOrderAsc(sessionId);
        List<PracticePdfRegionAnnotation> inRangeAnnos = new ArrayList<>();
        int ignoredCount = 0;

        for (PracticePdfRegionAnnotation ann : allAnnos) {
            if (ann.getPageNumber() >= startPage && ann.getPageNumber() <= endPage) {
                if ("IGNORE".equalsIgnoreCase(ann.getRegionType())) {
                    ignoredCount++;
                } else if (Boolean.TRUE.equals(ann.getIncludeInAi())) {
                    inRangeAnnos.add(ann);
                }
            } else {
                ignoredCount++;
            }
        }

        // 3. Extract raw text from page range for total character stats
        StringBuilder rawTextBuilder = new StringBuilder();
        List<PageContext> pageContexts = new ArrayList<>();
        Map<Integer, String> rawTextByPage = new LinkedHashMap<>();
        int totalRawChars = 0;
        for (int p = startPage; p <= endPage; p++) {
            PracticePdfPageExtraction ext = pageExtractionService.extractOrGetPageText(session, p);
            if (ext.getRawText() != null) {
                rawTextByPage.put(p, ext.getRawText());
                rawTextBuilder.append(ext.getRawText()).append("\n");
                totalRawChars += ext.getRawCharCount();

                PageContext context = new PageContext();
                context.setPageNumber(p);
                context.setRawText("");
                context.setRawCharCount(ext.getRawCharCount());
                context.setAllowEntityCreation(false);
                context.setUsageRule("Page metadata only. Entity content must come from a traceable region.");
                pageContexts.add(context);
            }
        }
        String basePageRangeText = rawTextBuilder.toString().trim();

        // 4. Build Region payloads, trigger crops, extract text
        List<RegionPayload> regionPayloads = new ArrayList<>();
        List<CropInfo> cropInfos = new ArrayList<>();
        long totalEstimatedBytes = 0;
        int totalSelectedChars = 0;

        for (PracticePdfRegionAnnotation ann : inRangeAnnos) {
            String regionId = "region-" + ann.getId();
            RegionPayload rPayload = new RegionPayload();
            rPayload.setRegionId(regionId);
            rPayload.setPageNumber(ann.getPageNumber());
            rPayload.setDisplayOrder(ann.getDisplayOrder());
            rPayload.setRegionType(ann.getRegionType());
            boolean regionTypeLocked = ann.getRegionType() != null && !"AUTO_DETECT".equalsIgnoreCase(ann.getRegionType());
            boolean sendText = ann.getIncludeTextInAi() != false;
            boolean sendImage = ann.getIncludeImageInAi() != false;
            rPayload.setClassificationSource(regionTypeLocked ? "LECTURER" : "AUTO");
            RegionLocks locks = new RegionLocks();
            locks.setRegionType(regionTypeLocked);
            locks.setSection(ann.getSectionTempId() != null && !ann.getSectionTempId().isBlank());
            locks.setGroup(ann.getGroupTempId() != null && !ann.getGroupTempId().isBlank());
            locks.setQuestionRange(ann.getExpectedQuestionFrom() != null || ann.getExpectedQuestionTo() != null);
            locks.setQuestionType(ann.getExpectedQuestionType() != null && !"AUTO_DETECT".equalsIgnoreCase(ann.getExpectedQuestionType()));
            locks.setPlacement(ann.getAssetPlacement() != null && !ann.getAssetPlacement().isBlank());
            rPayload.setLocks(locks);
            rPayload.setSendText(sendText);
            rPayload.setSendImage(sendImage);
            rPayload.setSendImageToAi(sendImage);
            rPayload.setKeepCropInSession(sendImage);
            rPayload.setSaveToLibrary(Boolean.TRUE.equals(ann.getSaveToAssetLibrary()));
            rPayload.setDisplayInExam("IMAGE_ASSET".equalsIgnoreCase(ann.getRegionType()));
            rPayload.setSectionTempId(ann.getSectionTempId());
            rPayload.setGroupTempId(ann.getGroupTempId());
            rPayload.setExpectedQuestionType(ann.getExpectedQuestionType() != null ? ann.getExpectedQuestionType() : "AUTO_DETECT");
            rPayload.setExpectedQuestionFrom(ann.getExpectedQuestionFrom());
            rPayload.setExpectedQuestionTo(ann.getExpectedQuestionTo());
            rPayload.setPlacement(ann.getAssetPlacement() != null ? ann.getAssetPlacement() : "UNASSIGNED");
            rPayload.setBbox(new NormalizedBoundingBox(ann.getxRatio(), ann.getyRatio(), ann.getWidthRatio(), ann.getHeightRatio()));
            rPayload.setLockedByLecturer(regionTypeLocked);

            String note = ann.getLecturerNote();
            rPayload.setLecturerNote(note != null ? note.trim() : "");
            rPayload.setSuggestedNote(getDefaultLecturerNote(ann.getRegionType()));

            // Extract region text
            if (Boolean.TRUE.equals(rPayload.getSendText()) && ("HYBRID".equalsIgnoreCase(strategy) || "REGION_ONLY".equalsIgnoreCase(strategy))) {
                try {
                    com.ksh.features.practice.manage.service.PracticePdfTextExtractionService areaTextExtractor =
                            new com.ksh.features.practice.manage.service.PracticePdfTextExtractionService();
                    String extractedText = areaTextExtractor.extractRegionText(pdfPath, ann.getPageNumber(),
                            ann.getxRatio(), ann.getyRatio(), ann.getWidthRatio(), ann.getHeightRatio());
                    rPayload.setOcrText(extractedText);
                    totalSelectedChars += extractedText.length();
                } catch (Exception e) {
                    log.error("[PayloadBuilder] Failed to extract text for region={}", regionId, e);
                }
            }

            // Extract image crop
            if (Boolean.TRUE.equals(rPayload.getSendImage()) && ("HYBRID".equalsIgnoreCase(strategy) || "REGION_ONLY".equalsIgnoreCase(strategy))) {
                try {
                    List<LecturerAsset> existingAssets = assetRepository.findBySourceImportSessionId(sessionId);
                    Optional<LecturerAsset> match = existingAssets.stream()
                            .filter(a -> ann.getId().equals(a.getSourceRegionId()))
                            .findFirst();

                    LecturerAsset asset;
                    if (match.isPresent()) {
                        asset = match.get();
                    } else {
                        // Perform crop and store
                        asset = cropService.cropRegion(pdfPath, ann.getPageNumber(),
                                ann.getxRatio(), ann.getyRatio(), ann.getWidthRatio(), ann.getHeightRatio(),
                                "WITH_PADDING", 16, session.getUploaderId(), sessionId, ann.getId());
                    }

                    // Promote temporary data key representation
                    String base64Data = "";
                    try (InputStream in = assetStorage.load(asset.getStorageKey()).getInputStream()) {
                        byte[] bytes = in.readAllBytes();
                        base64Data = "data:" + asset.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
                    }

                    String assetUrl = "/practice/materials/" + asset.getId() + "/content";
                    String assetRef = "asset-import-" + sessionId + "-" + regionId;
                    rPayload.setAssetRef(assetRef);

                    totalEstimatedBytes += asset.getFileSize();
                    cropInfos.add(new CropInfo(regionId, ann.getPageNumber(), ann.getRegionType(), rPayload.getAssetRef(), rPayload.getPlacement(), assetUrl, base64Data, asset.getFileSize()));
                } catch (Exception e) {
                    log.error("[PayloadBuilder] Failed to crop image for region={}", regionId, e);
                }
            }

            regionPayloads.add(rPayload);
        }

        // Guided mode still needs traceable source IDs. Represent each selected page as a
        // synthetic full-page region so the AI cannot create untraceable draft entities.
        if ("FULL_SELECTED_PAGES".equalsIgnoreCase(strategy)) {
            for (PageContext pageContext : pageContexts) {
                RegionPayload pageRegion = fullPageRegion(
                        pageContext, rawTextByPage.getOrDefault(pageContext.getPageNumber(), ""));
                regionPayloads.add(pageRegion);
                totalSelectedChars += pageContext.getRawCharCount() != null
                        ? pageContext.getRawCharCount()
                        : 0;
            }
        }

        // 5. Build section hints
        List<SectionHint> sectionHints = sectionDrafts.stream().map(sd -> {
            SectionHint sh = new SectionHint();
            sh.setSectionTempId(sd.getTempId());
            sh.setLabel(sd.getTitle() != null ? sd.getTitle() : sd.getSkill());
            sh.setSkill(sd.getSkill());
            sh.setTestNo(session.getTargetTestNo());
            sh.setLessonCode(session.getTargetLessonCode());
            sh.setDisplayOrder(sd.getDisplayOrder());
            sh.setDurationMinutes(null);
            
            // Map regions that fall into this section
            List<String> srcIds = inRangeAnnos.stream()
                    .filter(ann -> sd.getTempId().equals(ann.getSectionTempId()))
                    .map(ann -> "region-" + ann.getId())
                    .collect(Collectors.toList());
            sh.setSourceRegionIds(srcIds);
            return sh;
        }).collect(Collectors.toList());

        if (sectionHints.isEmpty() && session.getTargetSkill() != null
                && session.getTargetLessonCode() != null) {
            SectionHint target = new SectionHint();
            target.setSectionTempId("target-" + session.getTargetLessonCode().toLowerCase(Locale.ROOT));
            target.setLabel(skillLabel(session.getTargetSkill()));
            target.setSkill(session.getTargetSkill());
            target.setTestNo(session.getTargetTestNo());
            target.setLessonCode(session.getTargetLessonCode());
            target.setDisplayOrder(1);
            target.setDurationMinutes(null);
            target.setSourceRegionIds(regionPayloads.stream()
                    .map(RegionPayload::getRegionId)
                    .toList());
            sectionHints = new ArrayList<>(List.of(target));
        }

        // 6. Build group hints
        List<GroupHint> groupHints = groupDrafts.stream().map(gd -> {
            GroupHint gh = new GroupHint();
            gh.setGroupTempId(gd.getTempId());
            gh.setSectionTempId(gd.getSectionTempId());
            gh.setLabel(gd.getTitle() != null ? gd.getTitle() : "Group " + gd.getTempId());
            gh.setDisplayOrder(gd.getDisplayOrder());
            
            // Expected ranges
            gh.setExpectedQuestionFrom(gd.getExpectedFrom());
            gh.setExpectedQuestionTo(gd.getExpectedTo());
            
            // Find type based on region types within this group
            List<PracticePdfRegionAnnotation> groupAnnos = inRangeAnnos.stream()
                    .filter(ann -> gd.getTempId().equals(ann.getGroupTempId()))
                    .collect(Collectors.toList());
            
            String qType = "AUTO_DETECT";
            for (PracticePdfRegionAnnotation a : groupAnnos) {
                if (a.getExpectedQuestionType() != null && !"AUTO_DETECT".equals(a.getExpectedQuestionType())) {
                    qType = a.getExpectedQuestionType();
                    break;
                }
            }
            gh.setExpectedQuestionType(qType);

            List<String> srcIds = groupAnnos.stream()
                    .map(ann -> "region-" + ann.getId())
                    .collect(Collectors.toList());
            gh.setSourceRegionIds(srcIds);
            return gh;
        }).collect(Collectors.toList());

        // 7. Form final request DTO
        AiDocumentImportRequest request = new AiDocumentImportRequest();

        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setRequestId(UUID.randomUUID().toString());
        requestMeta.setPromptVersion("practice-import-v3");
        requestMeta.setSchemaVersion("2.0");
        requestMeta.setSessionRevision(session.getUpdatedAt() != null ? Math.toIntExact(Math.max(0, session.getUpdatedAt().hashCode())) : 0);
        requestMeta.setRegionRevision(inRangeAnnos.size());
        requestMeta.setCreatedAt(LocalDateTime.now().toString());
        request.setRequestMeta(requestMeta);
        
        DocumentMetadata meta = new DocumentMetadata();
        meta.setSessionId(sessionId);
        meta.setFilename(session.getOriginalFilename());
        meta.setExamCategory(session.getExamCategory() != null ? session.getExamCategory() : "TOPIK_II");
        meta.setAssessmentProgramCode(session.getAssessmentProgramCode());
        meta.setAssessmentProgramVersionId(session.getAssessmentProgramVersionId());
        meta.setExamTemplateCode(session.getExamTemplateCode());
        meta.setTargetTestNo(session.getTargetTestNo());
        meta.setTargetSkill(session.getTargetSkill());
        meta.setTargetLessonCode(session.getTargetLessonCode());
        meta.setPageFrom(startPage);
        meta.setPageTo(endPage);
        meta.setTotalExtractedCharacters(totalRawChars);
        request.setDocument(meta);
        request.setCategoryAssessment(detectCategoryAssessment(meta.getExamCategory(), session.getOriginalFilename(), basePageRangeText));
        request.setPageContexts(pageContexts);

        request.setSections(sectionHints);
        request.setGroups(groupHints);
        request.setRegions(regionPayloads);
        request.setConstraints(new ImportConstraints());

        // 8. Run Validator constraints
        List<ValidationError> validationErrors = payloadValidator.validate(request);

        // 9. DTO stats summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rawTextCharacters", totalRawChars);
        summary.put("selectedTextCharacters", totalSelectedChars);
        summary.put("finalSentTextCharacters", "FULL_SELECTED_PAGES".equalsIgnoreCase(strategy) ? totalRawChars : totalSelectedChars);
        summary.put("activeRegionsCount", regionPayloads.size());
        summary.put("ignoredRegionsCount", ignoredCount);
        summary.put("estimatedImageBytes", totalEstimatedBytes);
        summary.put("validationErrorsCount", validationErrors.size());
        summary.put("targetTestNo", session.getTargetTestNo());
        summary.put("targetSkill", session.getTargetSkill());
        summary.put("targetLessonCode", session.getTargetLessonCode());
        summary.put("examTemplateCode", session.getExamTemplateCode());

        return new PayloadInfo(request, "", cropInfos, summary, validationErrors);
    }

    private RegionPayload fullPageRegion(PageContext pageContext, String rawText) {
        RegionPayload payload = new RegionPayload();
        payload.setRegionId("page-" + pageContext.getPageNumber());
        payload.setPageNumber(pageContext.getPageNumber());
        payload.setDisplayOrder(pageContext.getPageNumber());
        payload.setRegionType("FULL_PAGE");
        payload.setClassificationSource("SYSTEM_GUIDED");
        payload.setSendText(true);
        payload.setSendImage(false);
        payload.setSendImageToAi(false);
        payload.setKeepCropInSession(false);
        payload.setSaveToLibrary(false);
        payload.setDisplayInExam(false);
        payload.setExpectedQuestionType("AUTO_DETECT");
        payload.setPlacement("UNASSIGNED");
        payload.setBbox(new NormalizedBoundingBox(0.0, 0.0, 1.0, 1.0));
        payload.setLockedByLecturer(false);
        payload.setOcrText(rawText);
        payload.setLecturerNote("Tự nhận diện cấu trúc trong toàn bộ trang đã chọn.");
        payload.setSuggestedNote("Tách nội dung theo thứ tự hiển thị và giữ sourceRegionId của trang.");

        RegionLocks locks = new RegionLocks();
        locks.setRegionType(false);
        locks.setSection(false);
        locks.setGroup(false);
        locks.setQuestionRange(false);
        locks.setQuestionType(false);
        locks.setPlacement(false);
        payload.setLocks(locks);
        return payload;
    }

    private String getDefaultLecturerNote(String regionType) {
        if (regionType == null) return "Xác định vai trò dựa duy nhất trên nội dung vùng.";
        return switch (regionType.toUpperCase()) {
            case "TRANSCRIPT" -> "Đây là lời thoại dùng chung. Chỉ sử dụng cho nhóm câu liên quan trong vùng đã chọn.";
            case "PASSAGE" -> "Đây là bài đọc dùng chung. Không chuyển các câu trong bài đọc thành options.";
            case "INSTRUCTION" -> "Đây là hướng dẫn làm bài. Không tạo thành câu hỏi độc lập.";
            case "QUESTION_BLOCK" -> "Vùng chứa câu hỏi. Tách câu và lựa chọn theo đúng thứ tự hiển thị.";
            case "OPTIONS" -> "Đây là các phương án trả lời. Không chép lại các lựa chọn vào prompt.";
            case "IMAGE_ASSET" -> "Đây là hình minh họa. Tham chiếu bằng assetRef, không diễn giải thành nội dung giả.";
            case "AUTO_DETECT" -> "Xác định vai trò dựa duy nhất trên nội dung vùng. Không suy diễn ngoài crop.";
            default -> "Xác định vai trò dựa duy nhất trên nội dung vùng.";
        };
    }

    private static String skillLabel(String skill) {
        return switch (skill == null ? "" : skill.toUpperCase(Locale.ROOT)) {
            case "LISTENING" -> "Phần Nghe";
            case "WRITING" -> "Phần Viết";
            case "SPEAKING" -> "Phần Nói";
            default -> "Phần Đọc";
        };
    }

    private CategoryAssessment detectCategoryAssessment(String declaredCategory, String filename, String text) {
        String haystack = ((filename != null ? filename : "") + "\n" + (text != null ? text : "")).toUpperCase(Locale.ROOT);
        CategoryAssessment assessment = new CategoryAssessment();
        assessment.setDeclaredCategory(declaredCategory);
        assessment.setDetectedCategory(declaredCategory);
        assessment.setConfidence(0.25);
        assessment.setConflict(false);
        assessment.setLecturerConfirmedConflict(false);
        assessment.setEvidence("");

        if (haystack.contains("TOPIK I") && !haystack.contains("TOPIK II")) {
            assessment.setDetectedCategory("TOPIK_I");
            assessment.setConfidence(0.85);
            assessment.setEvidence("Found TOPIK I marker in filename or extracted text.");
        } else if (haystack.contains("TOPIK II")) {
            assessment.setDetectedCategory("TOPIK_II");
            assessment.setConfidence(0.85);
            assessment.setEvidence("Found TOPIK II marker in filename or extracted text.");
        }

        if (declaredCategory != null && assessment.getDetectedCategory() != null
                && assessment.getConfidence() != null && assessment.getConfidence() >= 0.8
                && !declaredCategory.equalsIgnoreCase(assessment.getDetectedCategory())) {
            assessment.setConflict(true);
        }
        return assessment;
    }

    public record CropInfo(String regionId, int pageNumber, String regionType, String assetRef, String placement, String url, String base64DataUrl, long byteSize) {}

    public record PayloadInfo(
            AiDocumentImportRequest requestDto,
            String basePageRangeText,
            List<CropInfo> crops,
            Map<String, Object> statsSummary,
            List<ValidationError> validationErrors
    ) {}
}
