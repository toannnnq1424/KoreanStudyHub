package com.ksh.features.classes.controller;

import com.ksh.entities.ClassEntity;
import com.ksh.features.classes.controller.support.ClassDetailModelSupport;
import com.ksh.features.classes.dto.AnnouncementDtos.CommentFormError;
import com.ksh.features.classes.dto.ClassesDtos.ClassForm;
import com.ksh.features.classes.service.AnnouncementCommentService;
import com.ksh.features.classes.service.ClassAnnouncementService;
import com.ksh.features.classes.service.ClassMembersService;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.classes.service.JoinClassService;
import com.ksh.features.storage.StorageNotConfiguredException;
import com.ksh.features.tests.service.LecturerExamService;
import com.ksh.features.upload.AnnouncementImageStorageService;
import com.ksh.security.Roles;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.ksh.common.IConstant.*;
import static com.ksh.features.classes.controller.support.ClassDetailModelSupport.classUrl;
import static com.ksh.features.classes.controller.support.ClassDetailModelSupport.labelFor;
import static com.ksh.features.lessons.controller.support.AjaxResponses.badRequest;
import static com.ksh.features.lessons.controller.support.AjaxResponses.internalError;
import static com.ksh.features.lessons.dto.SectionDtos.AjaxResult;

/**
 * Controller for the class detail screens (sidebar tabs introduced in Sprint 2 phase 2).
 * Split out of {@link ClassesController} during the file-size refactor so each controller
 * stays focused on a single responsibility.
 *
 * <p>Exposed endpoints:
 * <ul>
 *   <li>{@code GET  /lecturer/classes/{id}/board}    — announcement tab</li>
 *   <li>{@code GET  /lecturer/classes/{id}/members}  — member list tab</li>
 *   <li>{@code GET  /lecturer/classes/{id}/settings} — class settings</li>
 *   <li>{@code GET  /lecturer/classes/{id}/schedule|roles|groups|...} — placeholder tabs</li>
 * </ul>
 *
 * <p>Authorization: class-level {@code @PreAuthorize} blocks STUDENT and anonymous
 * users. Ownership checks are enforced at the service layer using the
 * {@code (userId, role)} pair from {@link KshUserDetails}.
 */
@Controller
@RequestMapping(BASE_LECTURER)
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class ClassDetailController {

    private static final Logger log = LoggerFactory.getLogger(ClassDetailController.class);

    private final ClassesService classesService;
    private final ClassMembersService classMembersService;
    private final ClassDetailModelSupport detailSupport;
    private final LecturerExamService examService;
    private final JoinClassService joinClassService;
    private final ClassAnnouncementService announcementService;
    private final AnnouncementCommentService commentService;
    private final AnnouncementImageStorageService announcementImageStorage;

    public ClassDetailController(ClassesService classesService,
                                 ClassMembersService classMembersService,
                                 ClassDetailModelSupport detailSupport,
                                 LecturerExamService examService,
                                 JoinClassService joinClassService,
                                 ClassAnnouncementService announcementService,
                                 AnnouncementCommentService commentService,
                                 AnnouncementImageStorageService announcementImageStorage) {
        this.classesService = classesService;
        this.classMembersService = classMembersService;
        this.detailSupport = detailSupport;
        this.examService = examService;
        this.joinClassService = joinClassService;
        this.announcementService = announcementService;
        this.commentService = commentService;
        this.announcementImageStorage = announcementImageStorage;
    }

    /**
     * Renders the class board (announcement) tab with its paginated feed.
     *
     * <p>Reads gate through {@code ClassAccessPolicy.requireViewAccess} rather
     * than {@code classesService.getViewable}, so an unrelated lecturer now
     * receives 404 instead of 403 — a deliberate change that stops disclosing
     * class existence (design D3).
     */
    @GetMapping("/classes/{id}/board")
    public String detailBoard(@PathVariable Long id,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @AuthenticationPrincipal KshUserDetails user,
                              Model model) {
        populateBoard(model, id, page, user);
        // Preserve flashed form values from a prior failed POST.
        if (!model.containsAttribute(ATTR_ANNOUNCEMENT_FORM)) {
            model.addAttribute(ATTR_ANNOUNCEMENT_FORM, "");
        }
        return VIEW_CLASS_DETAIL_BOARD;
    }

    /** Publishes a new announcement to the class board. */
    @PostMapping("/classes/{id}/announcements")
    public String createAnnouncement(@PathVariable Long id,
                                     @RequestParam(name = "content", required = false) String content,
                                     @AuthenticationPrincipal KshUserDetails user,
                                     Model model,
                                     RedirectAttributes ra) {
        try {
            announcementService.create(id, content, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_ANNOUNCEMENT_CREATED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // Validation failure re-renders the board inline, so the full board
            // model must be rebuilt or ${announcementsPage} throws at render time.
            return rerenderBoardWithError(model, id, content, user, ex.getMessage());
        }
        return "redirect:" + classUrl(id) + "/" + TAB_BOARD;
    }

    /** Replaces the content of an existing announcement. */
    @PostMapping("/classes/{id}/announcements/{announcementId}")
    public String updateAnnouncement(@PathVariable Long id,
                                     @PathVariable Long announcementId,
                                     @RequestParam(name = "content", required = false) String content,
                                     @AuthenticationPrincipal KshUserDetails user,
                                     Model model,
                                     RedirectAttributes ra) {
        try {
            announcementService.update(id, announcementId, content, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_ANNOUNCEMENT_UPDATED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return rerenderBoardWithError(model, id, content, user, ex.getMessage());
        }
        return "redirect:" + classUrl(id) + "/" + TAB_BOARD;
    }

    /**
     * Soft-deletes an announcement. POST rather than DELETE because HTML forms
     * cannot issue DELETE; the verb suffix matches the existing
     * {@code /members/{userId}/approve} precedent in this controller.
     */
    @PostMapping("/classes/{id}/announcements/{announcementId}/delete")
    public String deleteAnnouncement(@PathVariable Long id,
                                     @PathVariable Long announcementId,
                                     @AuthenticationPrincipal KshUserDetails user,
                                     RedirectAttributes ra) {
        try {
            announcementService.delete(id, announcementId, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_ANNOUNCEMENT_DELETED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
        return "redirect:" + classUrl(id) + "/" + TAB_BOARD;
    }

    /**
     * Uploads an image for embedding in an announcement body (Quill toolbar and
     * paste). Returns {@code { url: "/uploads/announcements/staged-..." }} inside
     * the AjaxResult data, so the editor helper can read {@code res.data.url}.
     *
     * <p>Storage failures return <b>400, not 500</b>, so the Vietnamese reason
     * reaches the author and the fault is diagnosable — matching
     * {@code LecturerTestController.uploadImage}. The class-level
     * {@code @PreAuthorize} already blocks students, so no method-level
     * annotation is added.
     */
    @PostMapping(value = "/classes/{id}/announcements/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> uploadAnnouncementImage(@PathVariable Long id,
                                                     @RequestParam("file") MultipartFile file,
                                                     @AuthenticationPrincipal KshUserDetails user) {
        try {
            String url = announcementImageStorage.store(user.getId(), file);
            return ResponseEntity.ok(AjaxResult.success(Map.of("url", url)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (StorageNotConfiguredException ex) {
            return badRequest(MSG_STORAGE_R2_NOT_CONFIGURED);
        } catch (IOException ex) {
            log.error("Failed to upload announcement image for class {}", id, ex);
            return badRequest(MSG_STORAGE_UPLOAD_FAILED);
        } catch (Exception ex) {
            log.error("Failed to upload announcement image for class {}", id, ex);
            return internalError();
        }
    }

    /** Posts a comment on an announcement from the lecturer board. */
    @PostMapping("/classes/{id}/announcements/{announcementId}/comments")
    public String createComment(@PathVariable Long id,
                                @PathVariable Long announcementId,
                                @RequestParam(name = "content", required = false) String content,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model,
                                RedirectAttributes ra) {
        try {
            commentService.create(id, announcementId, content, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_COMMENT_CREATED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // Field validation renders inline beside the composer, not as a toast.
            return rerenderBoardWithCommentError(model, id, announcementId, content,
                    user, ex.getMessage());
        }
        return "redirect:" + classUrl(id) + "/" + TAB_BOARD;
    }

    /** Soft-deletes a comment from the lecturer board. */
    @PostMapping("/classes/{id}/announcements/{announcementId}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long id,
                                @PathVariable Long announcementId,
                                @PathVariable Long commentId,
                                @AuthenticationPrincipal KshUserDetails user,
                                RedirectAttributes ra) {
        try {
            commentService.delete(id, announcementId, commentId, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_COMMENT_DELETED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
        return "redirect:" + classUrl(id) + "/" + TAB_BOARD;
    }

    /** Renders the class members tab with ACTIVE members and PENDING requests. */
    @GetMapping("/classes/{id}/members")
    public String detailMembers(@PathVariable Long id,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model) {
        ClassMembersService.ClassMembersView view =
                classMembersService.listForClass(id, user.getId(), user.getRole());
        detailSupport.populateDetail(model, view.clazz(), TAB_MEMBERS,
                user.getId(), user.getRole());
        model.addAttribute(ATTR_MEMBERS, view.members());
        model.addAttribute(ATTR_MEMBER_TOTAL, view.total());
        model.addAttribute(ATTR_PENDING_MEMBERS, view.pendingMembers());
        model.addAttribute(ATTR_PENDING_TOTAL, view.pendingTotal());
        return VIEW_CLASS_DETAIL_MEMBERS;
    }

    /** Owner approves a PENDING join request. */
    @PostMapping("/classes/{id}/members/{userId}/approve")
    public String approveMember(@PathVariable Long id,
                                @PathVariable Long userId,
                                @AuthenticationPrincipal KshUserDetails user,
                                RedirectAttributes ra) {
        try {
            joinClassService.approve(id, userId, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_JOIN_APPROVED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_JOIN_APPROVE_FAILED + ex.getMessage());
        }
        return "redirect:" + classUrl(id) + "/" + TAB_MEMBERS;
    }

    /** Owner rejects a PENDING join request. */
    @PostMapping("/classes/{id}/members/{userId}/reject")
    public String rejectMember(@PathVariable Long id,
                               @PathVariable Long userId,
                               @AuthenticationPrincipal KshUserDetails user,
                               RedirectAttributes ra) {
        try {
            joinClassService.reject(id, userId, user.getId(), user.getRole());
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_JOIN_REJECTED);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_JOIN_REJECT_FAILED + ex.getMessage());
        }
        return "redirect:" + classUrl(id) + "/" + TAB_MEMBERS;
    }

    /** Renders the class exams tab — the exams belonging to this class. */
    @GetMapping("/classes/{id}/tests")
    public String detailTests(@PathVariable Long id,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @AuthenticationPrincipal KshUserDetails user,
                              Model model) {
        ClassEntity clazz = classesService.getViewable(id, user.getId(), user.getRole());
        detailSupport.populateDetail(model, clazz, TAB_TESTS, user.getId(), user.getRole());
        model.addAttribute(ATTR_EXAMS_PAGE,
                examService.listForClass(id, user.getId(), user.getRole(), page));
        return VIEW_CLASS_DETAIL_TESTS;
    }

    /**
     * Renders a placeholder view for class detail tabs not yet implemented (Sprint 3–5).
     * Handles: {@code /schedule}, {@code /roles}, {@code /groups}, {@code /assignments},
     * {@code /scores}, {@code /materials}.
     *
     * <p>Note: {@code /lessons} and {@code /assignments} are intentionally NOT mapped here —
     * they are owned by {@code SectionsController} and {@code LecturerAssignmentController}
     * respectively. Adding both mappings would raise
     * {@code IllegalStateException: Ambiguous mapping} at startup.
     */
    @GetMapping({"/classes/{id}/schedule", "/classes/{id}/roles",
            "/classes/{id}/groups",
            "/classes/{id}/materials"})
    public String detailPlaceholder(@PathVariable Long id,
                                    @AuthenticationPrincipal KshUserDetails user,
                                    jakarta.servlet.http.HttpServletRequest request,
                                    Model model) {
        ClassEntity clazz = classesService.getViewable(id, user.getId(), user.getRole());
        // Extract the last URL segment as the active tab key (e.g. "schedule").
        String path = request.getRequestURI();
        String tab = path.substring(path.lastIndexOf('/') + 1);
        detailSupport.populateDetail(model, clazz, tab, user.getId(), user.getRole());
        model.addAttribute(ATTR_PLACEHOLDER_TAB, tab);
        model.addAttribute(ATTR_PLACEHOLDER_LABEL, labelFor(tab));
        return VIEW_CLASS_DETAIL_PLACEHOLDER;
    }

    /**
     * Renders the class settings tab, reusing the edit-class form.
     * Only the immutable class owner (or ADMIN) may access this endpoint.
     *
     * <p>Invite settings were retired; this route only renders class info.
     */
    @GetMapping("/classes/{id}/settings")
    public String detailSettings(@PathVariable Long id,
                                 @RequestParam(defaultValue = SUBTAB_INFO) String tab,
                                 @AuthenticationPrincipal KshUserDetails user,
                                 Model model) {
        ClassEntity entity = classesService.getOwnerManaged(id, user.getId(), user.getRole());
        // Preserve flashed form values from a prior failed POST.
        if (!model.containsAttribute(ATTR_FORM)) {
            model.addAttribute(ATTR_FORM, ClassForm.fromEntity(entity));
        }
        detailSupport.populateDetail(model, entity, TAB_SETTINGS, user.getId(), user.getRole());

        model.addAttribute(ATTR_ACTIVE_DETAIL_TAB, SUBTAB_INFO);

        model.addAttribute(ATTR_MODE, MODE_EDIT);
        model.addAttribute(ATTR_FORM_ACTION, classUrl(id));
        model.addAttribute(ATTR_CLASS_ID, id);
        return VIEW_CLASS_DETAIL_SETTINGS;
    }

    // ── Internal helpers ───────────────────────────────────────────────

    /**
     * Loads everything the board view needs: the sidebar model, the paginated
     * announcement feed, and the pager parameters. Shared by the GET route and
     * the POST validation-failure path so both render an identical page.
     */
    private void populateBoard(Model model, Long id, int page, KshUserDetails user) {
        ClassEntity clazz = announcementService.getViewableForBoard(id, user.getId(), user.getRole());
        detailSupport.populateDetail(model, clazz, TAB_BOARD, user.getId(), user.getRole());
        model.addAttribute(ATTR_ANNOUNCEMENTS_PAGE,
                announcementService.listForClass(id, user.getId(), user.getRole(), page));
        model.addAttribute(ATTR_PAGER_PARAMS, new LinkedHashMap<String, Object>());
    }

    /**
     * Re-renders the board with an inline validation error, preserving the
     * rejected input so the author does not lose their draft.
     */
    private String rerenderBoardWithError(Model model, Long id, String content,
                                          KshUserDetails user, String message) {
        populateBoard(model, id, 0, user);
        model.addAttribute(ATTR_ANNOUNCEMENT_FORM, content == null ? "" : content);
        model.addAttribute(ATTR_FLASH_ERROR, message);
        return VIEW_CLASS_DETAIL_BOARD;
    }

    /**
     * Re-renders the board with an inline comment validation error.
     *
     * <p>The rejected text is preserved and tagged with its announcement id so
     * the template knows which of several composers on the page to re-open and
     * mark. The full board model is rebuilt first, or {@code ${announcementsPage}}
     * throws at render time.
     */
    private String rerenderBoardWithCommentError(Model model, Long id, Long announcementId,
                                                 String content, KshUserDetails user,
                                                 String message) {
        populateBoard(model, id, 0, user);
        if (!model.containsAttribute(ATTR_ANNOUNCEMENT_FORM)) {
            model.addAttribute(ATTR_ANNOUNCEMENT_FORM, "");
        }
        model.addAttribute(ATTR_COMMENT_FORM, new CommentFormError(
                announcementId, content == null ? "" : content, message));
        return VIEW_CLASS_DETAIL_BOARD;
    }
}