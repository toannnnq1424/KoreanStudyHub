package com.ksh.features.messaging;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.messaging.service.MessagingService;
import com.ksh.security.Role;
import com.ksh.entities.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for direct messaging (Epic #13, KSH-8.3 + KSH-8.4).
 *
 * <p>Covers: the conversation list, the recipient gate at conversation start,
 * per-thread membership (404 no-leak for a foreign thread), the send flow (JSON
 * fetch path + non-JS PRG redirect), the unread badge count, and the
 * class-scoped page ({@code /my/classes/{classId}/messages}) that keeps the
 * class shell (enrolled → OK, outsider → 404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MessagingIntegrationTest {

    private static final String STUDENT = "student@ksh.edu.vn";
    private static final String LECTURER = "lecturer-msgtest@ksh.edu.vn";
    private static final String SEEDED_LECTURER = "lecturer@ksh.edu.vn";
    private static final String OUTSIDER = "sv02@ksh.edu.vn";
    private static final String ADMIN = "admin@ksh.edu.vn";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private MessagingService messagingService;

    private Long lecturerId;
    private Long studentId;
    private Long classId;
    private Long convId;

    @BeforeEach
    void setUp() {
        // A dedicated lecturer and class keep the class-scoped messaging checks
        // deterministic; global compose access no longer depends on enrollment.
        User lecturer = ensureLecturer();
        User student = userRepository.findByEmailIgnoreCase(STUDENT).orElseThrow();
        lecturerId = lecturer.getId();
        studentId = student.getId();

        ClassEntity clazz = saveClass(lecturer);
        classId = clazz.getId();
        enroll(student, clazz);

        // A pre-existing student↔lecturer thread.
        convId = messagingService.getOrCreateConversation(studentId, Role.STUDENT, lecturerId);
    }

    // ── Listing + thread access ─────────────────────────────────────────

    @Test
    @WithUserDetails(STUDENT)
    void list_renders_for_authenticated_user() throws Exception {
        mockMvc.perform(get("/my/messages"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cuộc trò chuyện")));
    }

    @Test
    @WithUserDetails(STUDENT)
    void participant_opens_own_thread() throws Exception {
        mockMvc.perform(get("/my/messages/" + convId))
                .andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(OUTSIDER)
    void non_participant_open_returns_404_no_leak() throws Exception {
        mockMvc.perform(get("/my/messages/" + convId))
                .andExpect(status().isNotFound());
    }

    // ── Recipient gate at conversation start ────────────────────────────

    @Test
    @WithUserDetails(STUDENT)
    void student_can_reach_lecturer_of_enrolled_class() throws Exception {
        mockMvc.perform(post("/my/messages/new").with(csrf())
                        .param("to", String.valueOf(lecturerId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/my/messages/*"));
    }

    @Test
    @WithUserDetails(OUTSIDER)
    void student_can_reach_lecturer_without_shared_class() throws Exception {
        mockMvc.perform(post("/my/messages/new").with(csrf())
                        .param("to", String.valueOf(lecturerId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/my/messages/*"));
    }

    @Test
    @WithUserDetails(STUDENT)
    void student_compose_lists_students_and_teachers_but_not_admins() throws Exception {
        mockMvc.perform(get("/my/messages/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("msg-grid has-open")))
                .andExpect(content().string(containsString(OUTSIDER)))
                .andExpect(content().string(containsString(LECTURER)))
                .andExpect(content().string(not(containsString(ADMIN))));
    }

    @Test
    @WithUserDetails(STUDENT)
    void student_cannot_start_admin_conversation() throws Exception {
        Long adminId = userRepository.findByEmailIgnoreCase(ADMIN).orElseThrow().getId();
        mockMvc.perform(post("/my/messages/new").with(csrf())
                        .param("to", String.valueOf(adminId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails(SEEDED_LECTURER)
    void staff_can_start_conversation_with_any_active_role() throws Exception {
        Long adminId = userRepository.findByEmailIgnoreCase(ADMIN).orElseThrow().getId();
        mockMvc.perform(post("/my/messages/new").with(csrf())
                        .param("to", String.valueOf(adminId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/my/messages/*"));
    }

    // ── Sending ─────────────────────────────────────────────────────────

    @Test
    @WithUserDetails(STUDENT)
    void send_via_fetch_returns_json() throws Exception {
        mockMvc.perform(post("/my/messages/" + convId).with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("body", "Xin chào thầy ạ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.body").value("Xin chào thầy ạ"));
    }

    @Test
    @WithUserDetails(STUDENT)
    void send_without_js_redirects_back_to_thread() throws Exception {
        mockMvc.perform(post("/my/messages/" + convId).with(csrf())
                        .param("body", "Gửi không JS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/my/messages/" + convId));
    }

    @Test
    @WithUserDetails(STUDENT)
    void send_blank_body_returns_400() throws Exception {
        mockMvc.perform(post("/my/messages/" + convId).with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("body", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails(OUTSIDER)
    void non_participant_send_returns_404_no_leak() throws Exception {
        mockMvc.perform(post("/my/messages/" + convId).with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("body", "Tin lạ"))
                .andExpect(status().isNotFound());
    }

    // ── Unread badge count ──────────────────────────────────────────────

    @Test
    @WithUserDetails(STUDENT)
    void unread_count_endpoint_returns_json() throws Exception {
        mockMvc.perform(get("/my/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());
    }

    // ── Class-scoped page (keeps the class shell/sidebar) ───────────────

    @Test
    @WithUserDetails(STUDENT)
    void class_scoped_page_shows_lecturer_thread_in_class_shell() throws Exception {
        mockMvc.perform(get("/my/classes/" + classId + "/messages"))
                .andExpect(status().isOk())
                // The shared class sidebar renders the class name.
                .andExpect(content().string(containsString("Tin nhắn")));
    }

    @Test
    @WithUserDetails(OUTSIDER)
    void class_scoped_page_for_non_enrolled_returns_404_no_leak() throws Exception {
        mockMvc.perform(get("/my/classes/" + classId + "/messages"))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** A lecturer created for this test only, so it teaches just the test class. */
    private User ensureLecturer() {
        return userRepository.findByEmailIgnoreCase(LECTURER).orElseGet(() -> {
            User u = UserFactory.newAdminCreated(LECTURER,
                    "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                    "Giảng viên Messaging Test", Role.LECTURER, true, null, null);
            return userRepository.saveAndFlush(u);
        });
    }

    private void enroll(User u, ClassEntity clazz) {
        enrollmentRepository.saveAndFlush(Enrollment.createFor(
                u, clazz.getId(), Enrollment.JoinedVia.REQUEST, null));
    }

    private ClassEntity saveClass(User lecturer) {
        ClassEntity entity = new ClassEntity("Messaging test class", lecturer.getId(),
                lecturer.getId(), null, null, null, 100);
        entity.setCode("MSGTST");
        try {
            return classRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            entity.setCode("MSGT" + (int) (Math.random() * 99));
            return classRepository.saveAndFlush(entity);
        }
    }
}
