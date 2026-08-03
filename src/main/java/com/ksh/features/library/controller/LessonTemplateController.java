package com.ksh.features.library.controller;

import com.ksh.features.library.dto.LibraryDtos.LessonTemplatePageView;
import com.ksh.features.library.dto.LessonTemplateForm;
import com.ksh.features.library.service.LessonTemplateService;
import com.ksh.security.Roles;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static com.ksh.common.IConstant.ATTR_FLASH_ERROR;
import static com.ksh.common.IConstant.ATTR_FLASH_SUCCESS;
import static com.ksh.common.IConstant.ATTR_LIBRARY_CLASS_OPTIONS;
import static com.ksh.common.IConstant.ATTR_LIBRARY_PAGE;
import static com.ksh.common.IConstant.ATTR_LIBRARY_QUERY;
import static com.ksh.common.IConstant.ATTR_LIBRARY_SIZE;
import static com.ksh.common.IConstant.ATTR_LIBRARY_TEMPLATE_COUNT;
import static com.ksh.common.IConstant.BASE_LECTURER;
import static com.ksh.common.IConstant.DEFAULT_LIBRARY_PAGE_SIZE;
import static com.ksh.common.IConstant.MSG_GENERIC_RETRY;
import static com.ksh.common.IConstant.MSG_TEMPLATE_DELETED;
import static com.ksh.common.IConstant.PATH_LIBRARY;
import static com.ksh.common.IConstant.URL_LIBRARY;
import static com.ksh.common.IConstant.VIEW_LIBRARY;

/**
 * Canonical subject lesson authoring and class distribution in Library.
 */
@Controller
@RequestMapping(BASE_LECTURER + PATH_LIBRARY + "/templates")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class LessonTemplateController {

    private static final Logger log = LoggerFactory.getLogger(LessonTemplateController.class);
    private static final String REDIRECT_TEMPLATES =
            "redirect:" + URL_LIBRARY + "/templates";

    private final LessonTemplateService templateService;

    public LessonTemplateController(LessonTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public String page(@RequestParam(name = "subjectId", required = false) Long subjectId,
                       @RequestParam(name = "q", defaultValue = "") String q,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size",
                               defaultValue = "" + DEFAULT_LIBRARY_PAGE_SIZE) int size,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model) {
        LessonTemplatePageView view = templateService.list(
                user.getId(), user.getRole(), subjectId, q, page, size);
        model.addAttribute(ATTR_LIBRARY_PAGE, view.page());
        model.addAttribute(ATTR_LIBRARY_QUERY, view.q());
        model.addAttribute(ATTR_LIBRARY_SIZE, view.page().getSize());
        model.addAttribute(ATTR_LIBRARY_CLASS_OPTIONS, view.classOptions());
        model.addAttribute(ATTR_LIBRARY_TEMPLATE_COUNT, view.templateCount());
        model.addAttribute("librarySubjectId", view.subjectId());
        model.addAttribute("librarySubjectCode", view.subjectCode());
        model.addAttribute("librarySubjectName", view.subjectName());
        model.addAttribute("librarySubjectDescription", view.subjectDescription());
        model.addAttribute("librarySubjectOptions", view.subjectOptions());
        model.addAttribute("libraryChapters", view.chapters());
        return VIEW_LIBRARY;
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(name = "subjectId", required = false) Long subjectId,
                             @AuthenticationPrincipal KshUserDetails user, Model model) {
        populateForm(model, templateService.loadForm(
                user.getId(), user.getRole(), null, subjectId), user);
        return "library/lesson-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal KshUserDetails user, Model model) {
        populateForm(model, templateService.loadForm(
                user.getId(), user.getRole(), id, null), user);
        return "library/lesson-form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") LessonTemplateForm form,
                       BindingResult result,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model,
                       RedirectAttributes ra) {
        if (result.hasErrors()) {
            populateForm(model, form, user);
            return "library/lesson-form";
        }
        try {
            templateService.saveForm(user.getId(), user.getRole(), form);
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, "Đã lưu bài học trong Library");
            return redirectTemplates(form.getSubjectId());
        } catch (IllegalArgumentException | EntityNotFoundException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
            return form.getId() == null
                    ? "redirect:" + URL_LIBRARY + "/templates/new?subjectId=" + form.getSubjectId()
                    : "redirect:" + URL_LIBRARY + "/templates/" + form.getId() + "/edit";
        } catch (RuntimeException ex) {
            log.error("Failed to save Library lesson for user {}", user.getId(), ex);
            ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_GENERIC_RETRY);
            return form.getId() == null
                    ? "redirect:" + URL_LIBRARY + "/templates/new?subjectId=" + form.getSubjectId()
                    : "redirect:" + URL_LIBRARY + "/templates/" + form.getId() + "/edit";
        }
    }

    @PostMapping("/{id}/distribute")
    public String distribute(@PathVariable Long id,
                             @RequestParam(name = "classIds", required = false) List<Long> classIds,
                             @AuthenticationPrincipal KshUserDetails user,
                             RedirectAttributes ra) {
        try {
            int count = templateService.distribute(
                    id, classIds, user.getId(), user.getRole()).size();
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS,
                    "Đã phân phối bài học tới " + count + " lớp");
        } catch (IllegalArgumentException | EntityNotFoundException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to distribute Library lesson {} for user {}",
                    id, user.getId(), ex);
            ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_GENERIC_RETRY);
        }
        return REDIRECT_TEMPLATES;
    }

    @PostMapping("/subjects/{subjectId}/distribute")
    public String distributeSubject(@PathVariable Long subjectId,
                                    @RequestParam(name = "classIds", required = false) List<Long> classIds,
                                    @AuthenticationPrincipal KshUserDetails user,
                                    RedirectAttributes ra) {
        try {
            List<com.ksh.features.library.dto.LibraryDtos.LessonCloneResult> results =
                    templateService.distributeSubject(subjectId, classIds,
                            user.getId(), user.getRole());
            long classCount = results.stream().map(result -> result.classId()).distinct().count();
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS,
                    "Đã phân phối toàn bộ " + results.size() + " bài học tới " + classCount + " lớp");
        } catch (IllegalArgumentException | EntityNotFoundException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to distribute Library subject {} for user {}",
                    subjectId, user.getId(), ex);
            ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_GENERIC_RETRY);
        }
        return redirectTemplates(subjectId);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal KshUserDetails user,
                         RedirectAttributes ra) {
        try {
            templateService.softDelete(user.getId(), id);
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_TEMPLATE_DELETED);
        } catch (EntityNotFoundException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to delete template {} for user {}", id, user.getId(), ex);
            ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_GENERIC_RETRY);
        }
        return REDIRECT_TEMPLATES;
    }

    private void populateForm(Model model, LessonTemplateForm form, KshUserDetails user) {
        model.addAttribute("form", form);
        model.addAttribute("materialOptions", templateService.materialOptions(user.getId()));
        model.addAttribute("librarySubject",
                templateService.subjectContext(user.getId(), user.getRole(), form.getSubjectId()));
        model.addAttribute("librarySubjectOptions",
                templateService.subjectOptions(user.getId(), user.getRole()));
    }

    private static String redirectTemplates(Long subjectId) {
        return subjectId == null ? REDIRECT_TEMPLATES
                : REDIRECT_TEMPLATES + "?subjectId=" + subjectId;
    }
}
