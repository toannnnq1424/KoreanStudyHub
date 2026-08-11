package com.ksh.features.progress.controller;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.Lesson;
import com.ksh.entities.Section;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link LearningProgressController} (KSH-4.5): PRG redirect
 * with flash, 404 for outsiders, and CSRF enforcement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningProgressControllerTest {

    private static final String STUDENT_EMAIL = "student@ksh.edu.vn";
    private static final String OUTSIDER_EMAIL = "sv01@ksh.edu.vn";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private User lecturer;
    private User student;
    private ClassEntity clazz;
    private Section section;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        student = userRepository.findByEmailIgnoreCase(STUDENT_EMAIL).orElseThrow();
        clazz = saveClass("Progress controller class", "PRGCTL");
        section = sectionRepository.saveAndFlush(
                new Section(clazz.getId(), "Chương 1", (short) 0, lecturer.getId()));
        Lesson l = new Lesson(section.getId(), "Bài 1", (short) 0, lecturer.getId());
        l.updateContent("<p>Body</p>");
        l.publish();
        lesson = lessonRepository.saveAndFlush(l);
        enrollmentRepository.saveAndFlush(Enrollment.createFor(
                student, clazz.getId(), Enrollment.JoinedVia.REQUEST, null));
    }

    @Test
    @WithUserDetails(STUDENT_EMAIL)
    void legacy_toggle_redirects_with_checklist_warning_without_bypass() throws Exception {
        String expected = "/my/classes/" + clazz.getId() + "/lessons"
                + "?section=" + section.getId() + "&lesson=" + lesson.getId();

        mockMvc.perform(post(toggleUrl()).param("section", section.getId().toString()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expected))
                .andExpect(flash().attributeExists("flashWarning"));
    }

    @Test
    @WithUserDetails(STUDENT_EMAIL)
    void checkpoint_returns_applicability_and_persisted_server_state() throws Exception {
        mockMvc.perform(post(checkpointUrl())
                        .param("tab", "CONTENT")
                        .param("active", "true")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.applicable").value(true))
                .andExpect(jsonPath("$.content.seconds").value(0))
                .andExpect(jsonPath("$.content.requiredSeconds").value(60))
                .andExpect(jsonPath("$.video.applicable").value(false))
                .andExpect(jsonPath("$.video.satisfied").value(true))
                .andExpect(jsonPath("$.attachments.applicable").value(false))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.overallCompleted").value(false));
    }

    @Test
    @WithUserDetails(STUDENT_EMAIL)
    void checkpoint_rejects_unknown_tab_and_missing_csrf() throws Exception {
        mockMvc.perform(post(checkpointUrl()).param("tab", "SCORE").with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(checkpointUrl()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(checkpointUrl()).param("tab", "CONTENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void lecturer_cannot_mutate_student_checklist() throws Exception {
        mockMvc.perform(post(checkpointUrl()).param("tab", "CONTENT").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(OUTSIDER_EMAIL)
    void outsider_toggle_returns_404() throws Exception {
        mockMvc.perform(post(toggleUrl()).param("section", section.getId().toString()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails(STUDENT_EMAIL)
    void toggle_without_csrf_is_forbidden() throws Exception {
        mockMvc.perform(post(toggleUrl()).param("section", section.getId().toString()))
                .andExpect(status().isForbidden());
    }

    private String toggleUrl() {
        return "/my/classes/" + clazz.getId() + "/lessons/" + lesson.getId() + "/progress/toggle";
    }

    private String checkpointUrl() {
        return "/my/classes/" + clazz.getId() + "/lessons/" + lesson.getId()
                + "/progress/checkpoint";
    }

    private ClassEntity saveClass(String name, String code) {
        ClassEntity entity = new ClassEntity(name, lecturer.getId(), lecturer.getId(),
                null, null, null, 100);
        entity.setCode(code);
        try {
            return classRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            entity.setCode(code + "x");
            return classRepository.saveAndFlush(entity);
        }
    }
}
