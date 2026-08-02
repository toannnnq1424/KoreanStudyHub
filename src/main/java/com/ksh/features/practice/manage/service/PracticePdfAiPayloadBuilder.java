package com.ksh.features.practice.manage.service;

import com.ksh.entities.*;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest.*;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator.ValidationError;
import com.ksh.features.practice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
    private final PracticePdfAiLimits limits;

    @Autowired
    public PracticePdfAiPayloadBuilder(PracticePdfRegionAnnotationRepository annotationRepository,
                                       PracticePdfImportSectionDraftRepository sectionDraftRepository,
                                       PracticePdfImportGroupDraftRepository groupDraftRepository,
                                       PracticePdfPageExtractionService pageExtractionService,
                                       PracticePdfCropService cropService,
                                       LecturerAssetRepository assetRepository,
                                       AssetStorageService assetStorage,
                                       ImportAiPayloadValidator payloadValidator,
                                       PracticePdfAiLimits limits) {
        this.annotationRepository = annotationRepository;
        this.sectionDraftRepository = sectionDraftRepository;
        this.groupDraftRepository = groupDraftRepository;
        this.pageExtractionService = pageExtractionService;
        this.cropService = cropService;
        this.assetRepository = assetRepository;
        this.assetStorage = assetStorage;
        this.payloadValidator = payloadValidator;
        this.limits = limits;
    }

    PracticePdfAiPayloadBuilder(
            PracticePdfRegionAnnotationRepository annotationRepository,
            PracticePdfImportSectionDraftRepository sectionDraftRepository,
            PracticePdfImportGroupDraftRepository groupDraftRepository,
            PracticePdfPageExtractionService pageExtractionService,
            PracticePdfCropService cropService,
            LecturerAssetRepository assetRepository,
            AssetStorageService assetStorage,
            ImportAiPayloadValidator payloadValidator) {
        this(
                annotationRepository,
                sectionDraftRepository,
                groupDraftRepository,
                pageExtractionService,
                cropService,
                assetRepository,
                assetStorage,
                payloadValidator,
                new PracticePdfAiLimits(
                        50, 100, 1_000_000, 5_242_880L, 20_971_520L,
                        40_000_000L, Duration.ofMinutes(2)));
    }

    public PracticePdfAuthoringRequest buildBasicText(
            String sourceText,
            SourceOperation operation,
            String lecturerRequest,
            TargetRoute target) {
        String normalized = PracticePdfAuthoringRequest.normalize(sourceText);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Vui lòng dán nội dung cần biên soạn.");
        }
        if (normalized.length() > limits.maxTextCharacters()) {
            throw new IllegalArgumentException(
                    "Nội dung Text vượt ngân sách ký tự an toàn.");
        }
        PracticePdfAuthoringRequest.SourceEvidence evidence =
                new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "text-1", null,
                        normalized.length(), normalized);
        List<PracticePdfAuthoringRequest.SourceEvidence> evidenceList =
                List.of(evidence);
        return new PracticePdfAuthoringRequest(
                PracticePdfAuthoringRequest.SourceType.TEXT,
                operation,
                "Pasted text",
                digestEvidence(evidenceList, List.of()),
                target,
                lecturerRequest,
                evidenceList,
                sourceContext("BASIC_TEXT", target, evidenceList, Map.of(), null),
                List.of(),
                null);
    }

    public PracticePdfAuthoringRequest buildBasicPdf(
            PracticePdfImportSession session,
            SourceOperation operation,
            String lecturerRequest) {
        requireCandidateTarget(session);
        int start = session.getSelectedStartPage();
        int end = session.getSelectedEndPage();
        if (start < 1 || end < start || end - start + 1 > limits.maxSelectedPages()) {
            throw new IllegalArgumentException(
                    "Phạm vi trang PDF vượt ngân sách xử lý an toàn.");
        }
        List<PracticePdfAuthoringRequest.SourceEvidence> evidence = new ArrayList<>();
        int totalCharacters = 0;
        for (int page = start; page <= end; page++) {
            PracticePdfPageExtraction extraction =
                    pageExtractionService.extractOrGetPageText(session, page);
            if (!"COMPLETED".equals(extraction.getExtractionStatus())) {
                throw new IllegalArgumentException(
                        "Không thể trích xuất text từ trang PDF đã chọn.");
            }
            String text = PracticePdfAuthoringRequest.normalize(
                    extraction.getNormalizedText() == null
                            ? extraction.getRawText()
                            : extraction.getNormalizedText());
            if (text.length() > limits.maxTextCharacters() - totalCharacters) {
                throw new IllegalArgumentException(
                        "Nội dung PDF vượt ngân sách ký tự an toàn.");
            }
            totalCharacters += text.length();
            evidence.add(new PracticePdfAuthoringRequest.SourceEvidence(
                    "PAGE", "page-" + page, page, text.length(), text));
            if (!text.isEmpty()) {
                evidence.add(new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "page-" + page + "-text", page,
                        text.length(), text));
            }
        }
        if (totalCharacters == 0) {
            throw new IllegalArgumentException(
                    "Các trang đã chọn không có text để biên soạn Basic. Hãy dùng Advanced.");
        }
        TargetRoute target = target(session);
        return new PracticePdfAuthoringRequest(
                PracticePdfAuthoringRequest.SourceType.PDF,
                operation,
                safeSourceName(session.getOriginalFilename()),
                digestEvidence(evidence, List.of()),
                target,
                lecturerRequest,
                evidence,
                sourceContext("BASIC_PDF", target, evidence, Map.of(), session.getId()),
                List.of(),
                session.getId());
    }

    public PracticePdfAuthoringRequest buildAdvancedAuthoringRequest(
            PracticePdfImportSession session,
            PayloadInfo payload,
            SourceOperation operation,
            String lecturerRequest) {
        requireCandidateTarget(session);
        List<PracticePdfAuthoringRequest.SourceEvidence> evidence = new ArrayList<>();
        if (payload.requestDto() != null && payload.requestDto().getRegions() != null) {
            for (RegionPayload region : payload.requestDto().getRegions()) {
                String text = PracticePdfAuthoringRequest.normalize(region.getOcrText());
                String kind = "FULL_PAGE".equals(region.getRegionType())
                        ? "PAGE" : "REGION";
                evidence.add(new PracticePdfAuthoringRequest.SourceEvidence(
                        kind,
                        region.getRegionId(),
                        region.getPageNumber(),
                        text.length(),
                        text));
            }
        }
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "Advanced workspace chưa có source evidence hợp lệ.");
        }
        Map<String, String> assetReferences = new LinkedHashMap<>();
        List<com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest.ImageEvidence>
                images = new ArrayList<>();
        for (CropInfo crop : payload.crops()) {
            assetReferences.put(crop.assetRef(), crop.url());
            images.add(new com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest.ImageEvidence(
                    "PDF_REGION_" + crop.regionId(),
                    sha256(crop.base64DataUrl()),
                    crop.base64DataUrl(),
                    "high"));
        }
        TargetRoute target = target(session);
        Map<String, Object> context = sourceContext(
                "ADVANCED_PDF", target, evidence, assetReferences, session.getId());
        context = new LinkedHashMap<>(context);
        context.put("workspaceHints", payload.requestDto());
        return new PracticePdfAuthoringRequest(
                PracticePdfAuthoringRequest.SourceType.ADVANCED_PDF,
                operation,
                safeSourceName(session.getOriginalFilename()),
                digestEvidence(evidence, images),
                target,
                lecturerRequest,
                evidence,
                context,
                images,
                session.getId());
    }

    public PayloadInfo buildPayload(PracticePdfImportSession session) {
        Long sessionId = session.getId();
        String pdfPath = session.getStoredPdfPath();
        int startPage = session.getSelectedStartPage();
        int endPage = session.getSelectedEndPage();
        String strategy = session.getExtractionStrategy() != null ? session.getExtractionStrategy() : "HYBRID";
        int selectedPages = endPage - startPage + 1;
        if (startPage < 1 || endPage < startPage
                || selectedPages > limits.maxSelectedPages()) {
            throw new IllegalArgumentException(
                    "Phạm vi trang PDF vượt ngân sách xử lý an toàn.");
        }

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
        if (inRangeAnnos.size() > limits.maxRegions()) {
            throw new IllegalArgumentException(
                    "Số vùng PDF vượt ngân sách xử lý an toàn.");
        }

        // 3. Extract raw text from page range for total character stats
        StringBuilder rawTextBuilder = new StringBuilder();
        List<PageContext> pageContexts = new ArrayList<>();
        Map<Integer, String> rawTextByPage = new LinkedHashMap<>();
        int totalRawChars = 0;
        for (int p = startPage; p <= endPage; p++) {
            PracticePdfPageExtraction ext = pageExtractionService.extractOrGetPageText(session, p);
            if (ext.getRawText() != null) {
                String rawText = ext.getRawText();
                int rawCharacters = rawText.length();
                if (rawCharacters
                        > limits.maxTextCharacters() - totalRawChars) {
                    throw new IllegalArgumentException(
                            "Nội dung PDF vượt ngân sách ký tự an toàn.");
                }
                rawTextByPage.put(p, rawText);
                rawTextBuilder.append(rawText).append("\n");
                totalRawChars += rawCharacters;

                PageContext context = new PageContext();
                context.setPageNumber(p);
                context.setRawText("");
                context.setRawCharCount(rawCharacters);
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
        List<LecturerAsset> existingAssets =
                assetRepository.findBySourceImportSessionId(sessionId);
        if (existingAssets == null) {
            existingAssets = List.of();
        }

        for (PracticePdfRegionAnnotation ann : inRangeAnnos) {
            String regionId = "region-" + ann.getId();
            RegionPayload rPayload = new RegionPayload();
            rPayload.setRegionId(regionId);
            rPayload.setPageNumber(ann.getPageNumber());
            rPayload.setDisplayOrder(ann.getDisplayOrder());
            rPayload.setRegionType(ann.getRegionType());
            boolean regionTypeLocked = ann.getRegionType() != null && !"AUTO_DETECT".equalsIgnoreCase(ann.getRegionType());
            boolean sendText = !Boolean.FALSE.equals(ann.getIncludeTextInAi());
            boolean sendImage = !Boolean.FALSE.equals(ann.getIncludeImageInAi());
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
                    String extractedText = pageExtractionService.extractRegionText(
                            session, ann.getPageNumber(), ann.getxRatio(), ann.getyRatio(),
                            ann.getWidthRatio(), ann.getHeightRatio());
                    if (extractedText == null) extractedText = "";
                    rPayload.setOcrText(extractedText);
                    if (extractedText.length()
                            > limits.maxTextCharacters() - totalSelectedChars) {
                        throw new IllegalArgumentException(
                                "Nội dung vùng PDF vượt ngân sách ký tự an toàn.");
                    }
                    totalSelectedChars += extractedText.length();
                } catch (Exception e) {
                    if (e instanceof IllegalArgumentException unsafeRequest) {
                        throw unsafeRequest;
                    }
                    log.error("[PayloadBuilder] Failed to extract text for region={}", regionId, e);
                }
            }

            // Extract image crop
            if (Boolean.TRUE.equals(rPayload.getSendImage()) && ("HYBRID".equalsIgnoreCase(strategy) || "REGION_ONLY".equalsIgnoreCase(strategy))) {
                try {
                    Optional<LecturerAsset> match = PracticePdfRegionAssetSelector
                            .findCurrent(existingAssets, ann);

                    LecturerAsset asset;
                    if (match.isPresent()) {
                        asset = match.get();
                    } else {
                        // Perform crop and store
                        asset = cropService.cropRegion(
                                session, ann.getPageNumber(), ann.getxRatio(), ann.getyRatio(),
                                ann.getWidthRatio(), ann.getHeightRatio(), "WITH_PADDING", 16,
                                session.getUploaderId(), sessionId, ann.getId());
                    }

                    // Promote temporary data key representation
                    String base64Data = "";
                    if (asset.getFileSize() == null
                            || asset.getFileSize() < 0L
                            || asset.getFileSize() > limits.maxImageBytes()) {
                        throw new IllegalArgumentException(
                                "Ảnh crop vượt ngân sách kích thước an toàn.");
                    }
                    try (InputStream in = (asset.getStorageProfileCode() == null
                            ? assetStorage.load(asset.getStorageKey())
                            : assetStorage.load(asset.getStorageProfileCode(), asset.getStorageKey()))
                            .getInputStream()) {
                        byte[] bytes = in.readNBytes(
                                Math.toIntExact(limits.maxImageBytes() + 1L));
                        if (bytes.length > limits.maxImageBytes()) {
                            throw new IllegalArgumentException(
                                    "Ảnh crop vượt ngân sách kích thước an toàn.");
                        }
                        base64Data = "data:" + asset.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);

                        String assetUrl = "/practice/materials/" + asset.getId() + "/content";
                        String assetRef = "asset-import-" + sessionId + "-" + regionId;
                        rPayload.setAssetRef(assetRef);

                        long actualImageBytes = bytes.length;
                        if (actualImageBytes
                                > limits.maxTotalImageBytes() - totalEstimatedBytes) {
                            throw new IllegalArgumentException(
                                    "Tổng ảnh crop vượt ngân sách request an toàn.");
                        }
                        totalEstimatedBytes += actualImageBytes;
                        cropInfos.add(new CropInfo(
                                regionId,
                                ann.getPageNumber(),
                                ann.getRegionType(),
                                rPayload.getAssetRef(),
                                rPayload.getPlacement(),
                                assetUrl,
                                base64Data,
                                actualImageBytes));
                    }
                } catch (Exception e) {
                    if (e instanceof IllegalArgumentException unsafeRequest) {
                        throw unsafeRequest;
                    }
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
                int pageCharacters = pageContext.getRawCharCount() != null
                        ? pageContext.getRawCharCount()
                        : 0;
                if (pageCharacters
                        > limits.maxTextCharacters() - totalSelectedChars) {
                    throw new IllegalArgumentException(
                            "Nội dung vùng PDF vượt ngân sách ký tự an toàn.");
                }
                totalSelectedChars += pageCharacters;
            }
        }
        if (regionPayloads.size() > limits.maxRegions()) {
            throw new IllegalArgumentException(
                    "Số vùng PDF vượt ngân sách xử lý an toàn.");
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
        meta.setTargetTestNo(session.getTargetTestNo());
        meta.setTargetSkill(session.getTargetSkill());
        meta.setTargetLessonCode(session.getTargetLessonCode());
        meta.setPageFrom(startPage);
        meta.setPageTo(endPage);
        meta.setTotalExtractedCharacters(totalRawChars);
        request.setDocument(meta);
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

    public record CropInfo(String regionId, int pageNumber, String regionType, String assetRef, String placement, String url, String base64DataUrl, long byteSize) {}

    public record PayloadInfo(
            AiDocumentImportRequest requestDto,
            String basePageRangeText,
            List<CropInfo> crops,
            Map<String, Object> statsSummary,
            List<ValidationError> validationErrors
    ) {}

    private static TargetRoute target(PracticePdfImportSession session) {
        return new TargetRoute(
                session.getLinkedDraftId(),
                session.getTargetTestNo(),
                session.getTargetSkill(),
                session.getTargetLessonCode());
    }

    private static void requireCandidateTarget(PracticePdfImportSession session) {
        if (session == null || session.getLinkedDraftId() == null
                || session.getTargetTestNo() == null
                || session.getTargetSkill() == null
                || session.getTargetLessonCode() == null) {
            throw new IllegalArgumentException(
                    "PDF authoring candidate cần một phần đích trong bản nháp hiện có.");
        }
    }

    private static Map<String, Object> sourceContext(
            String mode,
            TargetRoute target,
            List<PracticePdfAuthoringRequest.SourceEvidence> evidence,
            Map<String, String> assetReferences,
            Long sessionId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("trust", "UNTRUSTED_SOURCE_CONTENT");
        context.put("mode", mode);
        context.put("targetSkill", target.skill());
        context.put("targetTestNo", target.testNo());
        context.put("targetLessonCode", target.lessonCode());
        if (sessionId != null) context.put("sessionId", sessionId);
        context.put("evidence", evidence.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("kind", item.kind());
            value.put("sourceId", item.sourceId());
            if (item.pageNumber() != null) value.put("pageNumber", item.pageNumber());
            value.put("textLength", item.textLength());
            value.put("untrustedText", item.untrustedText());
            return value;
        }).toList());
        context.put("assetReferences", Map.copyOf(assetReferences));
        return Map.copyOf(context);
    }

    private static String digestEvidence(
            List<PracticePdfAuthoringRequest.SourceEvidence> evidence,
            List<com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest.ImageEvidence>
                    images) {
        MessageDigest digest = sha256Digest();
        for (PracticePdfAuthoringRequest.SourceEvidence item : evidence) {
            update(digest, item.kind());
            update(digest, item.sourceId());
            update(digest, item.pageNumber() == null ? "" : item.pageNumber().toString());
            update(digest, item.untrustedText());
        }
        for (com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest.ImageEvidence
                image : images) {
            update(digest, image.role());
            update(digest, image.sha256());
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String safeSourceName(String value) {
        String name = PracticePdfAuthoringRequest.normalize(value);
        return name.isBlank() ? "Practice PDF" : name.substring(0, Math.min(255, name.length()));
    }

    private static String sha256(String value) {
        MessageDigest digest = sha256Digest();
        digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
