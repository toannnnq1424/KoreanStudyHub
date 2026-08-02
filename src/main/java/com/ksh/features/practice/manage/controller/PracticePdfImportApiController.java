package com.ksh.features.practice.manage.controller;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.entities.PracticePdfPageExtraction;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateException;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.service.*;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator.ValidationError;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/practice/manage")
@PreAuthorize(Roles.PREAUTH_LECTURER)
public class PracticePdfImportApiController {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfImportApiController.class);

    private final PracticePdfImportSessionService sessionService;
    private final PracticePdfRegionService regionService;
    private final PracticePdfPageExtractionService pageExtractionService;
    private final LecturerAssetService assetService;
    private final PracticePdfPayloadPreviewService payloadPreviewService;
    private final PracticePdfAiPayloadBuilder payloadBuilder;
    private final PracticePdfAiOrchestrator aiOrchestrator;
    private final PracticePdfAuthoringCandidateAssembler candidateAssembler;
    private final PracticePdfAiGenerationService generationService;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeImportSnapshotService snapshotService;
    private final PracticePdfPreviewService previewService;

    public PracticePdfImportApiController(PracticePdfImportSessionService sessionService,
                                          PracticePdfRegionService regionService,
                                          PracticePdfPageExtractionService pageExtractionService,
                                          LecturerAssetService assetService,
                                          PracticePdfPayloadPreviewService payloadPreviewService,
                                          PracticePdfAiPayloadBuilder payloadBuilder,
                                          PracticePdfAiOrchestrator aiOrchestrator,
                                          PracticePdfAuthoringCandidateAssembler candidateAssembler,
                                          PracticePdfAiGenerationService generationService,
                                          PracticeAuthorizationService authorizationService,
                                          PracticeImportSnapshotService snapshotService,
                                          PracticePdfPreviewService previewService) {
        this.sessionService = sessionService;
        this.regionService = regionService;
        this.pageExtractionService = pageExtractionService;
        this.assetService = assetService;
        this.payloadPreviewService = payloadPreviewService;
        this.payloadBuilder = payloadBuilder;
        this.aiOrchestrator = aiOrchestrator;
        this.candidateAssembler = candidateAssembler;
        this.generationService = generationService;
        this.authorizationService = authorizationService;
        this.snapshotService = snapshotService;
        this.previewService = previewService;
    }

    @PostMapping("/import-sessions")
    public ResponseEntity<PracticePdfImportSession> uploadPdf(@RequestParam("file") MultipartFile file,
                                                              @RequestParam(value = "title", required = false) String title,
                                                              @RequestParam(value = "linkedDraftId", required = false) Long linkedDraftId,
                                                              @RequestParam(value = "targetTestNo", required = false) Integer targetTestNo,
                                                              @RequestParam(value = "targetSkill", required = false) String targetSkill,
                                                              @RequestParam(value = "targetLessonCode", required = false) String targetLessonCode,
                                                              @AuthenticationPrincipal KshUserDetails user) throws Exception {
        PracticePdfImportSession session = sessionService.createSession(
                user.getId(), file, title, linkedDraftId,
                targetTestNo, targetSkill, targetLessonCode);
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
        return ResponseEntity.ok(dto.redacted());
    }

    @PostMapping("/import-sessions/{sessionId}/generate")
    public ResponseEntity<?> generateCandidate(
            @PathVariable Long sessionId,
            @RequestBody(required = false) AdvancedGenerateRequest request,
            @AuthenticationPrincipal KshUserDetails user) {
        // The short claim transaction commits before any crop/provider work. A
        // duplicate request therefore cannot start a second provider call.
        PracticePdfAiGenerationService.ClaimResult claim =
                generationService.claim(sessionId, user.getId());
        if (claim.outcome() == PracticePdfAiGenerationService.Outcome.COMPLETED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "LEGACY_PDF_GENERATION_ALREADY_COMPLETED",
                    "error", "Phiên PDF cũ đã tạo draft trực tiếp. Hãy mở lại PDF để tạo candidate mới."));
        }
        if (claim.outcome() == PracticePdfAiGenerationService.Outcome.IN_PROGRESS) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "PROCESSING",
                    "message", "Yêu cầu tạo bản nháp đang được xử lý.",
                    "leaseExpiresAt", claim.leaseExpiresAt()
            ));
        }

        String claimToken = claim.claimToken();
        PracticePdfImportSession session = claim.claimedSession();
        if (session == null) {
            throw new IllegalStateException(
                    "PDF AI generation claim did not provide a fenced session.");
        }
        try {
            authorizeGenerationTarget(session, user.getId());
            PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo = payloadBuilder.buildPayload(session);
            List<ValidationError> blockingErrors = payloadInfo.validationErrors().stream()
                    .filter(err -> "ERROR".equalsIgnoreCase(err.severity()))
                    .toList();
            if (!blockingErrors.isEmpty()) {
                generationService.release(
                        sessionId, user.getId(), claimToken, "READY_FOR_AI");
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "FAILED_RETRYABLE",
                        "message", "Dữ liệu khoanh vùng chưa đủ an toàn để gửi AI.",
                        "errors", blockingErrors,
                        "warnings", payloadInfo.validationErrors()
                ));
            }
            SourceOperation operation = operation(
                    request == null ? null : request.operation());
            PracticePdfAuthoringRequest authoring =
                    payloadBuilder.buildAdvancedAuthoringRequest(
                            session, payloadInfo, operation,
                            request == null ? "" : request.lecturerRequest());
            PracticePdfAiOrchestrator.GenerationResult generation =
                    aiOrchestrator.generate(authoring);
            CandidateView candidate = candidateAssembler.assemble(
                    authoring, generation, user.getId());
            generationService.release(
                    sessionId, user.getId(), claimToken, "REVIEWING");
            return ResponseEntity.ok(candidateResponse(candidate));
        } catch (AccessDeniedException e) {
            releaseClaimIfOwned(
                    sessionId, user.getId(), claimToken, "READY_FOR_AI");
            throw e;
        } catch (PracticeAuthoringCandidateException e) {
            releaseClaimIfOwned(
                    sessionId, user.getId(), claimToken, "AI_FAILED_RETRYABLE");
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", e.code(), "error", e.getMessage()));
        } catch (PracticeAiContractException e) {
            releaseClaimIfOwned(
                    sessionId, user.getId(), claimToken, "AI_FAILED_RETRYABLE");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", e.category(),
                    "error", "Purpose PRACTICE_PDF_AUTHORING hiện chưa sẵn sàng."));
        } catch (IllegalArgumentException e) {
            releaseClaimIfOwned(
                    sessionId, user.getId(), claimToken, "READY_FOR_AI");
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "PDF_AUTHORING_REQUEST_INVALID",
                    "error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ImportApiController] PDF authoring failed sessionId={} code=PDF_AUTHORING_FAILED",
                    sessionId);
            releaseClaimIfOwned(
                    sessionId,
                    user.getId(),
                    claimToken,
                    "AI_FAILED_RETRYABLE");
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED_RETRYABLE",
                    "message", "Phân tích AI thất bại. Vùng crop và candidate hiện tại vẫn được giữ nguyên.",
                    "error", "AI_PROCESSING_FAILED"
            ));
        }
    }

    @PostMapping(value = "/pdf-authoring/candidates",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createBasicCandidate(
            @RequestParam("sourceType") String sourceType,
            @RequestParam("operation") String rawOperation,
            @RequestParam(value = "sourceText", required = false) String sourceText,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "lecturerRequest", required = false) String lecturerRequest,
            @RequestParam("draftId") Long draftId,
            @RequestParam("testNo") Integer testNo,
            @RequestParam("skill") String skill,
            @RequestParam("lessonCode") String lessonCode,
            @RequestParam(value = "startPage", required = false) Integer startPage,
            @RequestParam(value = "endPage", required = false) Integer endPage,
            @AuthenticationPrincipal KshUserDetails user) {
        try {
            TargetRoute target = authorizedTarget(
                    draftId, testNo, skill, lessonCode, user.getId());
            SourceOperation operation = operation(rawOperation);
            PracticePdfAuthoringRequest authoring;
            String normalizedSourceType = sourceType == null ? ""
                    : sourceType.trim().toUpperCase(Locale.ROOT);
            if ("TEXT".equals(normalizedSourceType)) {
                authoring = payloadBuilder.buildBasicText(
                        sourceText, operation, lecturerRequest, target);
            } else if ("PDF".equals(normalizedSourceType)) {
                if (file == null || file.isEmpty()) {
                    throw new IllegalArgumentException("Vui lòng chọn file PDF.");
                }
                PracticePdfImportSession session = sessionService.createSession(
                        user.getId(), file, null, draftId,
                        testNo, target.skill(), target.lessonCode());
                int first = startPage == null ? 1 : startPage;
                int last = endPage == null ? session.getTotalPages() : endPage;
                session = sessionService.updatePageRange(
                        session.getId(), first, last, user.getId());
                authoring = payloadBuilder.buildBasicPdf(
                        session, operation, lecturerRequest);
            } else {
                throw new IllegalArgumentException("sourceType chỉ nhận TEXT hoặc PDF.");
            }
            PracticePdfAiOrchestrator.GenerationResult generation =
                    aiOrchestrator.generate(authoring);
            CandidateView candidate = candidateAssembler.assemble(
                    authoring, generation, user.getId());
            return ResponseEntity.ok(candidateResponse(candidate));
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (PracticeAuthoringCandidateException exception) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", exception.code(), "error", exception.getMessage()));
        } catch (PracticeAiContractException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", exception.category(),
                    "error", "Purpose PRACTICE_PDF_AUTHORING hiện chưa sẵn sàng."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "PDF_AUTHORING_REQUEST_INVALID",
                    "error", exception.getMessage()));
        } catch (Exception exception) {
            log.error("[ImportApiController] Basic PDF authoring failed code=PDF_AUTHORING_FAILED");
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", "PDF_AUTHORING_FAILED",
                    "error", "Không thể tạo authoring candidate lúc này."));
        }
    }

    private void authorizeGenerationTarget(
            PracticePdfImportSession session,
            Long userId) {
        if (session.getLinkedDraftId() == null) {
            throw new IllegalArgumentException(
                    "Advanced PDF cần liên kết một bản nháp trước khi tạo candidate.");
        }
        authorizationService.requireDraft(
                session.getLinkedDraftId(), userId, PracticeAction.EDIT);
    }

    private TargetRoute authorizedTarget(
            Long draftId,
            Integer testNo,
            String skill,
            String lessonCode,
            Long actorId) {
        if (draftId == null || testNo == null || testNo < 1
                || skill == null || lessonCode == null) {
            throw new IllegalArgumentException("Target draft/section là bắt buộc.");
        }
        PracticePdfImportSessionService.PdfImportStartContext context =
                sessionService.resolveStartContext(
                        draftId, testNo, lessonCode, actorId);
        if (context == null
                || context.selected().testNo() != testNo
                || !context.selected().skill().equalsIgnoreCase(skill)
                || !context.selected().lessonCode().equalsIgnoreCase(lessonCode)) {
            throw new IllegalArgumentException(
                    "Target Test/skill/lesson không khớp bản nháp được cấp quyền.");
        }
        return new TargetRoute(draftId, testNo,
                context.selected().skill(), context.selected().lessonCode());
    }

    private static SourceOperation operation(String value) {
        try {
            SourceOperation operation = SourceOperation.valueOf(
                    value == null || value.isBlank()
                            ? "EXTRACT" : value.trim().toUpperCase(Locale.ROOT));
            if (operation == SourceOperation.NONE) throw new IllegalArgumentException();
            return operation;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "operation chỉ nhận EXTRACT hoặc GENERATE.");
        }
    }

    private static Map<String, Object> candidateResponse(CandidateView candidate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("candidateId", candidate.candidateId());
        response.put("state", candidate.state().name());
        response.put("candidateVersion", candidate.version());
        response.put("candidateDigest", candidate.contentDigest());
        response.put("reviewUrl", "/practice/manage/authoring-candidates/"
                + candidate.candidateId());
        return response;
    }

    private void releaseClaimIfOwned(
            Long sessionId,
            Long userId,
            String claimToken,
            String nextStatus) {
        try {
            generationService.release(
                    sessionId, userId, claimToken, nextStatus);
        } catch (IllegalStateException lostClaim) {
            log.warn(
                    "[ImportApiController] generation claim no longer owned for sessionId={}",
                    sessionId);
        }
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

    @PatchMapping("/assets/{assetId}")
    public ResponseEntity<AssetView> updateAsset(@PathVariable Long assetId,
                                                 @RequestBody UpdateAssetRequest req,
                                                 @AuthenticationPrincipal KshUserDetails user) {
        LecturerAsset asset = assetService.updateAssetMetadata(
                assetId,
                user.getId(),
                req.title(),
                req.tagsJson(),
                req.assetType(),
                req.lecturerNote(),
                req.status());
        return ResponseEntity.ok(AssetView.from(asset));
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
    public ResponseEntity<PracticeMaterialReference> linkAsset(@PathVariable Long draftId,
                                                             @RequestBody LinkAssetRequest req,
                                                             @AuthenticationPrincipal KshUserDetails user) {
        PracticeMaterialReference reference = assetService.linkAssetToDraft(
                draftId, req.assetId(), user.getId(), req.sectionTempId(), req.groupTempId(),
                req.questionTempId(), req.placement(), req.altText()
        );
        return ResponseEntity.ok(reference);
    }

    @DeleteMapping("/drafts/{draftId}/assets/{referenceId}")
    public ResponseEntity<Void> unlinkAsset(@PathVariable Long draftId,
                                            @PathVariable Long referenceId,
                                            @AuthenticationPrincipal KshUserDetails user) {
        assetService.unlinkAssetFromDraft(draftId, referenceId, user.getId());
        return ResponseEntity.ok().build();
    }

    public record SaveStateRequest(Integer currentPage, Integer startPage, Integer endPage, String extractionStrategy) {}
    public record PageRangeRequest(Integer startPage, Integer endPage, String extractionMode) {}
    public record AdvancedGenerateRequest(String operation, String lecturerRequest) {}
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
                            Double cropX,
                            Double cropY,
                            Double cropWidth,
                            Double cropHeight,
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
                    asset.getCropX(),
                    asset.getCropY(),
                    asset.getCropWidth(),
                    asset.getCropHeight(),
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
