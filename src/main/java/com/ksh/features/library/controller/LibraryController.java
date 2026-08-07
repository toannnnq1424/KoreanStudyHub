package com.ksh.features.library.controller;

import com.ksh.features.library.service.LessonTemplateService;
import com.ksh.features.library.service.LibrarySubjectResolver;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lecturer/library")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class LibraryController {

    private final LibrarySubjectResolver subjectResolver;
    private final LessonTemplateService lessonTemplateService;

    public LibraryController(LibrarySubjectResolver subjectResolver,
                           LessonTemplateService lessonTemplateService) {
        this.subjectResolver = subjectResolver;
        this.lessonTemplateService = lessonTemplateService;
    }

    @GetMapping
    public String library() {
        return "redirect:/lecturer/library/list";
    }

    @GetMapping("/list")
    public String listSubjects(Model model, @AuthenticationPrincipal KshUserDetails userDetails) {
        var subjectStats = lessonTemplateService.getLibrarySubjectStats(
                userDetails.getId(), userDetails.getRole());
        model.addAttribute("librarySubjectStats", subjectStats);
        return "library/list-library";
    }
}
