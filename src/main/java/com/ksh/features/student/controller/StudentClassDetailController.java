package com.ksh.features.student.controller;

import com.ksh.features.classes.dto.AnnouncementDtos.CommentFormError;
import com.ksh.features.classes.service.AnnouncementCommentService;
import com.ksh.features.classes.service.ClassAnnouncementService;
import com.ksh.features.student.service.StudentClassDetailService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.ksh.common.IConstant.ATTR_ANNOUNCEMENTS_PAGE;
import static com.ksh.common.IConstant.ATTR_COMMENT_FORM;
import static com.ksh.common.IConstant.ATTR_FLASH_SUCCESS;
import static com.ksh.common.IConstant.ATTR_VIEW;
import static com.ksh.common.IConstant.MSG_COMMENT_CREATED;
import static com.ksh.common.IConstant.MSG_COMMENT_DELETED;
import static com.ksh.common.IConstant.VIEW_STUDENT_CLASS_BOARD;

/** Student class overview pages sharing one flat class-detail shell. */
@Controller
@RequestMapping("/my/classes/{classId}")
@PreAuthorize(Roles.PREAUTH_STUDENT)
public class StudentClassDetailController {

    private final StudentClassDetailService detailService;
    private final ClassAnnouncementService announcementService;
    private final AnnouncementCommentService commentService;

    public StudentClassDetailController(StudentClassDetailService detailService,
                                        ClassAnnouncementService announcementService,
                                        AnnouncementCommentService commentService) {
        this.detailService = detailService;
        this.announcementService = announcementService;
        this.commentService = commentService;
    }

    @GetMapping
    public String root(@PathVariable Long classId) {
        return "redirect:/my/classes/" + classId + "/board";
    }

    /**
     * Renders the student class board with its paginated announcement feed.
     *
     * <p>Two enrollment checks run here by design: {@code detailService.get}
     * stays authoritative for the sidebar view record, while
     * {@code requireViewAccess} inside the announcement service is the gate the
     * feed carries on both board routes. Both throw
     * {@code EntityNotFoundException}, so the observable result is identical.
     */
    @GetMapping("/board")
    public String board(@PathVariable Long classId,
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @AuthenticationPrincipal KshUserDetails user,
                        Model model) {
        model.addAttribute(ATTR_VIEW, detailService.get(classId, user.getId()));
        model.addAttribute(ATTR_ANNOUNCEMENTS_PAGE,
                announcementService.listForClass(classId, user.getId(), user.getRole(), page));
        return VIEW_STUDENT_CLASS_BOARD;
    }

    /**
     * Posts a comment on an announcement from the student board.
     *
     * <p>Delegates to the same service method as the lecturer route, so the
     * authorization decision lives in one place; only the tier-1 role gate
     * differs by URL (design D5).
     */
    @PostMapping("/announcements/{announcementId}/comments")
    public String createComment(@PathVariable Long classId,
                                @PathVariable Long announcementId,
                                @RequestParam(name = "content", required = false) String content,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model,
                                RedirectAttributes ra) {
        try {
            commentService.create(classId, announcementId, content, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_COMMENT_CREATED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // Field validation renders inline beside the composer, not as a toast.
            return rerenderBoardWithCommentError(model, classId, announcementId, content,
                    user, ex.getMessage());
        }
        return "redirect:/my/classes/" + classId + "/board";
    }

    /** Soft-deletes a comment from the student board. */
    @PostMapping("/announcements/{announcementId}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long classId,
                                @PathVariable Long announcementId,
                                @PathVariable Long commentId,
                                @AuthenticationPrincipal KshUserDetails user,
                                RedirectAttributes ra) {
        try {
            commentService.delete(classId, announcementId, commentId,
                    user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_COMMENT_DELETED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
        return "redirect:/my/classes/" + classId + "/board";
    }

    /**
     * Re-renders the student board with an inline comment validation error,
     * repopulating the full board model exactly as {@link #board} does or
     * {@code ${announcementsPage}} throws at render time.
     */
    private String rerenderBoardWithCommentError(Model model, Long classId, Long announcementId,
                                                 String content, KshUserDetails user,
                                                 String message) {
        model.addAttribute(ATTR_VIEW, detailService.get(classId, user.getId()));
        model.addAttribute(ATTR_ANNOUNCEMENTS_PAGE,
                announcementService.listForClass(classId, user.getId(), user.getRole(), 0));
        model.addAttribute(ATTR_COMMENT_FORM, new CommentFormError(
                announcementId, content == null ? "" : content, message));
        return VIEW_STUDENT_CLASS_BOARD;
    }

    @GetMapping("/members")
    public String members(@PathVariable Long classId,
                          @AuthenticationPrincipal KshUserDetails user,
                          Model model) {
        model.addAttribute("view", detailService.get(classId, user.getId()));
        return "student/class-members";
    }
}