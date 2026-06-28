package com.ksh.student.controller;

import com.ksh.auth.service.KshUserDetails;
import com.ksh.classes.entity.ClassEntity;
import com.ksh.classes.service.InviteCodeValidationException;
import com.ksh.classes.service.JoinClassService;
import com.ksh.classes.service.JoinClassService.AlreadyJoined;
import com.ksh.classes.service.JoinClassService.JoinResult;
import com.ksh.classes.service.JoinClassService.Success;
import com.ksh.student.dto.StudentClassesDtos.EnrolledClassRow;
import com.ksh.student.dto.StudentClassesDtos.JoinForm;
import com.ksh.student.service.StudentClassesService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Student-facing controller for the {@code /my/classes} surface.
 *
 * <p>Authentication is required (gated by {@code SecurityConfig});
 * any authenticated user — STUDENT, LECTURER, HEAD, or ADMIN — can
 * use these endpoints. The intent is that lecturers can test the
 * join flow from their own account without role gymnastics.
 */
@Controller
@RequestMapping("/my")
@PreAuthorize("isAuthenticated()")
public class StudentClassesController {

    private final StudentClassesService studentClassesService;
    private final JoinClassService joinClassService;

    public StudentClassesController(StudentClassesService studentClassesService,
                                    JoinClassService joinClassService) {
        this.studentClassesService = studentClassesService;
        this.joinClassService = joinClassService;
    }

    /** Renders the list of the caller's ACTIVE enrolled classes. */
    @GetMapping("/classes")
    public String list(@AuthenticationPrincipal KshUserDetails user, Model model) {
        List<EnrolledClassRow> rows = studentClassesService.listEnrolledClasses(user.getId());
        model.addAttribute("rows", rows);
        return "student/my-classes";
    }

    /** Renders the join form. */
    @GetMapping("/classes/join")
    public String joinForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", JoinForm.empty());
        }
        return "student/join-class";
    }

    /**
     * Submits the join form. On success redirects to
     * {@code /my/classes} with a success/info toast. Validation /
     * business errors are re-rendered with inline field messages
     * sourced directly from {@link InviteCodeValidationException#getMessage()}.
     */
    @PostMapping("/classes/join")
    public String join(@Valid @ModelAttribute("form") JoinForm form,
                       BindingResult result,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model,
                       RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "student/join-class";
        }
        try {
            JoinResult outcome = joinClassService.join(form.code(), user.getId());
            return redirectAfterJoin(outcome, ra);
        } catch (InviteCodeValidationException ex) {
            result.rejectValue("code", "invite.invalid", ex.getMessage());
            return "student/join-class";
        }
    }

    /**
     * Leaves a class. Refusing to operate on classes the caller is
     * not actively enrolled in (404). COMPLETED classes raise 409 +
     * an error toast.
     */
    @PostMapping("/classes/{id}/leave")
    public String leave(@PathVariable Long id,
                        @AuthenticationPrincipal KshUserDetails user,
                        RedirectAttributes ra) {
        try {
            ClassEntity clazz = joinClassService.leave(id, user.getId());
            ra.addFlashAttribute("flashSuccess", "Đã rời lớp " + clazz.getName());
            return "redirect:/my/classes";
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("flashError", "Không thể rời lớp đã hoàn thành");
            return "redirect:/my/classes";
        }
    }

    private String redirectAfterJoin(JoinResult outcome, RedirectAttributes ra) {
        if (outcome instanceof Success s) {
            ra.addFlashAttribute("flashSuccess", "Đã tham gia lớp " + s.clazz().getName());
        } else if (outcome instanceof AlreadyJoined a) {
            ra.addFlashAttribute("flashInfo", "Bạn đã ở trong lớp " + a.clazz().getName());
        }
        return "redirect:/my/classes";
    }
}
