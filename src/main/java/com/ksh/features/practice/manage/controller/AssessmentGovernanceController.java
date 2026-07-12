package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.assessment.AssessmentGovernanceService;
import com.ksh.features.practice.manage.service.PracticeStorageReadinessService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/practice/manage/governance/assessment")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class AssessmentGovernanceController {

    private final AssessmentGovernanceService governanceService;
    private final PracticeStorageReadinessService storageReadinessService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public AssessmentGovernanceController(AssessmentGovernanceService governanceService,
                                          PracticeStorageReadinessService storageReadinessService,
                                          AuthenticatedUserIdResolver userIdResolver) {
        this.governanceService = governanceService;
        this.storageReadinessService = storageReadinessService;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/storage-readiness")
    public PracticeStorageReadinessService.Readiness storageReadiness(
            Authentication authentication) {
        // The governance permission is checked by a no-op profile-safe query path.
        governanceService.requireAccess(userIdResolver.resolve(authentication));
        return storageReadinessService.readiness();
    }

    @PostMapping("/programs/{programCode}/versions")
    public Object createProgramVersion(
            @PathVariable String programCode,
            @RequestBody AssessmentGovernanceService.ProgramVersionRequest request,
            Authentication authentication) {
        return governanceService.createProgramVersion(
                programCode, request, userIdResolver.resolve(authentication));
    }

    @PostMapping("/programs/{programCode}/versions/{versionId}/activate")
    public Object activateProgramVersion(
            @PathVariable String programCode,
            @PathVariable Long versionId,
            Authentication authentication) {
        return governanceService.activateProgramVersion(
                programCode, versionId, userIdResolver.resolve(authentication));
    }

    @PostMapping("/templates/{templateCode}/versions")
    public Object createTemplateVersion(
            @PathVariable String templateCode,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        return governanceService.createTemplateVersion(
                templateCode, request.get("configJson"), userIdResolver.resolve(authentication));
    }

    @PostMapping("/templates/{templateCode}/versions/{versionId}/activate")
    public Object activateTemplateVersion(
            @PathVariable String templateCode,
            @PathVariable Long versionId,
            Authentication authentication) {
        return governanceService.activateTemplateVersion(
                templateCode, versionId, userIdResolver.resolve(authentication));
    }

    @PostMapping("/profiles/scoring/{code}")
    public Object createScoringProfile(
            @PathVariable String code,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        return governanceService.createScoringProfile(
                code, request.get("configJson"), userIdResolver.resolve(authentication));
    }

    @PostMapping("/profiles/prompt/{code}")
    public Object createPromptProfile(
            @PathVariable String code,
            @RequestBody AssessmentGovernanceService.PromptProfileRequest request,
            Authentication authentication) {
        return governanceService.createPromptProfile(
                code, request, userIdResolver.resolve(authentication));
    }

    @PostMapping("/profiles/rubric/{code}")
    public Object createRubricProfile(
            @PathVariable String code,
            @RequestBody AssessmentGovernanceService.RubricProfileRequest request,
            Authentication authentication) {
        return governanceService.createRubricProfile(
                code, request, userIdResolver.resolve(authentication));
    }

    @PostMapping("/profiles/{kind}/{profileId}/activate")
    public Map<String, String> activateProfile(
            @PathVariable AssessmentGovernanceService.ProfileKind kind,
            @PathVariable Long profileId,
            Authentication authentication) {
        governanceService.activateProfile(
                kind, profileId, userIdResolver.resolve(authentication));
        return Map.of("status", "active");
    }
}
