package com.ksh.features.classes.controller;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.Lesson;
import com.ksh.entities.Section;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.security.Role;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Integration tests for {@link ClassProgressController} (progress tab render + authz). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClassProgressControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClassRepository classRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private User lecturer;
    private ClassEntity clazz;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        clazz = saveClass("Progress tab class", lecturer.getId(), "PGTAB1");
        Section section = sectionRepository.saveAndFlush(
                new Section(clazz.getId(), "Chương 1", (short) 0, lecturer.getId()));
        Lesson pub = new Lesson(section.getId(), "Bài 1", (short) 0, lecturer.getId());
        pub.publish();
        lessonRepository.saveAndFlush(pub);
        User student = ensureUser("pgtab-student@ksh.edu.vn", "Pg Student", Role.STUDENT);
        enrollmentRepository.saveAndFlush(Enrollment.createFor(
                student, clazz.getId(), Enrollment.JoinedVia.CODE, null));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void owner_sees_progress_tab() throws Exception {
        mockMvc.perform(get(progressUrl()))
                .andExpect(status().isOk())
                .andExpect(view().name("classes/detail-progress"))
                .andExpect(model().attributeExists("progressSummary"))
                .andExpect(model().attributeExists("progressPage"))
                .andExpect(model().attribute("activeTab", "progress"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void filter_search_page_params_are_honored() throws Exception {
        mockMvc.perform(get(progressUrl())
                        .param("status", "in-progress")
                        .param("q", "pg")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("progressStatus", "in-progress"))
                .andExpect(model().attribute("progressQuery", "pg"))
                .andExpect(model().attribute("progressSize", 5));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void pager_links_preserve_filters_and_encode_query() throws Exception {
        // Seed 3 matching students so size=2 forces a second page (pager renders).
        // The query term carries '&' — it MUST be URL-encoded in every page link.
        String term = "aa&bb";
        for (int i = 1; i <= 3; i++) {
            User s = ensureUser("pgenc" + i + "@ksh.edu.vn", term + " Student " + i, Role.STUDENT);
            enrollmentRepository.saveAndFlush(Enrollment.createFor(
                    s, clazz.getId(), Enrollment.JoinedVia.CODE, null));
        }
        mockMvc.perform(get(progressUrl())
                        .param("status", "all")
                        .param("q", term)
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                // Shared fragment rendered with a second-page link that keeps the filters.
                .andExpect(content().string(allOf(
                        containsString("class=\"pager\""),
                        containsString("status=all"),
                        containsString("size=2"),
                        // '&' encoded to %26 — not left raw as an argument separator.
                        containsString("q=aa%26bb"))))
                .andExpect(content().string(not(containsString("q=aa&bb"))));
    }

    @Test
    void non_owner_lecturer_gets_403() throws Exception {
        User otherLecturer = ensureUser("lecturer-pgother@ksh.edu.vn", "Other Lec", Role.LECTURER);
        mockMvc.perform(get(progressUrl())
                        .with(user(new KshUserDetails(otherLecturer))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_is_denied_by_security() throws Exception {
        mockMvc.perform(get(progressUrl()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_redirects_to_login() throws Exception {
        mockMvc.perform(get(progressUrl()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    private String progressUrl() {
        return "/lecturer/classes/" + clazz.getId() + "/progress";
    }

    private ClassEntity saveClass(String name, Long lecturerId, String code) {
        ClassEntity entity = new ClassEntity(name, lecturerId, lecturerId,
                null, null, null, 100);
        entity.setCode(code);
        try {
            return classRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            entity.setCode(code + "x");
            return classRepository.saveAndFlush(entity);
        }
    }

    private User ensureUser(String email, String name, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = UserFactory.newAdminCreated(email,
                    "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                    name, role, true, null, null);
            return userRepository.saveAndFlush(u);
        });
    }
}
