package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.manage.service.PracticeImportTargetService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping
@PreAuthorize(Roles.PREAUTH_LECTURER)
public class PracticeImportController {

    private final PracticeImportTargetService targetService;

    public PracticeImportController(PracticeImportTargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping("/practice/manage/import")
    public String showImportStartPage(@RequestParam("draftId") Long draftId,
                                      @RequestParam("testNo") Integer testNo,
                                      @RequestParam("skill") String skill,
                                      @RequestParam("lessonCode") String lessonCode,
                                      @AuthenticationPrincipal KshUserDetails user,
                                      Model model) {
        PracticeImportTargetService.ImportStartContext targetContext;
        try {
            targetContext = targetService.resolveStartContext(
                    draftId, testNo, skill, lessonCode, user.getId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        model.addAttribute("draftId", draftId);
        model.addAttribute("pdfImportContext", targetContext);
        return "practice/manage/import-wizard";
    }
}
