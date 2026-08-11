package com.ksh.features.classes;

import com.ksh.entities.AnnouncementComment;
import com.ksh.entities.ClassAnnouncement;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.AnnouncementCommentRepository;
import com.ksh.features.classes.repository.ClassAnnouncementRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.notifications.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec scenario "Notification failure leaves the comment committed".
 *
 * <p>Lives in its own class because it replaces {@link NotificationService} with
 * a throwing stub for the whole Spring context. Doing that inside
 * {@code AnnouncementCommentIntegrationTest} would silently disarm every
 * notification assertion there — the self-comment and deep-link tests would keep
 * passing while proving nothing.
 *
 * <p>{@code AnnouncementCommentService.notifyAuthor} swallows the failure by
 * design, following {@code LessonsPublishService.fanOutLessonPublished}. Remove
 * that {@code try/catch} and this test fails on both assertions at once: the
 * request 500s and the comment row is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnnouncementCommentNotificationFailureTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClassRepository classRepository;
    @Autowired private ClassAnnouncementRepository announcementRepository;
    @Autowired private AnnouncementCommentRepository commentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    @MockitoBean private NotificationService notificationService;

    private User lecturer;
    private User student;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
        // Every create path through this context now fails at notification time.
        willThrow(new IllegalStateException("simulated notification outage"))
                .given(notificationService)
                .create(anyLong(), anyString(), anyString(),
                        anyString(), anyString(), anyLong());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void a_failing_notification_leaves_the_comment_committed_without_a_server_error()
            throws Exception {
        ClassEntity c = saveClass("Cmt-NotifFail", lecturer.getId(), "CMT25");
        // Authored by the lecturer, so the student's comment triggers a real
        // notification attempt rather than the self-comment skip.
        ClassAnnouncement a = announcementRepository.saveAndFlush(
                new ClassAnnouncement(c.getId(), "<p>Thông báo</p>", lecturer.getId()));
        enrollmentRepository.saveAndFlush(Enrollment.createFor(
                student, c.getId(), Enrollment.JoinedVia.REQUEST, null));
        long before = commentRepository.count();

        int status = mockMvc.perform(post("/my/classes/" + c.getId() + "/announcements/"
                        + a.getId() + "/comments").with(csrf())
                        .param("content", "Bình luận vẫn phải được lưu"))
                .andReturn().getResponse().getStatus();

        // The comment survives the notification outage.
        em.flush();
        assertThat(commentRepository.count())
                .as("a notification failure must not roll back the comment")
                .isEqualTo(before + 1);
        List<AnnouncementComment> rows =
                commentRepository.findForAnnouncementIds(List.of(a.getId()));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getContent()).isEqualTo("Bình luận vẫn phải được lưu");

        // And the request completes normally rather than 5xx.
        assertThat(status)
                .as("the request must not surface a server error")
                .isEqualTo(302);
    }

    /** Sanity check that the stub is actually armed for this context. */
    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void the_notification_stub_is_armed() throws Exception {
        ClassEntity c = saveClass("Cmt-StubArmed", lecturer.getId(), "CMT26");
        ClassAnnouncement a = announcementRepository.saveAndFlush(
                new ClassAnnouncement(c.getId(), "<p>Thông báo</p>", lecturer.getId()));
        enrollmentRepository.saveAndFlush(Enrollment.createFor(
                student, c.getId(), Enrollment.JoinedVia.REQUEST, null));

        mockMvc.perform(post("/my/classes/" + c.getId() + "/announcements/"
                        + a.getId() + "/comments").with(csrf())
                        .param("content", "Kích hoạt thông báo"))
                .andExpect(status().is3xxRedirection());

        org.mockito.Mockito.verify(notificationService)
                .create(anyLong(), anyString(), anyString(),
                        anyString(), anyString(), anyLong());
    }

    private ClassEntity saveClass(String name, Long lecturerId, String code) {
        ClassEntity e = new ClassEntity(name, lecturerId, lecturerId, null, null, null, 100);
        userRepository.findById(lecturerId).map(User::getSubjectId).ifPresent(e::setSubjectId);
        e.approve(lecturerId, java.time.LocalDateTime.now());
        e.setCode(code);
        return classRepository.saveAndFlush(e);
    }
}