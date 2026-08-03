package com.ksh.features.library.controller;

import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Canonical Library entry point; loose asset management is no longer a page. */
@Controller
@RequestMapping("/lecturer/library")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class LibraryController {

    @GetMapping
    public String library() {
        return "redirect:/lecturer/library/templates";
    }
}
