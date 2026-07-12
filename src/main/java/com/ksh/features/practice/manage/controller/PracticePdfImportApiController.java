package com.ksh.features.practice.manage.controller;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeDraftAssetUsage;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.entities.PracticePdfPageExtraction;
import com.ksh.features.practice.manage.service.*;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator.ValidationError;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import com.ksh.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/practice/manage")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticePdfImportApiController {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfImportApiController.class);

    private final PracticePdfImportSessionService sessionService;
    private final PracticePdfRegionService regionService;
    private final PracticePdfPageExtractionService pageExtractionService;
    private final LecturerAssetService assetService;
    private final LecturerAssetRepository assetRepository;
    private final PracticePdfPayloadPreviewService payloadPreviewService;
    private final PracticePdfAiPayloadBuilder payloadBuilder;
    private final PracticePdfAiOrchestrator aiOrchestrator;
    private final PracticePdfDraftAssembler draftAssembler;
    private final PracticeImportDraftService importDraftService;
    private final PracticeImportSnapshotService snapshotService;
    private final PracticePdfPreviewService previewService;
    private final PracticeOverrideContextService overrideContextService;

    public PracticePdfImportApiController(PracticePdfImportSessionService sessionService,
                                          PracticePdfRegionService regionService,
                                          PracticePdfPageExtractionService pageExtractionService,
                                          LecturerAssetService assetService,
                                          LecturerAssetRepository assetRepository,
                                          PracticePdfPayloadPreviewService payloadPreviewService,
                                          PracticePdfAiPayloadBuilder payloadBuilder,
                                          PracticePdfAiOrchestrator aiOrchestrator,
                                          PracticePdfDraftAssembler draftAssembler,
                                          PracticeImportDraftService importDraftService,
                                          PracticeImportSnapshotService snapshotService,
                                          PracticePdfPreviewService previewService,
                                          PracticeOverrideContextService overrideContextService) {
        this.sessionService = sessionService;
        this.regionService = regionService;
        this.pageExtractionService = pageExtractionService;
        this.assetService = assetService;
        this.assetRepository = assetRepository;
        this.payloadPreviewService = payloadPreviewService;
        this.payloadBuilder = payloadBuilder;
        this.aiOrchestrator = aiOrchestrator;
        this.draftAssembler = draftAssembler;
        this.importDraftService = importDraftService;
        this.snapshotService = snapshotService;
        this.previewService = previewService;
        this.overrideContextService = overrideContextService;
    }

    @PostMapping("/import-sessions")
    public ResponseEntity<PracticePdfImportSession> uploadPdf(@RequestParam("file") MultipartFile file,
                                                              @RequestParam(value = "examTemplateCode", required = false) String examTemplateCode,
                                                              @RequestParam(value = "examCategory", required = false) String legacyExamCategory,
                                                              @RequestParam(value = "title", required = false) String title,
                                                              @RequestParam(value = "linkedDraftId", required = false) Long linkedDraftId,
                                                              @RequestParam(value = "targetTestNo", required = false) Integer targetTestNo,
                                                              @RequestParam(value = "targetSkill", required = false) String targetSkill,
                                                              @RequestParam(value = "targetLessonCode", required = false) String targetLessonCode,
                                                              @RequestParam(value = "overrideReason", required = false) String explicitOverrideReason,
                                                              @AuthenticationPrincipal KshUserDetails user,
                                                              jakarta.servlet.http.HttpSession httpSession) throws Exception {
        String requestedTemplate = examTemplateCode == null || examTemplateCode.isBlank()
                ? legacyExamCategory
                : examTemplateCode;
        PracticePdfImportSession session = sessionService.createSession(
                user.getId(), file, requestedTemplate, title, linkedDraftId,
                targetTestNo, targetSkill, targetLessonCode,
                overrideReason(httpSession, linkedDraftId, explicitOverrideReason));
        // Save initial snapshot
        snapshotService.saveSnapshot(session.getId(), user.getId());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/import-sessions/{sessionId}")
    public ResponseEntity<PracticePdfImportSession> getSession(@PathVariable Long sessionId,
                                                               @AuthenticationPrincipal KshUserDetails user) {
        PracticePdfImportSession session = sessionService.getSession(sessionId, user.getId());
        return ResponseEntity.ok(session);
    }

    @PutMapping("/import-sessions/{sessionId}/page-range")
    public ResponseEntity<PracticePdfImportSession> updatePageRange(@PathVariable Long sessionId,
                                                                    @RequestBody PageRangeRequest req,
                                                                    @AuthenticationPrincipal KshUserDetails user) {
        PracticePdfImportSession session = sessionService.updatePageRange(sessionId, req.startPage(), req.endPage(), user.getId());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/import-sessions/{sessionId}/file")
    public ResponseEntity<InputStreamResource> getPdfFile(@PathVariable Long sessionId,
                                                          @AuthenticationPrincipal KshUserDetails user) throws Exception {
        InputStream stream = previewService.getPdfStream(sessionId, user.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(stream));
    }

    @PostMapping("/import-sessions/{sessionId}/save")
    public ResponseEntity<PracticePdfImportSession> saveState(@PathVariable Long sessionId,
                                                              @RequestBody SaveStateRequest req,
                                                              @AuthenticationPrincipal KshUserDetails user) {
        PracticePdfImportSession session = sessionService.saveState(
                sessionId, req.currentPage(), req.startPage(), req.endPage(), req.extractionStrategy(), user.getId()
        );
        // Take an updated snapshot on manual save/autosave triggers
        snapshotService.saveSnapshot(sessionId, user.getId());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/import-sessions/{sessionId}/cancel-changes")
    public ResponseEntity<Void> cancelChanges(@PathVariable Long sessionId,
                                              @AuthenticationPrincipal KshUserDetails user) {
        snapshotService.restoreSnapshot(sessionId, user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/import-sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId,
                                              @AuthenticationPrincipal KshUserDetails user) {
        sessionService.deleteSession(sessionId, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/import-sessions/{sessionId}/extracted-text")
    public ResponseEntity<Map<String, Object>> getExtractedText(@PathVariable Long sessionId,
                                                                @RequestParam(value = "page", required = false) Integer page,
                                                                @AuthenticationPrincipal KshUserDetails user) {
        PracticePdfImportSession session = sessionService.getSession(sessionId, user.getId());
        if (page != null) {
            PracticePdfPageExtraction ext = pageExtractionService.extractOrGetPageText(session, page);
            return ResponseEntity.ok(Map.of(
                    "pageNumber", page,
                    "rawText", ext.getRawText() != null ? ext.getRawText() : "",
                    "rawCharCount", ext.getRawCharCount()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "sessionId", sessionId,
                    "totalPages", session.getTotalPages()
            ));
        }
    }

    @GetMapping("/import-sessions/{sessionId}/annotations")
    public ResponseEntity<List<PracticePdfRegionAnnotation>> getAnnotations(@PathVariable Long sessionId,
                                                                            @AuthenticationPrincipal KshUserDetails user) {
        List<PracticePdfRegionAnnotation> list = regionService.getAnnotations(sessionId, user.getId());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/import-sessions/{sessionId}/annotations")
    public ResponseEntity<PracticePdfRegionAnnotation> addAnnotation(@PathVariable Long sessionId,
                                                                     @RequestBody PracticePdfRegionAnnotation annotation,
                                                                     @AuthenticationPrincipal KshUserDetails user) {
        PracticePdfRegionAnnotation created = regionService.createAnnotation(sessionId, annotation, user.getId());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/import-sessions/{sessionId}/annotations/{annotationId}")
    public ResponseEntity<PracticePdfRegionAnnotation> updateAnnotation(@PathVariable Long sessionId,
                                                                        @PathVariable Long annotationId,
                                                                        @RequestBody PracticePdfRegionAnnotation annotation,
                                                                        @AuthenticationPrincipal KshUserDetails user) {
        PracticePdfRegionAnnotation updated = regionService.updateAnnotation(sessionId, annotationId, annotation, user.getId());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/import-sessions/{sessionId}/annotations/{annotationId}")
    public ResponseEntity<Void> deleteAnnotation(@PathVariable Long sessionId,
                                                 @PathVariable Long annotationId,
                                                 @AuthenticationPrincipal KshUserDetails user) {
        regionService.deleteAnnotation(sessionId, annotationId, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/import-sessions/{sessionId}/payload-preview")
    public ResponseEntity<PracticePdfPayloadPreviewService.PayloadPreviewDto> getPayloadPreview(@PathVariable Long sessionId,
                                                                                                @AuthenticationPrincipal KshUserDetails user) {
        PracticePdfImportSession session = sessionService.getSession(sessionId, user.getId());
        PracticePdfPayloadPreviewService.PayloadPreviewDto dto = payloadPreviewService.getPreview(session);
        boolean privileged = user.getRole() == Role.HEAD || user.getRole() == Role.ADMIN;
        return ResponseEntity.ok(privileged ? dto : dto.redacted());
    }

    @PostMapping("/import-sessions/{sessionId}/generate")
    public ResponseEntity<?> generateDraft(@PathVariable Long sessionId,
                                           @RequestParam(value = "overrideReason", required = false) String explicitOverrideReason,
                                           @AuthenticationPrincipal KshUserDetails user,
                                           jakarta.servlet.http.HttpSession httpSession) {
        // AI job is run in a separate transaction B. Failure doesn't rollback crops or session annotations
        PracticePdfImportSession session = sessionService.getSession(sessionId, user.getId());
        sessionService.updateStatus(sessionId, "PROCESSING");
        try {
            PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo = payloadBuilder.buildPayload(session);
            List<ValidationError> blockingErrors = payloadInfo.validationErrors().stream()
                    .filter(err -> "ERROR".equalsIgnoreCase(err.severity()))
                    .toList();
            if (!blockingErrors.isEmpty()) {
                sessionService.updateStatus(sessionId, "READY_FOR_AI");
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "FAILED_RETRYABLE",
                        "message", "Dữ liệu khoanh vùng chưa đủ an toàn để gửi AI.",
                        "errors", blockingErrors,
                        "warnings", payloadInfo.validationErrors()
                ));
            }
            String rawAiJson = aiOrchestrator.callAi(payloadInfo, sessionId, session.getExtractionStrategy());
            PracticeDraft draft = draftAssembler.assembleAndSaveDraft(
                    session, rawAiJson, user.getId(), overrideReason(
                            httpSession, session.getLinkedDraftId(), explicitOverrideReason));
            sessionService.updateStatus(sessionId, "AI_COMPLETED");
            return ResponseEntity.ok(draft);
        } catch (Exception e) {
            log.error("[ImportApiController] AI Job analysis failed for sessionId={}", sessionId, e);
            sessionService.updateStatus(sessionId, "AI_FAILED_RETRYABLE");
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED_RETRYABLE",
                    "message", "Phân tích AI thất bại. Vùng crop và bản nháp hiện tại vẫn được giữ nguyên.",
                    "error", "AI_PROCESSING_FAILED"
            ));
        }
    }

    @PostMapping("/import-sessions/{sessionId}/create-manual-draft")
    public ResponseEntity<PracticeDraft> createManualDraft(@PathVariable Long sessionId,
                                                           @RequestParam(value = "overrideReason", required = false) String explicitOverrideReason,
                                                           @AuthenticationPrincipal KshUserDetails user,
                                                           jakarta.servlet.http.HttpSession httpSession) {
        PracticePdfImportSession session = sessionService.getSession(sessionId, user.getId());
        PracticeDraft draft = importDraftService.createManualDraftFromSession(
                sessionId, user.getId(), overrideReason(
                        httpSession, session.getLinkedDraftId(), explicitOverrideReason));
        return ResponseEntity.ok(draft);
    }

    @PostMapping("/import-sessions/{sessionId}/attach-to-draft")
    public ResponseEntity<PracticeDraft> attachToDraft(@PathVariable Long sessionId,
                                                       @RequestParam("targetDraftId") Long targetDraftId,
                                                       @RequestParam(value = "overrideReason", required = false) String explicitOverrideReason,
                                                       @AuthenticationPrincipal KshUserDetails user,
                                                       jakarta.servlet.http.HttpSession httpSession) {
        PracticeDraft draft = importDraftService.attachToExistingDraft(
                sessionId, targetDraftId, user.getId(), overrideReason(
                        httpSession, targetDraftId, explicitOverrideReason));
        return ResponseEntity.ok(draft);
    }

    private String overrideReason(jakarta.servlet.http.HttpSession session,
                                  Long draftId, String explicitReason) {
        if (draftId == null) return null;
        if (explicitReason != null && !explicitReason.isBlank()) {
            overrideContextService.establishForDraft(session, draftId, explicitReason);
        }
        return overrideContextService.reasonForDraft(session, draftId, explicitReason);
    }

    // Lecturer asset library endpoints

    @GetMapping("/assets")
    public ResponseEntity<List<AssetView>> getAssetsList(
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @AuthenticationPrincipal KshUserDetails user) {
        List<LecturerAsset> assets;
        if (sessionId != null) {
            sessionService.getSession(sessionId, user.getId());
            assets = assetService.getSessionAssets(sessionId, user.getId());
        } else {
            assets = assetService.getLibraryAssets(user.getId());
        }
        return ResponseEntity.ok(assets.stream().map(AssetView::from).toList());
    }

    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<Resource> getAssetContent(@PathVariable Long assetId,
                                                    @AuthenticationPrincipal KshUserDetails user) throws Exception {
        Resource fileResource = assetService.loadAssetResource(assetId, user.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(fileResource);
    }

    @PatchMapping("/assets/{assetId}")
    public ResponseEntity<AssetView> updateAsset(@PathVariable Long assetId,
                                                 @RequestBody UpdateAssetRequest req,
                                                 @AuthenticationPrincipal KshUserDetails user) {
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!asset.getOwnerLecturerId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không sở hữu asset này.");
        }
        if (req.title() != null) asset.setTitle(req.title());
        if (req.tagsJson() != null) asset.setTagsJson(req.tagsJson());
        if (req.assetType() != null) asset.setAssetType(req.assetType());
        if (req.lecturerNote() != null) asset.setLecturerNote(req.lecturerNote());
        if (req.status() != null) {
            if (!"TEMPORARY".equalsIgnoreCase(req.status())) {
                throw new IllegalArgumentException(
                        "Chỉ endpoint promote-asset được phép chuyển asset sang ACTIVE.");
            }
            asset.setStatus("TEMPORARY");
        }
        asset.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(AssetView.from(assetRepository.save(asset)));
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long assetId,
                                            @AuthenticationPrincipal KshUserDetails user) {
        assetService.deleteAsset(assetId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/import-sessions/{sessionId}/regions/{regionId}/promote-asset")
    public ResponseEntity<AssetView> promoteAsset(@PathVariable Long sessionId,
                                                  @PathVariable Long regionId,
                                                  @RequestParam("assetId") Long assetId,
                                                  @AuthenticationPrincipal KshUserDetails user) {
        regionService.getAnnotation(sessionId, regionId, user.getId());
        LecturerAsset asset = assetService.promoteSessionRegionAsset(
                sessionId, regionId, assetId, user.getId());
        return ResponseEntity.ok(AssetView.from(asset));
    }

    @PostMapping("/drafts/{draftId}/assets")
    public ResponseEntity<PracticeDraftAssetUsage> linkAsset(@PathVariable Long draftId,
                                                             @RequestBody LinkAssetRequest req,
                                                             @AuthenticationPrincipal KshUserDetails user) {
        PracticeDraftAssetUsage usage = assetService.linkAssetToDraft(
                draftId, req.assetId(), user.getId(), req.sectionTempId(), req.groupTempId(),
                req.questionTempId(), req.placement(), req.altText()
        );
        return ResponseEntity.ok(usage);
    }

    @DeleteMapping("/drafts/{draftId}/assets/{usageId}")
    public ResponseEntity<Void> unlinkAsset(@PathVariable Long draftId,
                                            @PathVariable Long usageId,
                                            @AuthenticationPrincipal KshUserDetails user) {
        assetService.unlinkAssetFromDraft(draftId, usageId, user.getId());
        return ResponseEntity.ok().build();
    }

    public record SaveStateRequest(Integer currentPage, Integer startPage, Integer endPage, String extractionStrategy) {}
    public record PageRangeRequest(Integer startPage, Integer endPage, String extractionMode) {}
    public record UpdateAssetRequest(String title, String tagsJson, String assetType, String lecturerNote, String status) {}

    public record AssetView(Long id,
                            Long sourceImportSessionId,
                            Long sourceRegionId,
                            String originalFilename,
                            String mimeType,
                            boolean contentVerified,
                            Integer width,
                            Integer height,
                            Long fileSize,
                            String assetType,
                            String title,
                            String altText,
                            String sourceType,
                            Integer sourcePageNumber,
                            String lecturerNote,
                            String tagsJson,
                            String status,
                            String visibility,
                            LocalDateTime retentionUntil,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt,
                            String contentUrl) {
        static AssetView from(LecturerAsset asset) {
            return new AssetView(
                    asset.getId(),
                    asset.getSourceImportSessionId(),
                    asset.getSourceRegionId(),
                    asset.getOriginalFilename(),
                    asset.getMimeType(),
                    asset.isContentVerified(),
                    asset.getWidth(),
                    asset.getHeight(),
                    asset.getFileSize(),
                    asset.getAssetType(),
                    asset.getTitle(),
                    asset.getAltText(),
                    asset.getSourceType(),
                    asset.getSourcePageNumber(),
                    asset.getLecturerNote(),
                    asset.getTagsJson(),
                    asset.getStatus(),
                    asset.getVisibility(),
                    asset.getRetentionUntil(),
                    asset.getCreatedAt(),
                    asset.getUpdatedAt(),
                    "/practice/materials/" + asset.getId() + "/content");
        }
    }
    public record LinkAssetRequest(Long assetId, String sectionTempId, String groupTempId, String questionTempId, String placement, String altText) {}
}
