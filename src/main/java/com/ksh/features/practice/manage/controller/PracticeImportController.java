package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.manage.service.PracticePdfImportSessionService;
import com.ksh.features.practice.manage.service.PracticeOverrideContextService;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeImportController {

    private final PracticePdfImportSessionService importSessionService;
    private final PracticePdfImportSessionRepository sessionRepository;
    private final AssessmentAuthoringCatalogService authoringCatalogService;
    private final PracticeOverrideContextService overrideContextService;

    public PracticeImportController(PracticePdfImportSessionService importSessionService,
                                    PracticePdfImportSessionRepository sessionRepository,
                                    AssessmentAuthoringCatalogService authoringCatalogService,
                                    PracticeOverrideContextService overrideContextService) {
        this.importSessionService = importSessionService;
        this.sessionRepository = sessionRepository;
        this.authoringCatalogService = authoringCatalogService;
        this.overrideContextService = overrideContextService;
    }

    @GetMapping("/practice/manage/import")
    public String showImportStartPage(@RequestParam(value = "draftId", required = false) Long draftId,
                                      @RequestParam(value = "testNo", required = false) Integer testNo,
                                      @RequestParam(value = "lessonCode", required = false) String lessonCode,
                                      @AuthenticationPrincipal KshUserDetails user,
                                      jakarta.servlet.http.HttpSession session,
                                      Model model) {
        model.addAttribute("draftId", draftId);
        PracticePdfImportSessionService.PdfImportStartContext targetContext =
                importSessionService.resolveStartContext(draftId, testNo, lessonCode,
                        user.getId(), draftId == null ? null
                                : overrideContextService.reasonForDraft(
                                        session, draftId, null));
        model.addAttribute("pdfImportContext", targetContext);
        model.addAttribute("selectedTemplateCode",
                targetContext == null ? "TOPIK_II" : targetContext.templateCode());
        
        // Fetch recent import sessions for the user to list on the right column
        List<PracticePdfImportSession> recentSessions = sessionRepository
                .findByUploaderIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("recentSessions", recentSessions);
        model.addAttribute("authoringCatalog", authoringCatalogService.catalog());
        
        return "practice/manage/import-wizard";
    }

    @GetMapping("/practice/manage/import-sessions/{sessionId}/workspace")
    public String showWorkspace(@PathVariable Long sessionId,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model) {
        // Validate access
        PracticePdfImportSession session = importSessionService.getSession(sessionId, user.getId());
        
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("sessionInfo", session);
        model.addAttribute("draftId", session.getLinkedDraftId());
        model.addAttribute("canViewAiRequest",
                user.getRole() == com.ksh.security.Role.HEAD || user.getRole() == com.ksh.security.Role.ADMIN);
        
        return "practice/manage/import-workspace";
    }
}
