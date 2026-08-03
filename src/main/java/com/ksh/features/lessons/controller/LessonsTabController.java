package com.ksh.features.lessons.controller;

import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.classes.controller.support.ClassDetailModelSupport;
import com.ksh.features.student.dto.StudentLessonsDtos.ClassLessonsView;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonDetailView;
import com.ksh.features.student.dto.StudentLessonsDtos.SectionWithLessons;
import com.ksh.features.student.service.StudentLessonDetailService;
import com.ksh.features.student.service.StudentLessonsService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    private final StudentLessonsService studentLessonsService;
    private final StudentLessonDetailService studentLessonDetailService;
    private final ClassDetailModelSupport detailSupport;

    public LessonsTabController(ClassesService classesService,
                                StudentLessonsService studentLessonsService,
                                StudentLessonDetailService studentLessonDetailService,
                                ClassDetailModelSupport detailSupport) {
        this.classesService = classesService;
        this.studentLessonsService = studentLessonsService;
        this.studentLessonDetailService = studentLessonDetailService;
        this.detailSupport = detailSupport;
    }

    @GetMapping
    public String viewDistributedLessons(@PathVariable Long classId,
                                         @RequestParam(value = "section", required = false) Long sectionParam,
                                         @RequestParam(value = "lesson", required = false) Long lessonParam,
                                         @AuthenticationPrincipal KshUserDetails user,
                                         Model model) {
        var clazz = classesService.getViewable(classId, user.getId(), user.getRole());
        ClassLessonsView view = studentLessonsService
                .listClassLessons(classId, user.getId(), user.getRole());
        Long activeSectionId = resolveActiveSection(view, sectionParam);
        model.addAttribute("view", view);
        model.addAttribute("activeSectionId", activeSectionId);
        model.addAttribute("teachingView", true);
        model.addAttribute("classSharedDecks", java.util.List.of());
        model.addAttribute("lessonBasePath", "/lecturer/classes/" + classId + "/lessons");
        detailSupport.populateDetail(model, clazz, "lessons", user.getId(), user.getRole());

        if (lessonParam != null && activeSectionId != null
                && lessonBelongsToSection(view, activeSectionId, lessonParam)) {
            try {
                LessonDetailView detail = studentLessonDetailService
                        .getLessonDetail(classId, lessonParam, user.getId(), user.getRole());
                model.addAttribute("lessonDetail", detail);
            } catch (EntityNotFoundException ignored) {
                // Keep the class shell visible without leaking a foreign lesson.
            }
        }
        return "student/class-lessons";
    }

    private static Long resolveActiveSection(ClassLessonsView view, Long requested) {
        if (view.sections().isEmpty()) return null;
        return view.sections().stream().map(SectionWithLessons::sectionId)
                .filter(id -> id.equals(requested)).findFirst()
                .orElse(view.sections().get(0).sectionId());
    }

    private static boolean lessonBelongsToSection(ClassLessonsView view, Long sectionId, Long lessonId) {
        return view.sections().stream()
                .filter(section -> section.sectionId().equals(sectionId))
                .flatMap(section -> section.lessons().stream())
                .anyMatch(lesson -> lesson.id().equals(lessonId));
    }
}
