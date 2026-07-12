package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.service.PracticeDraftService;
import com.ksh.features.practice.manage.service.PracticeDraftPreviewService;
import com.ksh.features.practice.manage.service.PracticePublisherService;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PublishedPracticeGraphMutationBlockedException;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/practice/manage")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeDraftController {

    private static final Logger log = LoggerFactory.getLogger(PracticeDraftController.class);
    private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            ".mp3", ".wav", ".m4a", ".ogg", ".webm");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp");

    private final PracticeDraftService draftService;
    private final PracticePublisherService publisherService;
    private final PracticeDraftValidator draftValidator;
    private final AssessmentAuthoringCatalogService authoringCatalogService;
    private final PracticeDraftPreviewService draftPreviewService;
    private final LecturerAssetService assetService;

    @org.springframework.beans.factory.annotation.Autowired
    public PracticeDraftController(PracticeDraftService draftService,
                                   PracticePublisherService publisherService,
                                   PracticeDraftValidator draftValidator,
                                   AssessmentAuthoringCatalogService authoringCatalogService,
                                   PracticeDraftPreviewService draftPreviewService,
                                   LecturerAssetService assetService) {
        this.draftService = draftService;
        this.publisherService = publisherService;
        this.draftValidator = draftValidator;
        this.authoringCatalogService = authoringCatalogService;
        this.draftPreviewService = draftPreviewService;
        this.assetService = assetService;
    }

    public PracticeDraftController(PracticeDraftService draftService,
                                   PracticePublisherService publisherService,
                                   PracticeDraftValidator draftValidator) {
        this(draftService, publisherService, draftValidator, null, null, null);
    }

    public PracticeDraftController(PracticeDraftService draftService,
                                   PracticePublisherService publisherService,
                                   PracticeDraftValidator draftValidator,
                                   AssessmentAuthoringCatalogService authoringCatalogService) {
        this(draftService, publisherService, draftValidator, authoringCatalogService, null, null);
    }

    public PracticeDraftController(PracticeDraftService draftService,
                                   PracticePublisherService publisherService,
                                   PracticeDraftValidator draftValidator,
                                   AssessmentAuthoringCatalogService authoringCatalogService,
                                   PracticeDraftPreviewService draftPreviewService) {
        this(draftService, publisherService, draftValidator, authoringCatalogService,
                draftPreviewService, null);
    }

    @GetMapping("/create")
    public String createEmptyDraft(@AuthenticationPrincipal KshUserDetails user) {
        PracticeDraft draft = draftService.getOrCreateEmptyDraft(user.getId());
        return "redirect:/practice/manage/drafts/" + draft.getId();
    }

    @GetMapping("/drafts/{draftId}/exit")
    public String exitDraft(@PathVariable("draftId") Long draftId,
                            @AuthenticationPrincipal KshUserDetails user) {
        try {
            PracticeDraft draft = draftService.getDraft(draftId, user.getId());
            String json = draft.getDraftJson();
            boolean isEmpty = true;
            if (json != null && !json.isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                    if (root.has("sections") && root.get("sections").isArray() && root.get("sections").size() > 0) {
                        isEmpty = false;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            if (isEmpty) {
                draftService.deleteDraft(draftId, user.getId());
                log.info("[DraftController] Deleted empty draft id={} on exit", draftId);
            }
        } catch (Exception e) {
            log.error("[DraftController] Exit draft cleanup failed for draftId={}", draftId, e);
        }
        return "redirect:/practice/manage";
    }

    @GetMapping("/drafts/{draftId}")
    public String editDraft(@PathVariable("draftId") Long draftId,
                            @AuthenticationPrincipal KshUserDetails user,
                            Model model) {
        PracticeDraft draft = draftService.getDraft(draftId, user.getId());
        model.addAttribute("draft", draft);
        model.addAttribute("draftJson", draft.getDraftJson());
        if (authoringCatalogService != null) {
            model.addAttribute("authoringCatalog", authoringCatalogService.catalog());
        }
        return "practice/manage/editor";
    }

    @GetMapping("/authoring-catalog")
    @ResponseBody
    public ResponseEntity<?> authoringCatalog() {
        if (authoringCatalogService == null) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Authoring catalog unavailable."));
        }
        return ResponseEntity.ok(authoringCatalogService.catalog());
    }

    @PostMapping("/drafts/{draftId}/preview")
    @ResponseBody
    public ResponseEntity<?> previewDraft(@PathVariable("draftId") Long draftId,
                                          @RequestBody Map<String, Object> payload,
                                          @AuthenticationPrincipal KshUserDetails user) {
        draftService.getDraft(draftId, user.getId());
        if (draftPreviewService == null) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Draft preview unavailable."));
        }
        Object rawDraftJson = payload.get("draftJson");
        if (!(rawDraftJson instanceof String draftJson) || draftJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thiếu dữ liệu bản nháp."));
        }
        try {
            return ResponseEntity.ok(draftPreviewService.preview(draftJson));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    // REST API endpoint for autosave
    @PostMapping("/drafts/{draftId}/autosave")
    @ResponseBody
    public ResponseEntity<?> autosave(@PathVariable("draftId") Long draftId,
                                      @RequestBody Map<String, Object> payload,
                                      @AuthenticationPrincipal KshUserDetails user) {
        try {
            String draftJson = (String) payload.get("draftJson");
            String title = (String) payload.get("title");
            String description = (String) payload.get("description");
            
            Integer clientVersion = null;
            if (payload.containsKey("version") && payload.get("version") != null) {
                try {
                    clientVersion = Integer.parseInt(payload.get("version").toString());
                } catch (NumberFormatException ignored) {}
            }

            PracticeDraft saved = draftService.saveDraftState(draftId, user.getId(), draftJson, title, description, clientVersion);
            
            // Run validation on the fly to return to UI
            PracticeDraftValidator.ValidationResult valRes = draftValidator.validate(saved.getDraftJson());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "version", saved.getVersion(),
                    "validation", valRes
            ));
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("[DraftAutosave] Optimistic lock conflict on draftId={}: {}", draftId, e.getMessage());
            return ResponseEntity.status(409).body(Map.of(
                    "status", "conflict",
                    "error", "Xung đột ghi đè đồng thời! Một giảng viên khác đã chỉnh sửa bản nháp này. Vui lòng tải lại trang để cập nhật nội dung mới nhất."
            ));
        } catch (org.springframework.security.access.AccessDeniedException
                 | jakarta.persistence.EntityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[DraftAutosave] Failed to autosave draftId={}", draftId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Không thể lưu bản nháp với dữ liệu đã gửi."));
        }
    }

    @PostMapping("/drafts/{draftId}/publish")
    public String publishDraft(@PathVariable("draftId") Long draftId,
                               @AuthenticationPrincipal KshUserDetails user,
                               RedirectAttributes redirectAttributes) {
        try {
            Long setId = publisherService.publish(draftId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Xuất bản bộ luyện tập thành công!");
            return "redirect:/practice/sets/" + setId;
        } catch (PublishedPracticeGraphMutationBlockedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/practice/manage/drafts/" + draftId;
        } catch (Exception e) {
            log.error("[DraftPublish] Publish failed draftId={}", draftId, e);
            redirectAttributes.addFlashAttribute("error", "Không thể xuất bản bộ luyện tập.");
            return "redirect:/practice/manage/drafts/" + draftId;
        }
    }

    @PostMapping("/drafts/{draftId}/delete")
    public String deleteDraft(@PathVariable("draftId") Long draftId,
                              @AuthenticationPrincipal KshUserDetails user,
                              RedirectAttributes redirectAttributes) {
        try {
            draftService.deleteDraft(draftId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Đã xoá bản nháp thành công.");
            return "redirect:/practice/manage";
        } catch (Exception e) {
            log.error("[DraftDelete] Delete failed draftId={}", draftId, e);
            redirectAttributes.addFlashAttribute("error", "Không thể xoá bản nháp.");
            return "redirect:/practice/manage";
        }
    }

    @PostMapping("/drafts/{draftId}/upload-audio")
    @ResponseBody
    public ResponseEntity<?> uploadAudio(@PathVariable("draftId") Long draftId,
                                         @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                         @AuthenticationPrincipal KshUserDetails user) {
        try {
            validateFilename(file, AUDIO_EXTENSIONS, "âm thanh");
            if (assetService == null) throw new IllegalStateException("Asset service unavailable.");
            com.ksh.entities.LecturerAsset asset = assetService.createDraftUploadAsset(
                    draftId, user.getId(), file, "AUDIO", MAX_AUDIO_BYTES);
            return ResponseEntity.ok(Map.of(
                    "assetId", asset.getId(),
                    "url", "/practice/materials/" + asset.getId() + "/content",
                    "filename", asset.getOriginalFilename()));
        } catch (org.springframework.security.access.AccessDeniedException
                 | jakarta.persistence.EntityNotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[AudioUpload] Failed for draftId={}", draftId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Không thể lưu tệp âm thanh."));
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/drafts/{draftId}/upload-image")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> uploadImage(@PathVariable("draftId") Long draftId,
                                                                 @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                                 @AuthenticationPrincipal KshUserDetails user) {
        try {
            validateFilename(file, IMAGE_EXTENSIONS, "ảnh");
            if (assetService == null) throw new IllegalStateException("Asset service unavailable.");
            com.ksh.entities.LecturerAsset asset = assetService.createDraftUploadAsset(
                    draftId, user.getId(), file, "IMAGE", MAX_IMAGE_BYTES);
            return org.springframework.http.ResponseEntity.ok(Map.of(
                    "assetId", asset.getId(),
                    "url", "/practice/materials/" + asset.getId() + "/content",
                    "filename", asset.getOriginalFilename()));
        } catch (org.springframework.security.access.AccessDeniedException
                 | jakarta.persistence.EntityNotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[ImageUpload] Failed for draftId={}", draftId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Không thể lưu tệp ảnh."));
        }
    }

    private static void validateFilename(org.springframework.web.multipart.MultipartFile file,
                                         Set<String> allowedExtensions,
                                         String fileLabel) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Tên tệp " + fileLabel + " không hợp lệ.");
        }
        int extensionIndex = originalFilename.lastIndexOf('.');
        String extension = extensionIndex < 0
                ? ""
                : originalFilename.substring(extensionIndex).toLowerCase(java.util.Locale.ROOT);
        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException("Định dạng tệp " + fileLabel + " không được hỗ trợ.");
        }
    }
}
