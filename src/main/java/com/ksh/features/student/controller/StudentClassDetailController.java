package com.ksh.features.student.controller;

import com.ksh.features.student.service.StudentClassDetailService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/** Student class overview pages sharing one flat class-detail shell. */
@Controller
@RequestMapping("/my/classes/{classId}")
@PreAuthorize(Roles.PREAUTH_STUDENT)
public class StudentClassDetailController {

    private final StudentClassDetailService detailService;

    public StudentClassDetailController(StudentClassDetailService detailService) {
        this.detailService = detailService;
    }

    @GetMapping
    public String root(@PathVariable Long classId) {
        return "redirect:/my/classes/" + classId + "/board";
    }

    @GetMapping("/board")
    public String board(@PathVariable Long classId,
                        @AuthenticationPrincipal KshUserDetails user,
                        Model model) {
        model.addAttribute("view", detailService.get(classId, user.getId()));
        return "student/class-board";
    }

    @GetMapping("/members")
    public String members(@PathVariable Long classId,
                          @AuthenticationPrincipal KshUserDetails user,
                          Model model) {
        model.addAttribute("view", detailService.get(classId, user.getId()));
        return "student/class-members";
    }
}
