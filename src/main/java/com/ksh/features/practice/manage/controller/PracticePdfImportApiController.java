package com.ksh.features.practice.manage.controller;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateException;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PracticeImportTargetService;
import com.ksh.features.practice.manage.service.PracticePdfAiOrchestrator;
import com.ksh.features.practice.manage.service.PracticePdfAiPayloadBuilder;
import com.ksh.features.practice.manage.service.PracticePdfAuthoringCandidateAssembler;
import com.ksh.features.practice.manage.service.PracticePdfAuthoringRequest;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Request-local Text/PDF authoring plus the shared canonical asset API. */
@RestController
@RequestMapping("/practice/manage")
@PreAuthorize(Roles.PREAUTH_LECTURER)
public class PracticePdfImportApiController {

    private static final Logger log = LoggerFactory.getLogger(
            PracticePdfImportApiController.class);

    private final LecturerAssetService assetService;
    private final PracticeImportTargetService targetService;
    private final PracticePdfAiPayloadBuilder payloadBuilder;
    private final PracticePdfAiOrchestrator aiOrchestrator;
    private final PracticePdfAuthoringCandidateAssembler candidateAssembler;

    public PracticePdfImportApiController(
            LecturerAssetService assetService,
            PracticeImportTargetService targetService,
            PracticePdfAiPayloadBuilder payloadBuilder,
            PracticePdfAiOrchestrator aiOrchestrator,
            PracticePdfAuthoringCandidateAssembler candidateAssembler) {
        this.assetService = assetService;
        this.targetService = targetService;
        this.payloadBuilder = payloadBuilder;
        this.aiOrchestrator = aiOrchestrator;
        this.candidateAssembler = candidateAssembler;
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
            TargetRoute target = targetService.requireExactTarget(
                    draftId, testNo, skill, lessonCode, user.getId());
            SourceOperation operation = operation(rawOperation);
            String normalizedSourceType = sourceType == null ? ""
                    : sourceType.trim().toUpperCase(Locale.ROOT);
            PracticePdfAuthoringRequest authoring = switch (normalizedSourceType) {
                case "TEXT" -> payloadBuilder.buildBasicText(
                        sourceText, operation, lecturerRequest, target);
                case "PDF" -> payloadBuilder.buildBasicPdf(
                        file, startPage, endPage,
                        operation, lecturerRequest, target);
                default -> throw new IllegalArgumentException(
                        "sourceType chỉ nhận TEXT hoặc PDF.");
            };
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
                    "code", "PRACTICE_PDF_AUTHORING_UNAVAILABLE",
                    "causeCode", exception.category(),
                    "error", authoringFailureMessage(exception.category())));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "PDF_AUTHORING_REQUEST_INVALID",
                    "error", exception.getMessage()));
        } catch (Exception exception) {
            log.error("[ImportApiController] Request-local PDF authoring failed code=PDF_AUTHORING_FAILED");
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", "PDF_AUTHORING_FAILED",
                    "error", "Không thể tạo authoring candidate lúc này."));
        }
    }

    private static String authoringFailureMessage(String causeCode) {
        return switch (causeCode == null ? "" : causeCode) {
            case "PROVIDER_HTTP_REQUEST_REJECTED" ->
                    "PDF không bị chặn. Nhà cung cấp AI từ chối định dạng yêu cầu; "
                            + "hệ thống sẽ cần kiểm tra schema hoặc khả năng model đã gán.";
            case "PROVIDER_HTTP_AUTHENTICATION_FAILED" ->
                    "PDF không bị chặn. Nhà cung cấp AI từ chối xác thực; "
                            + "quản trị viên cần kiểm tra API key của profile đã gán.";
            case "PROVIDER_HTTP_ENDPOINT_OR_MODEL_NOT_FOUND" ->
                    "PDF không bị chặn. Endpoint hoặc model của nhà cung cấp AI không tồn tại; "
                            + "quản trị viên cần kiểm tra Base URL và model đã gán.";
            case "PROVIDER_HTTP_RATE_LIMITED" ->
                    "PDF không bị chặn. Nhà cung cấp AI đang giới hạn yêu cầu hoặc hết hạn mức; "
                            + "hãy thử lại sau hoặc kiểm tra quota.";
            case "PROVIDER_HTTP_TIMEOUT", "PROVIDER_TRANSPORT_ERROR" ->
                    "PDF không bị chặn. Không kết nối được tới nhà cung cấp AI trong thời gian cho phép; "
                            + "hãy thử lại sau.";
            case "PROVIDER_HTTP_REQUEST_TOO_LARGE", "PROVIDER_REQUEST_TOO_LARGE" ->
                    "PDF không bị chặn. Nội dung gửi tới AI vượt giới hạn của nhà cung cấp; "
                            + "hãy rút ngắn nguồn hoặc phạm vi trang.";
            default -> "PDF không bị chặn. AI cho Biên soạn từ PDF không thể xử lý yêu cầu lúc này; "
                    + "hãy kiểm tra profile, model hoặc thử lại sau.";
        };
    }

    @GetMapping("/assets")
    public ResponseEntity<List<AssetView>> getAssetsList(
            @AuthenticationPrincipal KshUserDetails user) {
        return ResponseEntity.ok(assetService.getLibraryAssets(user.getId()).stream()
                .map(AssetView::from)
                .toList());
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<Void> deleteAsset(
            @PathVariable Long assetId,
            @AuthenticationPrincipal KshUserDetails user) {
        assetService.deleteAsset(assetId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/drafts/{draftId}/assets")
    public ResponseEntity<PracticeMaterialReference> linkAsset(
            @PathVariable Long draftId,
            @RequestBody LinkAssetRequest request,
            @AuthenticationPrincipal KshUserDetails user) {
        PracticeMaterialReference reference = assetService.linkAssetToDraft(
                draftId, request.assetId(), user.getId(), request.sectionTempId(),
                request.groupTempId(), request.questionTempId(),
                request.placement(), request.altText());
        return ResponseEntity.ok(reference);
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

    public record AssetView(
            Long id,
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
                    asset.getId(), asset.getOriginalFilename(), asset.getMimeType(),
                    asset.isContentVerified(), asset.getWidth(), asset.getHeight(),
                    asset.getFileSize(), asset.getAssetType(), asset.getTitle(),
                    asset.getAltText(), asset.getSourceType(), asset.getLecturerNote(),
                    asset.getTagsJson(), asset.getStatus(), asset.getVisibility(),
                    asset.getRetentionUntil(), asset.getCreatedAt(), asset.getUpdatedAt(),
                    "/practice/materials/" + asset.getId() + "/content");
        }
    }

    public record LinkAssetRequest(
            Long assetId,
            String sectionTempId,
            String groupTempId,
            String questionTempId,
            String placement,
            String altText) {
    }
}
