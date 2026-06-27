package com.ksh.classes.controller;

import com.ksh.auth.Roles;
import com.ksh.classes.dto.ClassesDtos.ClassForm;
import com.ksh.classes.entity.ClassEntity;
import com.ksh.classes.service.ClassMembersService;
import com.ksh.classes.service.ClassesService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * Controller for the lecturer's class management screen.
 * Restricted to LECTURER, HEAD, and ADMIN roles (see {@link Roles}).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /lecturer/classes}            — list classes</li>
 *   <li>{@code GET  /lecturer/classes/new}        — create class form</li>
 *   <li>{@code POST /lecturer/classes}            — submit class creation</li>
 *   <li>{@code GET  /lecturer/classes/{id}/edit}  — edit class form</li>
 *   <li>{@code POST /lecturer/classes/{id}}       — submit class modifications</li>
 *   <li>{@code POST /lecturer/classes/{id}/delete}— soft-delete class</li>
 * </ul>
 *
 * <p>Validation: {@code @Valid ClassForm + BindingResult}. Errors are rendered
 * inline under each field using {@code th:errors}, inputs are preserved.
 *
 * <p>Authorization: class-level {@code @PreAuthorize} blocks STUDENT/anonymous users.
 * Owner checks (e.g. LECTURER can only edit their own class) are enforced at the service layer.
 * 404 and 403 maps are handled via {@code GlobalExceptionHandler}.
 */
@Controller
@RequestMapping("/lecturer")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class ClassesController {

    private final ClassesService classesService;
    private final ClassMembersService classMembersService;

    public ClassesController(ClassesService classesService,
                             ClassMembersService classMembersService) {
        this.classesService = classesService;
        this.classMembersService = classMembersService;
    }

    @GetMapping("/classes")
    public String list(Principal principal, Model model) {
        model.addAttribute("classes", classesService.listForUser(principal));
        return "classes/manage";
    }

    @GetMapping("/classes/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", ClassForm.empty());
        }
        model.addAttribute("mode", "create");
        model.addAttribute("formAction", "/lecturer/classes");
        return "classes/form";
    }

    @PostMapping("/classes")
    public String create(@Valid @ModelAttribute("form") ClassForm form,
                         BindingResult result,
                         Principal principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            rebindDateRangeError(result);
            model.addAttribute("mode", "create");
            model.addAttribute("formAction", "/lecturer/classes");
            return "classes/form";
        }
        ClassEntity saved = classesService.create(form, principal);
        ra.addFlashAttribute("flashSuccess", "Đã tạo lớp " + saved.getCode());
        return "redirect:/lecturer/classes";
    }

    @GetMapping("/classes/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        ClassEntity entity = classesService.getEditable(id, principal);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", ClassForm.fromEntity(entity));
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("formAction", "/lecturer/classes/" + id);
        model.addAttribute("classId", id);
        return "classes/form";
    }

    @PostMapping("/classes/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ClassForm form,
                         BindingResult result,
                         Principal principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            rebindDateRangeError(result);
            model.addAttribute("mode", "edit");
            model.addAttribute("formAction", "/lecturer/classes/" + id);
            model.addAttribute("classId", id);
            return "classes/form";
        }
        classesService.update(id, form, principal);
        ra.addFlashAttribute("flashSuccess", "Đã cập nhật lớp");
        return "redirect:/lecturer/classes";
    }

    @PostMapping("/classes/{id}/delete")
    public String delete(@PathVariable Long id,
                         Principal principal,
                         RedirectAttributes ra) {
        classesService.softDelete(id, principal);
        ra.addFlashAttribute("flashSuccess", "Đã xoá lớp");
        return "redirect:/lecturer/classes";
    }

    // ───────── Class Detail Page — sidebar tabs (Sprint 2 phase 2) ─────────
    //
    // URL pattern: /lecturer/classes/{id}/{tab}
    //   - /board     : Board (default tab when entering /lecturer/classes/{id})
    //   - /members   : Members (wired with real data)
    //   - /schedule, /roles, /groups, /assignments, /scores, /lessons,
    //     /materials  : placeholder (Sprint 3-5)
    //   - /settings  : form to edit the class (reuses classes/form.html)
    //
    // All endpoints use the same class-detail layout (sidebar + main).

    @GetMapping("/classes/{id}")
    public String detailRoot(@PathVariable Long id) {
        return "redirect:/lecturer/classes/" + id + "/board";
    }

    @GetMapping("/classes/{id}/board")
    public String detailBoard(@PathVariable Long id, Principal principal, Model model) {
        ClassEntity clazz = classesService.getViewable(id, principal);
        populateDetailModel(model, clazz, "board");
        return "classes/detail-board";
    }

    @GetMapping("/classes/{id}/members")
    public String detailMembers(@PathVariable Long id, Principal principal, Model model) {
        ClassMembersService.ClassMembersView view = classMembersService.listForClass(id, principal);
        populateDetailModel(model, view.clazz(), "members");
        model.addAttribute("members", view.members());
        model.addAttribute("memberTotal", view.total());
        return "classes/detail-members";
    }

    @GetMapping({"/classes/{id}/schedule", "/classes/{id}/roles",
                "/classes/{id}/groups", "/classes/{id}/assignments",
                "/classes/{id}/scores", "/classes/{id}/lessons",
                "/classes/{id}/materials"})
    public String detailPlaceholder(@PathVariable Long id,
                                    Principal principal,
                                    jakarta.servlet.http.HttpServletRequest request,
                                    Model model) {
        ClassEntity clazz = classesService.getViewable(id, principal);
        String path = request.getRequestURI();
        String tab = path.substring(path.lastIndexOf('/') + 1);
        populateDetailModel(model, clazz, tab);
        model.addAttribute("placeholderTab", tab);
        model.addAttribute("placeholderLabel", labelFor(tab));
        return "classes/detail-placeholder";
    }

    @GetMapping("/classes/{id}/settings")
    public String detailSettings(@PathVariable Long id, Principal principal, Model model) {
        ClassEntity entity = classesService.getEditable(id, principal);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", ClassForm.fromEntity(entity));
        }
        populateDetailModel(model, entity, "settings");
        model.addAttribute("mode", "edit");
        model.addAttribute("formAction", "/lecturer/classes/" + id);
        model.addAttribute("classId", id);
        return "classes/detail-settings";
    }

    /** Populates common attributes for class detail layout (sidebar). */
    private void populateDetailModel(Model model, ClassEntity clazz, String activeTab) {
        model.addAttribute("clazz", clazz);
        model.addAttribute("activeTab", activeTab);
    }

    private static String labelFor(String tab) {
        return switch (tab) {
            case "board" -> "Bảng tin";
            case "schedule" -> "Lịch học";
            case "members" -> "Thành viên";
            case "roles" -> "Vai trò lớp";
            case "groups" -> "Nhóm học tập";
            case "assignments" -> "Bài tập";
            case "scores" -> "Bảng điểm";
            case "lessons" -> "Bài giảng";
            case "materials" -> "Tài liệu";
            case "settings" -> "Cài đặt lớp học";
            default -> tab;
        };
    }

    /**
     * {@code @AssertTrue isDateRangeValid()} generates a global error with
     * field-name {@code dateRangeValid}. Rebinds it to a field error
     * on {@code endDate} so the template can render it inline in the correct place.
     */
    private void rebindDateRangeError(BindingResult result) {
        result.getFieldErrors("dateRangeValid").forEach(err ->
                result.rejectValue("endDate", "dateRange.invalid", err.getDefaultMessage())
        );
    }
}
