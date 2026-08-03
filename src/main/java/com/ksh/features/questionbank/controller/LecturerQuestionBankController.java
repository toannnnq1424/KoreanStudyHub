package com.ksh.features.questionbank.controller;

import com.ksh.features.questionbank.dto.QuestionBankItemForm;
import com.ksh.features.questionbank.service.QuestionBankItemService;
import com.ksh.features.questionbank.service.QuestionBankValidationException;
import com.ksh.security.Roles;
import com.ksh.security.KshUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.ksh.common.IConstant.ATTR_FORM;
import static com.ksh.common.IConstant.ATTR_MODE;
import static com.ksh.common.IConstant.ATTR_QB_DETAIL;
import static com.ksh.common.IConstant.ATTR_QB_EMPTY_DEPARTMENT;
import static com.ksh.common.IConstant.ATTR_QB_ITEMS;
import static com.ksh.common.IConstant.ATTR_QB_QUERY;
import static com.ksh.common.IConstant.ATTR_QB_SELECTED_STATUS;
import static com.ksh.common.IConstant.BASE_LECTURER_QUESTION_BANK;
import static com.ksh.common.IConstant.MODE_CREATE;
import static com.ksh.common.IConstant.MODE_EDIT;
import static com.ksh.common.IConstant.MSG_QB_DRAFT_SAVED;
import static com.ksh.common.IConstant.MSG_QB_RESUBMITTED;
import static com.ksh.common.IConstant.MSG_QB_SUBMITTED;
import static com.ksh.common.IConstant.MSG_QB_UPDATED;
import static com.ksh.common.IConstant.URL_LECTURER_QUESTION_BANK;
import static com.ksh.common.IConstant.VIEW_QB_DETAIL;
import static com.ksh.common.IConstant.VIEW_QB_FORM;
import static com.ksh.common.IConstant.VIEW_QB_LIST;

/** Lecturer contribution screens for the subject-scoped shared question bank. */
@Controller
@RequestMapping(BASE_LECTURER_QUESTION_BANK)
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class LecturerQuestionBankController {

    private final QuestionBankItemService itemService;

    public LecturerQuestionBankController(QuestionBankItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public String list(@RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "q", required = false) String q,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model) {
        boolean emptyDepartment = !itemService.hasSubject(user.getId(), user.getRole());
        model.addAttribute(ATTR_QB_EMPTY_DEPARTMENT, emptyDepartment);
        if (emptyDepartment) {
            model.addAttribute(ATTR_QB_ITEMS, java.util.List.of());
            model.addAttribute(ATTR_QB_SELECTED_STATUS, status);
            model.addAttribute(ATTR_QB_QUERY, q);
            return VIEW_QB_LIST;
        }
        model.addAttribute(ATTR_QB_ITEMS,
                itemService.list(user.getId(), user.getRole(), status, null, q));
        model.addAttribute(ATTR_QB_SELECTED_STATUS, status);
        model.addAttribute(ATTR_QB_QUERY, q);
        return VIEW_QB_LIST;
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal KshUserDetails user, Model model) {
        if (!model.containsAttribute(ATTR_FORM)) {
            model.addAttribute(ATTR_FORM, QuestionBankItemForm.empty());
        }
        populateForm(model, user, MODE_CREATE);
        return VIEW_QB_FORM;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute(ATTR_FORM) QuestionBankItemForm form,
                         BindingResult result,
                         @AuthenticationPrincipal KshUserDetails user,
                         Model model,
                         RedirectAttributes ra) {
        form.ensureMinOptions(4);
        if (result.hasErrors()) {
            populateForm(model, user, MODE_CREATE);
            return VIEW_QB_FORM;
        }
        try {
            Long id = itemService.save(user.getId(), user.getRole(), form);
            ra.addFlashAttribute("flashSuccess",
                    "REVIEW".equalsIgnoreCase(form.getWorkflowAction())
                            ? MSG_QB_SUBMITTED
                            : MSG_QB_DRAFT_SAVED);
            return redirectDetail(id);
        } catch (QuestionBankValidationException ex) {
            model.addAttribute("flashError", ex.getMessage());
            populateForm(model, user, MODE_CREATE);
            return VIEW_QB_FORM;
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal KshUserDetails user,
                         Model model,
                         RedirectAttributes ra) {
        try {
            model.addAttribute(ATTR_QB_DETAIL, itemService.detail(user.getId(), user.getRole(), id));
            return VIEW_QB_DETAIL;
        } catch (QuestionBankValidationException | AccessDeniedException ex) {
            ra.addFlashAttribute("flashError", ex.getMessage());
            return redirectList();
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal KshUserDetails user,
                           Model model,
                           RedirectAttributes ra) {
        try {
            if (!model.containsAttribute(ATTR_FORM)) {
                model.addAttribute(ATTR_FORM, itemService.loadForm(user.getId(), user.getRole(), id));
            }
            populateForm(model, user, MODE_EDIT);
            return VIEW_QB_FORM;
        } catch (QuestionBankValidationException | AccessDeniedException ex) {
            ra.addFlashAttribute("flashError", ex.getMessage());
            return redirectList();
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute(ATTR_FORM) QuestionBankItemForm form,
                         BindingResult result,
                         @AuthenticationPrincipal KshUserDetails user,
                         Model model,
                         RedirectAttributes ra) {
        form.setId(id);
        form.ensureMinOptions(4);
        if (result.hasErrors()) {
            populateForm(model, user, MODE_EDIT);
            return VIEW_QB_FORM;
        }
        try {
            itemService.save(user.getId(), user.getRole(), form);
            ra.addFlashAttribute("flashSuccess",
                    "REVIEW".equalsIgnoreCase(form.getWorkflowAction())
                            ? MSG_QB_RESUBMITTED
                            : MSG_QB_UPDATED);
            return redirectDetail(id);
        } catch (QuestionBankValidationException | AccessDeniedException ex) {
            model.addAttribute("flashError", ex.getMessage());
            populateForm(model, user, MODE_EDIT);
            return VIEW_QB_FORM;
        }
    }

    private void populateForm(Model model, KshUserDetails user, String mode) {
        model.addAttribute(ATTR_MODE, mode);
        boolean emptyDepartment = !itemService.hasSubject(user.getId(), user.getRole());
        model.addAttribute(ATTR_QB_EMPTY_DEPARTMENT, emptyDepartment);
    }

    private static String redirectList() {
        return "redirect:" + URL_LECTURER_QUESTION_BANK;
    }

    private static String redirectDetail(Long id) {
        return "redirect:" + URL_LECTURER_QUESTION_BANK + "/" + id;
    }
}
