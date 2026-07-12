package com.ksh.features.practice.manage.controller;
 
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
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
    private final com.ksh.features.practice.manage.service.PracticeRevisionService revisionService;
    private final com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository;
    private final com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository collaborationRepository;
    private final com.ksh.features.practice.governance.PracticeLifecycleService lifecycleService;
    private final com.ksh.features.practice.governance.PracticeCollaborationService collaborationService;
    private final com.ksh.features.practice.manage.service.PracticeOverrideContextService overrideContextService;
 
    public PracticeManageController(PracticeSetRepository setRepository,
                                    PracticeDraftRepository draftRepository,
                                    com.ksh.features.auth.repository.UserRepository userRepository,
                                    com.ksh.features.practice.manage.service.PracticeDraftService draftService,
                                    com.ksh.features.practice.manage.service.PracticeRevisionService revisionService,
                                    com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository,
                                    com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository collaborationRepository,
                                    com.ksh.features.practice.governance.PracticeLifecycleService lifecycleService,
                                    com.ksh.features.practice.governance.PracticeCollaborationService collaborationService,
                                    com.ksh.features.practice.manage.service.PracticeOverrideContextService overrideContextService) {
        this.setRepository = setRepository;
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
        this.draftService = draftService;
        this.revisionService = revisionService;
        this.publishedVersionRepository = publishedVersionRepository;
        this.collaborationRepository = collaborationRepository;
        this.lifecycleService = lifecycleService;
        this.collaborationService = collaborationService;
        this.overrideContextService = overrideContextService;
    }
 
    @GetMapping("/sets/{setId}/edit")
    public String editSet(@org.springframework.web.bind.annotation.PathVariable("setId") Long setId,
                          @AuthenticationPrincipal KshUserDetails user,
                          jakarta.servlet.http.HttpSession session) {
        String overrideReason = overrideContextService.reasonForSet(session, setId, null);
        com.ksh.entities.PracticeDraft draft = draftService.createDraftFromPublishedSet(
                setId, user.getId(), overrideReason);
        if (overrideReason != null) {
            overrideContextService.establishForDraft(
                    session, draft.getId(), overrideReason);
        }
        return "redirect:/practice/manage/drafts/" + draft.getId();
    }

    @org.springframework.web.bind.annotation.PostMapping("/sets/{setId}/override-edit")
    public String overrideEditSet(
            @org.springframework.web.bind.annotation.PathVariable("setId") Long setId,
            @org.springframework.web.bind.annotation.RequestParam("overrideReason")
            String overrideReason,
            @AuthenticationPrincipal KshUserDetails user,
            jakarta.servlet.http.HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            com.ksh.entities.PracticeDraft draft = draftService
                    .createDraftFromPublishedSet(setId, user.getId(), overrideReason);
            overrideContextService.establishForSet(session, setId, overrideReason);
            overrideContextService.establishForDraft(
                    session, draft.getId(), overrideReason);
            return "redirect:/practice/manage/drafts/" + draft.getId();
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/practice/manage";
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/drafts/{draftId}/override-edit")
    public String overrideEditDraft(
            @org.springframework.web.bind.annotation.PathVariable("draftId") Long draftId,
            @org.springframework.web.bind.annotation.RequestParam("overrideReason")
            String overrideReason,
            @AuthenticationPrincipal KshUserDetails user,
            jakarta.servlet.http.HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            com.ksh.entities.PracticeDraft draft = draftService.openDraftForEdit(
                    draftId, user.getId(), overrideReason);
            overrideContextService.establishForDraft(
                    session, draft.getId(), overrideReason);
            return "redirect:/practice/manage/drafts/" + draft.getId();
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/practice/manage";
        }
    }
 
    @GetMapping({"", "/"})
    public String dashboard(@RequestParam(value = "status", required = false) String status,
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
        java.util.Map<Long, String> collaboratorEmailsMap = new java.util.HashMap<>();
        java.util.Map<Long, List<com.ksh.entities.PracticeAuthoringCollaboration>>
                collaboratorsBySet = new java.util.LinkedHashMap<>();
        for (PracticeSet s : sets) {
            if (s.getCreatedBy() != null && !authorsMap.containsKey(s.getCreatedBy())) {
                userRepository.findById(s.getCreatedBy())
                        .ifPresent(u -> authorsMap.put(s.getCreatedBy(), u.getFullName()));
            }
            List<com.ksh.entities.PracticeAuthoringCollaboration> grants =
                    collaborationRepository
                            .findByTargetTypeAndTargetIdAndRevokedAtIsNull(
                                    com.ksh.entities.PracticeAuthoringCollaboration.TARGET_SET,
                                    s.getId());
            collaboratorsBySet.put(s.getId(), grants);
            for (com.ksh.entities.PracticeAuthoringCollaboration grant : grants) {
                userRepository.findById(grant.getCollaboratorId()).ifPresent(collaborator -> {
                    authorsMap.put(grant.getCollaboratorId(), collaborator.getFullName());
                    collaboratorEmailsMap.put(
                            grant.getCollaboratorId(), collaborator.getEmail());
                });
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

        boolean canEmergencyOverride = user.getRole() == com.ksh.security.Role.HEAD
                || user.getRole() == com.ksh.security.Role.ADMIN;
        int reviewLimit = 50;
        org.springframework.data.domain.Pageable reviewWindow =
                org.springframework.data.domain.PageRequest.of(0, reviewLimit);
        List<PracticeSet> reviewSets = canEmergencyOverride
                ? setRepository.findByCreatedByNotOrderByCreatedAtDesc(
                        user.getId(), reviewWindow)
                : List.of();
        List<com.ksh.entities.PracticeDraft> reviewDrafts = canEmergencyOverride
                ? draftRepository.findByOwnerIdNotOrderByUpdatedAtDesc(
                        user.getId(), reviewWindow)
                : List.of();
        for (PracticeSet review : reviewSets) {
            userRepository.findById(review.getCreatedBy())
                    .ifPresent(owner -> authorsMap.put(
                            review.getCreatedBy(), owner.getFullName()));
        }
        for (com.ksh.entities.PracticeDraft review : reviewDrafts) {
            userRepository.findById(review.getOwnerId())
                    .ifPresent(owner -> authorsMap.put(
                            review.getOwnerId(), owner.getFullName()));
        }
 
        model.addAttribute("sets", sets);
        model.addAttribute("drafts", drafts);
        model.addAttribute("sharedSets", sharedSets);
        model.addAttribute("sharedDrafts", sharedDrafts);
        model.addAttribute("reviewSets", reviewSets);
        model.addAttribute("reviewDrafts", reviewDrafts);
        model.addAttribute("canEmergencyOverride", canEmergencyOverride);
        model.addAttribute("reviewLimit", reviewLimit);
        model.addAttribute("publishedCount", publishedCount);
        model.addAttribute("authorsMap", authorsMap);
        model.addAttribute("collaboratorEmailsMap", collaboratorEmailsMap);
        model.addAttribute("collaboratorsBySet", collaboratorsBySet);
        model.addAttribute("activeStatus", status != null ? status : "ALL");
        return "practice/manage/dashboard";
    }

    @GetMapping("/revisions")
    public String revisions(
            @RequestParam(value = "setId", required = false) Long requestedSetId,
            @AuthenticationPrincipal KshUserDetails user,
            Model model) {
        java.util.Map<Long, String> authorsMap = new java.util.HashMap<>();
        model.addAttribute("authorsMap", authorsMap);
        List<VersionHistoryRow> versions = new java.util.ArrayList<>();
        PracticeSet selectedSet = null;
        List<PracticeSet> ownedSets = setRepository.findByCreatedByOrderByCreatedAtDesc(user.getId());
        java.util.Set<Long> visibleSetIds = ownedSets.stream().map(PracticeSet::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        boolean canEmergencyOverride = user.getRole() == com.ksh.security.Role.HEAD
                || user.getRole() == com.ksh.security.Role.ADMIN;
        if (requestedSetId != null) {
            PracticeSet requested = setRepository.findById(requestedSetId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Học liệu không tồn tại."));
            boolean collaborator = collaborationRepository
                    .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                            com.ksh.entities.PracticeAuthoringCollaboration.TARGET_SET,
                            requestedSetId, user.getId())
                    .isPresent();
            if (!user.getId().equals(requested.getCreatedBy())
                    && !collaborator && !canEmergencyOverride) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Bạn không có quyền xem lịch sử học liệu này.");
            }
            visibleSetIds.clear();
            visibleSetIds.add(requestedSetId);
            selectedSet = requested;
        } else if (canEmergencyOverride) {
            setRepository.findAll().stream().map(PracticeSet::getId)
                    .forEach(visibleSetIds::add);
        } else {
            collaborationRepository
                    .findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(user.getId())
                    .stream()
                    .filter(grant -> com.ksh.entities.PracticeAuthoringCollaboration.TARGET_SET
                            .equals(grant.getTargetType()))
                    .map(com.ksh.entities.PracticeAuthoringCollaboration::getTargetId)
                    .forEach(visibleSetIds::add);
        }
        for (PracticeSet set : setRepository.findAllById(visibleSetIds)) {
            userRepository.findById(set.getCreatedBy())
                    .ifPresent(owner -> authorsMap.put(
                            set.getCreatedBy(), owner.getFullName()));
            java.util.Optional<com.ksh.entities.PracticeAuthoringCollaboration> grant =
                    collaborationRepository
                            .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                                    com.ksh.entities.PracticeAuthoringCollaboration.TARGET_SET,
                                    set.getId(), user.getId());
            boolean owner = user.getId().equals(set.getCreatedBy());
            boolean normalRestoreGrant = !set.isOwnerLocked()
                    && grant.map(com.ksh.entities.PracticeAuthoringCollaboration::isCanRestore)
                    .orElse(false);
            boolean canRestore = owner || normalRestoreGrant || canEmergencyOverride;
            boolean requiresOverrideReason = canEmergencyOverride
                    && !owner && !normalRestoreGrant;
            for (com.ksh.entities.PracticePublishedVersion version :
                    publishedVersionRepository.findBySetIdOrderByVersionNumberDesc(set.getId())) {
                versions.add(new VersionHistoryRow(
                        version, set, canRestore, requiresOverrideReason));
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
        model.addAttribute("canEmergencyOverride", canEmergencyOverride);
        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("selectedSetId", requestedSetId);
        model.addAttribute("selectedSet", selectedSet);
        return "practice/manage/revisions";
    }

    @org.springframework.web.bind.annotation.PostMapping("/sets/{setId}/versions/{versionId}/restore")
    public String restorePublishedVersion(
            @org.springframework.web.bind.annotation.PathVariable Long setId,
            @org.springframework.web.bind.annotation.PathVariable Long versionId,
            @org.springframework.web.bind.annotation.RequestParam(value = "overrideReason", required = false)
            String overrideReason,
            @AuthenticationPrincipal KshUserDetails user,
            jakarta.servlet.http.HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            String reason = overrideContextService.reasonForSet(
                    session, setId, overrideReason);
            revisionService.restorePublishedVersion(
                    setId, versionId, user.getId(), reason);
            redirectAttributes.addFlashAttribute("success",
                    "Đã tạo phiên bản xuất bản mới từ lịch sử đã chọn.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/practice/manage/revisions?setId=" + setId;
    }

    @org.springframework.web.bind.annotation.PostMapping("/sets/{setId}/{action:lock|unlock|archive|unarchive}")
    public String lifecycle(
            @org.springframework.web.bind.annotation.PathVariable Long setId,
            @org.springframework.web.bind.annotation.PathVariable String action,
            @org.springframework.web.bind.annotation.RequestParam(value = "overrideReason", required = false)
            String overrideReason,
            @AuthenticationPrincipal KshUserDetails user,
            jakarta.servlet.http.HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            String reason = overrideContextService.reasonForSet(
                    session, setId, overrideReason);
            switch (action) {
                case "lock" -> lifecycleService.lockSet(setId, user.getId(), reason);
                case "unlock" -> lifecycleService.unlockSet(setId, user.getId(), reason);
                case "archive" -> lifecycleService.archiveSet(setId, user.getId(), reason);
                case "unarchive" -> lifecycleService.unarchiveSet(setId, user.getId(), reason);
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
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean canEdit,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean canPublish,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean canRestore,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean canManageMaterial,
            @org.springframework.web.bind.annotation.RequestParam(value = "overrideReason", required = false)
            String overrideReason,
            @AuthenticationPrincipal KshUserDetails user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            collaborationService.shareSetByEmail(setId, email,
                    new com.ksh.features.practice.governance.PracticeCollaborationService.Grants(
                            canEdit, canPublish, canRestore, canManageMaterial),
                    user.getId(), overrideReason);
            redirectAttributes.addFlashAttribute("success", "Đã chia sẻ học liệu.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/practice/manage";
    }

    @org.springframework.web.bind.annotation.PostMapping(
            "/sets/{setId}/collaborators/{collaboratorId}/revoke")
    public String revokeSetCollaboration(
            @org.springframework.web.bind.annotation.PathVariable Long setId,
            @org.springframework.web.bind.annotation.PathVariable Long collaboratorId,
            @AuthenticationPrincipal KshUserDetails user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            collaborationService.revokeSet(
                    setId, collaboratorId, user.getId(), null);
            redirectAttributes.addFlashAttribute(
                    "success", "Đã thu hồi quyền cộng tác.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/practice/manage";
    }

    public record VersionHistoryRow(com.ksh.entities.PracticePublishedVersion version,
                                    PracticeSet set,
                                    boolean canRestore,
                                    boolean requiresOverrideReason) {
    }
}
