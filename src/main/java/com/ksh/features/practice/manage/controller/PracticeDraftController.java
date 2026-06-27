package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.service.PracticeDraftService;
import com.ksh.features.practice.manage.service.PracticePublisherService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
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

@Controller
@RequestMapping("/practice/manage")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeDraftController {

    private static final Logger log = LoggerFactory.getLogger(PracticeDraftController.class);

    @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}")
    private String rawUploadDir;

    private final PracticeDraftService draftService;
    private final PracticePublisherService publisherService;
    private final PracticeDraftValidator draftValidator;

    public PracticeDraftController(PracticeDraftService draftService,
                                   PracticePublisherService publisherService,
                                   PracticeDraftValidator draftValidator) {
        this.draftService = draftService;
        this.publisherService = publisherService;
        this.draftValidator = draftValidator;
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
        return "practice/manage/editor";
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
            PracticeDraftValidator.ValidationResult valRes = draftValidator.validate(draftJson);

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
        } catch (Exception e) {
            log.error("[DraftAutosave] Failed to autosave draftId={}", draftId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
        } catch (Exception e) {
            log.error("[DraftPublish] Publish failed draftId={}", draftId, e);
            redirectAttributes.addFlashAttribute("error", "Lỗi xuất bản: " + e.getMessage());
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
            redirectAttributes.addFlashAttribute("error", "Lỗi xoá bản nháp: " + e.getMessage());
            return "redirect:/practice/manage";
        }
    }

    @PostMapping("/drafts/{draftId}/upload-audio")
    @ResponseBody
    public ResponseEntity<?> uploadAudio(@PathVariable("draftId") Long draftId,
                                         @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tệp âm thanh rỗng."));
            }
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = java.util.UUID.randomUUID() + ext;
            java.nio.file.Path targetDir = java.nio.file.Paths.get(rawUploadDir, "practice-audio").toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(targetDir);
            java.nio.file.Path dest = targetDir.resolve(filename);
            file.transferTo(dest.toFile());

            String relativeUrl = "/uploads/practice-audio/" + filename;
            return ResponseEntity.ok(Map.of("url", relativeUrl, "filename", originalFilename));
        } catch (Exception e) {
            log.error("[AudioUpload] Failed for draftId={}", draftId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/drafts/{draftId}/upload-image")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> uploadImage(@PathVariable("draftId") Long draftId,
                                                                 @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", "Tệp ảnh rỗng."));
            }
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = java.util.UUID.randomUUID() + ext;
            java.nio.file.Path targetDir = java.nio.file.Paths.get(rawUploadDir, "practice-images").toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(targetDir);
            java.nio.file.Path dest = targetDir.resolve(filename);
            file.transferTo(dest.toFile());

            String relativeUrl = "/uploads/practice-images/" + filename;
            return org.springframework.http.ResponseEntity.ok(Map.of("url", relativeUrl, "filename", originalFilename));
        } catch (Exception e) {
            log.error("[ImageUpload] Failed for draftId={}", draftId, e);
            return org.springframework.http.ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
