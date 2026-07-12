package com.ksh.features.practice.manage.controller;
 
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.manage.service.PublishedPracticeGraphMutationBlockedException;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
 
import java.util.List;
 
@Controller
@RequestMapping("/practice/manage")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeManageController {
 
    private final PracticeSetRepository setRepository;
    private final PracticeDraftRepository draftRepository;
    private final com.ksh.features.auth.repository.UserRepository userRepository;
    private final com.ksh.features.practice.manage.service.PracticeDraftService draftService;
    private final com.ksh.features.practice.repository.PracticeEditLogRepository editLogRepository;
    private final com.ksh.features.practice.manage.service.PracticeRevisionService revisionService;
    private final com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository;
    private final com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository collaborationRepository;
    private final com.ksh.features.practice.governance.PracticeLifecycleService lifecycleService;
    private final com.ksh.features.practice.governance.PracticeCollaborationService collaborationService;
 
    public PracticeManageController(PracticeSetRepository setRepository,
                                    PracticeDraftRepository draftRepository,
                                    com.ksh.features.auth.repository.UserRepository userRepository,
                                    com.ksh.features.practice.manage.service.PracticeDraftService draftService,
                                    com.ksh.features.practice.repository.PracticeEditLogRepository editLogRepository,
                                    com.ksh.features.practice.manage.service.PracticeRevisionService revisionService,
                                    com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository,
                                    com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository collaborationRepository,
                                    com.ksh.features.practice.governance.PracticeLifecycleService lifecycleService,
                                    com.ksh.features.practice.governance.PracticeCollaborationService collaborationService) {
        this.setRepository = setRepository;
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
        this.draftService = draftService;
        this.editLogRepository = editLogRepository;
        this.revisionService = revisionService;
        this.publishedVersionRepository = publishedVersionRepository;
        this.collaborationRepository = collaborationRepository;
        this.lifecycleService = lifecycleService;
        this.collaborationService = collaborationService;
    }
 
    @GetMapping("/sets/{setId}/edit")
    public String editSet(@org.springframework.web.bind.annotation.PathVariable("setId") Long setId,
                          @AuthenticationPrincipal KshUserDetails user) {
        com.ksh.entities.PracticeDraft draft = draftService.createDraftFromPublishedSet(setId, user.getId());
        return "redirect:/practice/manage/drafts/" + draft.getId();
    }
 
    @GetMapping({"", "/"})
    public String dashboard(@RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "category", required = false) String category,
                            @AuthenticationPrincipal KshUserDetails user,
                            Model model) {
        List<PracticeSet> sets;
        if (status != null && !status.isBlank()) {
            sets = setRepository.findByCreatedByAndStatusOrderByCreatedAtDesc(user.getId(), status);
        } else {
            sets = setRepository.findByCreatedByOrderByCreatedAtDesc(user.getId());
        }
        
        long publishedCount = sets.stream()
                .filter(s -> "PUBLISHED".equals(s.getStatus()))
                .count();
 
        // Load author names
        java.util.Map<Long, String> authorsMap = new java.util.HashMap<>();
        for (PracticeSet s : sets) {
            if (s.getCreatedBy() != null && !authorsMap.containsKey(s.getCreatedBy())) {
                userRepository.findById(s.getCreatedBy())
                        .ifPresent(u -> authorsMap.put(s.getCreatedBy(), u.getFullName()));
            }
        }
 
        // Clean up empty drafts for this user on loading dashboard
        try {
            draftService.cleanupEmptyDrafts(user.getId());
        } catch (Exception e) {
            // log and continue
        }

        List<com.ksh.entities.PracticeDraft> drafts = draftRepository.findByOwnerIdOrderByUpdatedAtDesc(user.getId());

        List<com.ksh.entities.PracticeAuthoringCollaboration> sharedGrants =
                collaborationRepository.findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(user.getId());
        java.util.Set<Long> sharedSetIds = sharedGrants.stream()
                .filter(grant -> com.ksh.entities.PracticeAuthoringCollaboration.TARGET_SET
                        .equals(grant.getTargetType()))
                .map(com.ksh.entities.PracticeAuthoringCollaboration::getTargetId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        java.util.Set<Long> sharedDraftIds = sharedGrants.stream()
                .filter(grant -> com.ksh.entities.PracticeAuthoringCollaboration.TARGET_DRAFT
                        .equals(grant.getTargetType()))
                .map(com.ksh.entities.PracticeAuthoringCollaboration::getTargetId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<PracticeSet> sharedSets = setRepository.findAllById(sharedSetIds).stream()
                .filter(set -> status == null || status.isBlank() || status.equals(set.getStatus()))
                .toList();
        List<com.ksh.entities.PracticeDraft> sharedDrafts = draftRepository.findAllById(sharedDraftIds);
        for (PracticeSet shared : sharedSets) {
            userRepository.findById(shared.getCreatedBy())
                    .ifPresent(owner -> authorsMap.put(shared.getCreatedBy(), owner.getFullName()));
        }
        for (com.ksh.entities.PracticeDraft shared : sharedDrafts) {
            userRepository.findById(shared.getOwnerId())
                    .ifPresent(owner -> authorsMap.put(shared.getOwnerId(), owner.getFullName()));
        }
 
        model.addAttribute("sets", sets);
        model.addAttribute("drafts", drafts);
        model.addAttribute("sharedSets", sharedSets);
        model.addAttribute("sharedDrafts", sharedDrafts);
        model.addAttribute("publishedCount", publishedCount);
        model.addAttribute("authorsMap", authorsMap);
        model.addAttribute("activeStatus", status != null ? status : "ALL");
        model.addAttribute("activeCategory", category != null ? category : "ALL");
        return "practice/manage/dashboard";
    }

    @GetMapping("/revisions")
    public String revisions(@AuthenticationPrincipal KshUserDetails user, Model model) {
        java.util.Map<Long, String> authorsMap = new java.util.HashMap<>();
        model.addAttribute("authorsMap", authorsMap);
        List<VersionHistoryRow> versions = new java.util.ArrayList<>();
        List<PracticeSet> ownedSets = setRepository.findByCreatedByOrderByCreatedAtDesc(user.getId());
        java.util.Set<Long> visibleSetIds = ownedSets.stream().map(PracticeSet::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        collaborationRepository.findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(user.getId())
                .stream()
                .filter(grant -> com.ksh.entities.PracticeAuthoringCollaboration.TARGET_SET
                        .equals(grant.getTargetType()))
                .map(com.ksh.entities.PracticeAuthoringCollaboration::getTargetId)
                .forEach(visibleSetIds::add);
        for (PracticeSet set : setRepository.findAllById(visibleSetIds)) {
            for (com.ksh.entities.PracticePublishedVersion version :
                    publishedVersionRepository.findBySetIdOrderByVersionNumberDesc(set.getId())) {
                versions.add(new VersionHistoryRow(version, set));
                if (version.getPublishedBy() != null) {
                    userRepository.findById(version.getPublishedBy())
                            .ifPresent(actor -> authorsMap.put(
                                    version.getPublishedBy(), actor.getFullName()));
                }
            }
        }
        versions.sort(java.util.Comparator.comparing(
                (VersionHistoryRow row) -> row.version().getPublishedAt(),
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        model.addAttribute("versions", versions);
        return "practice/manage/revisions";
    }

    @org.springframework.web.bind.annotation.PostMapping("/sets/{setId}/versions/{versionId}/restore")
    public String restorePublishedVersion(
            @org.springframework.web.bind.annotation.PathVariable Long setId,
            @org.springframework.web.bind.annotation.PathVariable Long versionId,
            @org.springframework.web.bind.annotation.RequestParam(value = "overrideReason", required = false)
            String overrideReason,
            @AuthenticationPrincipal KshUserDetails user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            revisionService.restorePublishedVersion(
                    setId, versionId, user.getId(), overrideReason);
            redirectAttributes.addFlashAttribute("success",
                    "Đã tạo phiên bản xuất bản mới từ lịch sử đã chọn.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/practice/manage/revisions";
    }

    @org.springframework.web.bind.annotation.PostMapping("/sets/{setId}/{action:lock|unlock|archive|unarchive}")
    public String lifecycle(
            @org.springframework.web.bind.annotation.PathVariable Long setId,
            @org.springframework.web.bind.annotation.PathVariable String action,
            @org.springframework.web.bind.annotation.RequestParam(value = "overrideReason", required = false)
            String overrideReason,
            @AuthenticationPrincipal KshUserDetails user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            switch (action) {
                case "lock" -> lifecycleService.lockSet(setId, user.getId(), overrideReason);
                case "unlock" -> lifecycleService.unlockSet(setId, user.getId(), overrideReason);
                case "archive" -> lifecycleService.archiveSet(setId, user.getId(), overrideReason);
                case "unarchive" -> lifecycleService.unarchiveSet(setId, user.getId(), overrideReason);
                default -> throw new IllegalArgumentException("Hành động không hợp lệ.");
            }
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật trạng thái học liệu.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/practice/manage";
    }

    @org.springframework.web.bind.annotation.PostMapping("/sets/{setId}/share")
    public String shareSet(
            @org.springframework.web.bind.annotation.PathVariable Long setId,
            @org.springframework.web.bind.annotation.RequestParam String email,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean canEdit,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean canPublish,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean canRestore,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean canManageMaterial,
            @AuthenticationPrincipal KshUserDetails user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            collaborationService.shareSetByEmail(setId, email,
                    new com.ksh.features.practice.governance.PracticeCollaborationService.Grants(
                            canEdit, canPublish, canRestore, canManageMaterial),
                    user.getId(), null);
            redirectAttributes.addFlashAttribute("success", "Đã chia sẻ học liệu.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/practice/manage";
    }

    @org.springframework.web.bind.annotation.PostMapping("/revisions/{logId}/restore")
    public String restoreRevision(@org.springframework.web.bind.annotation.PathVariable("logId") Long logId,
                                  @AuthenticationPrincipal KshUserDetails user,
                                  org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            revisionService.restoreRevision(logId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Khôi phục phiên bản lịch sử thành công!");
        } catch (PublishedPracticeGraphMutationBlockedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể khôi phục phiên bản lịch sử.");
        }
        return "redirect:/practice/manage/revisions";
    }

    public record VersionHistoryRow(com.ksh.entities.PracticePublishedVersion version,
                                    PracticeSet set) {
    }
}
