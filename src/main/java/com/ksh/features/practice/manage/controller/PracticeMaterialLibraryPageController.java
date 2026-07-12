package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PracticeMaterialLibraryService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/practice/manage/materials")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeMaterialLibraryPageController {
    private final PracticeMaterialLibraryService libraryService;
    private final LecturerAssetService assetService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public PracticeMaterialLibraryPageController(
            PracticeMaterialLibraryService libraryService,
            LecturerAssetService assetService,
            AuthenticatedUserIdResolver userIdResolver) {
        this.libraryService = libraryService;
        this.assetService = assetService;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        model.addAttribute("catalog",
                libraryService.catalog(userIdResolver.resolve(authentication)));
        return "practice/manage/material-library";
    }

    @PostMapping("/{assetId}/delete")
    public String delete(@PathVariable Long assetId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            assetService.deleteAsset(assetId, userIdResolver.resolve(authentication));
            redirectAttributes.addFlashAttribute(
                    "success", "Đã cập nhật vòng đời tài nguyên.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/practice/manage/materials";
    }
}
