package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.features.practice.governance.PracticeCollaborationService;
import com.ksh.features.practice.governance.PracticeLifecycleService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.Roles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/practice/manage/governance")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeGovernanceController {

    private final PracticeCollaborationService collaborationService;
    private final PracticeLifecycleService lifecycleService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public PracticeGovernanceController(PracticeCollaborationService collaborationService,
                                        PracticeLifecycleService lifecycleService,
                                        AuthenticatedUserIdResolver userIdResolver) {
        this.collaborationService = collaborationService;
        this.lifecycleService = lifecycleService;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/shared-with-me")
    public List<PracticeAuthoringCollaboration> sharedWithMe(
            Authentication authentication) {
        return collaborationService.sharedWith(userIdResolver.resolve(authentication));
    }

    @GetMapping("/sets/{setId}/collaborators")
    public List<PracticeAuthoringCollaboration> collaborators(
            @PathVariable Long setId,
            Authentication authentication) {
        return collaborationService.listSet(
                setId, userIdResolver.resolve(authentication), null);
    }

    @PostMapping("/sets/{setId}/collaborators")
    public PracticeAuthoringCollaboration shareSet(
            @PathVariable Long setId,
            @RequestBody CollaborationRequest request,
            Authentication authentication) {
        return collaborationService.shareSet(setId, request.collaboratorId(),
                request.grants(), userIdResolver.resolve(authentication), request.overrideReason());
    }

    @PostMapping("/drafts/{draftId}/collaborators")
    public PracticeAuthoringCollaboration shareDraft(
            @PathVariable Long draftId,
            @RequestBody CollaborationRequest request,
            Authentication authentication) {
        return collaborationService.shareDraft(draftId, request.collaboratorId(),
                request.grants(), userIdResolver.resolve(authentication), request.overrideReason());
    }

    @PostMapping("/sets/{setId}/collaborators/{collaboratorId}/revoke")
    public ResponseEntity<Map<String, String>> revokeSet(
            @PathVariable Long setId,
            @PathVariable Long collaboratorId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        collaborationService.revokeSet(setId, collaboratorId,
                userIdResolver.resolve(authentication), reason(request));
        return ResponseEntity.ok(Map.of("status", "revoked"));
    }

    @PostMapping("/drafts/{draftId}/collaborators/{collaboratorId}/revoke")
    public ResponseEntity<Map<String, String>> revokeDraft(
            @PathVariable Long draftId,
            @PathVariable Long collaboratorId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        collaborationService.revokeDraft(draftId, collaboratorId,
                userIdResolver.resolve(authentication), reason(request));
        return ResponseEntity.ok(Map.of("status", "revoked"));
    }

    @PostMapping("/sets/{setId}/lock")
    public ResponseEntity<Map<String, String>> lockSet(
            @PathVariable Long setId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        lifecycleService.lockSet(setId, userIdResolver.resolve(authentication), reason(request));
        return status("locked");
    }

    @PostMapping("/sets/{setId}/unlock")
    public ResponseEntity<Map<String, String>> unlockSet(
            @PathVariable Long setId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        lifecycleService.unlockSet(setId, userIdResolver.resolve(authentication), reason(request));
        return status("unlocked");
    }

    @PostMapping("/drafts/{draftId}/lock")
    public ResponseEntity<Map<String, String>> lockDraft(
            @PathVariable Long draftId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        lifecycleService.lockDraft(draftId, userIdResolver.resolve(authentication), reason(request));
        return status("locked");
    }

    @PostMapping("/drafts/{draftId}/unlock")
    public ResponseEntity<Map<String, String>> unlockDraft(
            @PathVariable Long draftId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        lifecycleService.unlockDraft(draftId, userIdResolver.resolve(authentication), reason(request));
        return status("unlocked");
    }

    @PostMapping("/sets/{setId}/archive")
    public ResponseEntity<Map<String, String>> archiveSet(
            @PathVariable Long setId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        lifecycleService.archiveSet(setId, userIdResolver.resolve(authentication), reason(request));
        return status("archived");
    }

    @PostMapping("/sets/{setId}/unarchive")
    public ResponseEntity<Map<String, String>> unarchiveSet(
            @PathVariable Long setId,
            @RequestBody(required = false) ReasonRequest request,
            Authentication authentication) {
        lifecycleService.unarchiveSet(setId, userIdResolver.resolve(authentication), reason(request));
        return status("published");
    }

    private static ResponseEntity<Map<String, String>> status(String value) {
        return ResponseEntity.ok(Map.of("status", value));
    }

    private static String reason(ReasonRequest request) {
        return request == null ? null : request.overrideReason();
    }

    public record CollaborationRequest(Long collaboratorId, boolean canEdit,
                                       boolean canPublish, boolean canRestore,
                                       boolean canManageMaterial,
                                       String overrideReason) {
        PracticeCollaborationService.Grants grants() {
            return new PracticeCollaborationService.Grants(
                    canEdit, canPublish, canRestore, canManageMaterial);
        }
    }

    public record ReasonRequest(String overrideReason) {
    }
}
