package com.ksh.features.progress.controller;

import com.ksh.features.progress.service.LearningProgressService;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonEngagementTab;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonEngagementView;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.ksh.common.IConstant.ATTR_FLASH_SUCCESS;
import static com.ksh.common.IConstant.ATTR_FLASH_WARNING;
import static com.ksh.common.IConstant.MSG_PROGRESS_CHECKLIST_REQUIRED;
import static com.ksh.common.IConstant.MSG_PROGRESS_MARKED_COMPLETE;

/**
 * Handles the student lesson engagement checklist (KSH-4.5).
 *
 * <p>POST-Redirect-Get: the toggle reloads the lessons page so every
 * aggregate (section counts, class percent, rail badges) refreshes at once.
 * All authz gates live in {@link LearningProgressService}; any gate failure
 * raises {@code EntityNotFoundException} mapped to HTTP 404 by the global
 * handler, so the endpoint never leaks lesson existence.
 */
@Controller
@RequestMapping("/my/classes/{classId}/lessons/{lessonId}/progress")
@PreAuthorize(Roles.PREAUTH_STUDENT)
public class LearningProgressController {

    private final LearningProgressService progressService;

    public LearningProgressController(LearningProgressService progressService) {
        this.progressService = progressService;
    }

    /**
     * Reconciles the legacy completion request against persisted checklist
     * evidence, then redirects back to the inlined lesson.
     *
     * @param section the active section id, echoed so the redirect
     *                pre-selects the correct sidebar entry
     */
    @PostMapping("/toggle")
    public String toggle(@PathVariable Long classId,
                         @PathVariable Long lessonId,
                         @RequestParam("section") Long section,
                         @AuthenticationPrincipal KshUserDetails user,
                         RedirectAttributes ra) {
        boolean nowCompleted = progressService.toggleCompletion(classId, lessonId, user.getId());
        ra.addFlashAttribute(nowCompleted ? ATTR_FLASH_SUCCESS : ATTR_FLASH_WARNING,
                nowCompleted ? MSG_PROGRESS_MARKED_COMPLETE : MSG_PROGRESS_CHECKLIST_REQUIRED);
        return "redirect:" + lessonUrl(classId, section, lessonId);
    }

    /**
     * Persists one server-timed visibility heartbeat and returns the complete
     * checklist state. Invalid enum values are rejected by Spring as HTTP 400;
     * authentication, CSRF and ACTIVE-enrollment gates apply before mutation.
     */
    @PostMapping("/checkpoint")
    @ResponseBody
    public LessonEngagementView checkpoint(@PathVariable Long classId,
                                           @PathVariable Long lessonId,
                                           @RequestParam LessonEngagementTab tab,
                                           @RequestParam(defaultValue = "true") boolean active,
                                           @AuthenticationPrincipal KshUserDetails user) {
        return progressService.checkpointEngagement(
                classId, lessonId, user.getId(), tab, active);
    }

    /** Builds the canonical inlined-lesson URL; carries path/query variables. */
    private static String lessonUrl(Long classId, Long sectionId, Long lessonId) {
        return "/my/classes/" + classId + "/lessons"
                + "?section=" + sectionId + "&lesson=" + lessonId;
    }
}
