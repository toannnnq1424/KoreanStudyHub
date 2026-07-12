package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.assessment.AssessmentGovernanceService;
import com.ksh.features.practice.manage.service.PracticeStorageReadinessService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/practice/manage/assessment-governance")
@PreAuthorize(Roles.PREAUTH_HEAD_OR_ADMIN)
public class AssessmentGovernancePageController {

    private final AssessmentGovernanceService governanceService;
    private final PracticeStorageReadinessService storageReadinessService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public AssessmentGovernancePageController(
            AssessmentGovernanceService governanceService,
            PracticeStorageReadinessService storageReadinessService,
            AuthenticatedUserIdResolver userIdResolver) {
        this.governanceService = governanceService;
        this.storageReadinessService = storageReadinessService;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        Long actorId = userIdResolver.resolve(authentication);
        model.addAttribute("catalog", governanceService.governanceCatalog(actorId));
        model.addAttribute("storageReadiness", storageReadinessService.readiness());
        return "practice/manage/assessment-governance";
    }
}
