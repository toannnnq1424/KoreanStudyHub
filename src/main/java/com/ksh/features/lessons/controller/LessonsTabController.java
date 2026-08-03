package com.ksh.features.lessons.controller;

import com.ksh.features.classes.service.ClassesService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Compatibility entry point for the former class-scoped lesson authoring UI.
 *
 * <p>Lessons are authored only in Library. A lecturer opening the old class
 * tab is admitted through the normal class view gate and redirected to the
 * shared, read-only distributed-lessons surface.
 */
@Controller
@RequestMapping("/lecturer/classes/{classId}/lessons")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class LessonsTabController {

    private final ClassesService classesService;

    public LessonsTabController(ClassesService classesService) {
        this.classesService = classesService;
    }

    @GetMapping
    public String viewDistributedLessons(@PathVariable Long classId,
                                         @AuthenticationPrincipal KshUserDetails user) {
        classesService.getViewable(classId, user.getId(), user.getRole());
        return "redirect:/my/classes/" + classId + "/lessons";
    }
}
