package com.ksh.features.classes;

import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassAnnouncement;
import com.ksh.entities.ClassCoLecturer;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassActivityRepository;
import com.ksh.features.classes.repository.ClassAnnouncementRepository;
import com.ksh.features.classes.repository.ClassCoLecturerRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.mail.outbox.MailOutboxRepository;
import com.ksh.features.notifications.entity.Notification;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.repository.NotificationRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ksh.common.IConstant.DEFAULT_ANNOUNCEMENT_PAGE_SIZE;
import static com.ksh.common.IConstant.MAX_ANNOUNCEMENT_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for the class Board announcement feature
 * ({@code class-announcement-board}).
 *
 * <p>Write-path assertions count DB rows rather than reading HTTP status, because
 * a rejected POST still returns 200 when the board re-renders with an inline
 * error — only a row count distinguishes "blocked" from "written".
 *
 * <p>Seed users come from {@code V5__seed_test_users.sql} /
 * {@code V48__standardize_subject_leader_role.sql}: lecturer@ksh.edu.vn,
 * leader@ksh.edu.vn, admin@ksh.edu.vn, student@ksh.edu.vn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClassAnnouncementIntegrationTest {

    private static final String CONTENT = "content";
    private static final String BODY = "<p>Nội dung thông báo</p>";

    @Autowired private MockMvc mockMvc;
    @Autowired private ClassRepository classRepository;
    @Autowired private ClassAnnouncementRepository announcementRepository;
    @Autowired private ClassActivityRepository activityRepository;
    @Autowired private ClassCoLecturerRepository coLecturerRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private MailOutboxRepository mailOutboxRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    private User lecturer;
    private User leader;
    private User admin;
    private User student;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        leader = userRepository.findByEmailIgnoreCase("leader@ksh.edu.vn").orElseThrow();
        admin = userRepository.findByEmailIgnoreCase("admin@ksh.edu.vn").orElseThrow();
        student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
    }

    // ───────────────────── 7.2 Write happy paths ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void owner_publishes_announcement_and_writes_one_audit_row() throws Exception {
        ClassEntity c = saveClass("Ann-Own", lecturer.getId(), "ANN01");
        long before = announcementRepository.count();
        long activityBefore = activityRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, BODY))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/lecturer/classes/" + c.getId() + "/board"));

        assertThat(announcementRepository.count()).isEqualTo(before + 1);
        assertThat(activityRepository.count()).isEqualTo(activityBefore + 1);

        ClassAnnouncement saved = onlyAnnouncementOf(c);
        assertThat(saved.getClassId()).isEqualTo(c.getId());
        assertThat(saved.getCreatedBy()).isEqualTo(lecturer.getId());
        assertThat(saved.isDeleted()).isFalse();
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void co_lecturer_publishes_announcement() throws Exception {
        // Class owned by someone else; the acting lecturer is only a co-lecturer.
        User owner = ensureUser("ann-owner@ksh.edu.vn", "Ann Owner", Role.LECTURER);
        ClassEntity c = saveClass("Ann-Co", owner.getId(), "ANN02");
        coLecturerRepository.saveAndFlush(
                new ClassCoLecturer(c.getId(), lecturer.getId(), owner.getId()));
        long before = announcementRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, BODY))
                .andExpect(status().is3xxRedirection());

        assertThat(announcementRepository.count()).isEqualTo(before + 1);
        assertThat(onlyAnnouncementOf(c).getCreatedBy()).isEqualTo(lecturer.getId());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void edit_replaces_content_without_adding_a_row_and_writes_one_audit_row() throws Exception {
        ClassEntity c = saveClass("Ann-Edit", lecturer.getId(), "ANN03");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Bản gốc</p>");
        long before = announcementRepository.count();
        long activityBefore = activityRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId()
                        + "/announcements/" + a.getId()).with(csrf())
                        .param(CONTENT, "<p>Bản đã sửa</p>"))
                .andExpect(status().is3xxRedirection());

        assertThat(announcementRepository.count()).isEqualTo(before);
        assertThat(reload(a).getContent()).contains("Bản đã sửa").doesNotContain("Bản gốc");

        // Exactly one audit row, and it must carry the UPDATED type — a count-only
        // assertion would still pass if the wrong constant were written.
        assertThat(activityRepository.count()).isEqualTo(activityBefore + 1);
        assertThat(onlyActivityOf(c).getType())
                .isEqualTo(ClassActivity.TYPE_ANNOUNCEMENT_UPDATED);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void delete_retains_row_as_soft_deleted_and_hides_it_from_both_feeds() throws Exception {
        ClassEntity c = saveClass("Ann-Del", lecturer.getId(), "ANN04");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Sắp bị xoá</p>");
        enrollStudent(c);
        long activityBefore = activityRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId()
                        + "/announcements/" + a.getId() + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Exactly one audit row, and it must carry the DELETED type — a count-only
        // assertion would still pass if the wrong constant were written.
        assertThat(activityRepository.count()).isEqualTo(activityBefore + 1);
        assertThat(onlyActivityOf(c).getType())
                .isEqualTo(ClassActivity.TYPE_ANNOUNCEMENT_DELETED);

        // The row survives with is_deleted = 1; @SQLRestriction hides it from JPA,
        // so assert the flag through a native query against the real table.
        // The driver maps TINYINT(1) to Boolean, so normalise before asserting.
        Object flag = em.createNativeQuery(
                        "SELECT is_deleted FROM class_announcements WHERE id = :id")
                .setParameter("id", a.getId())
                .getSingleResult();
        int deletedFlag = (flag instanceof Boolean b) ? (b ? 1 : 0) : ((Number) flag).intValue();
        assertThat(deletedFlag).isEqualTo(1);

        // Clear the first-level cache: @SQLRestriction filters SQL, not instances
        // already managed by this transaction's persistence context.
        em.clear();
        assertThat(announcementRepository.findById(a.getId())).isEmpty();

        mockMvc.perform(get("/lecturer/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Sắp bị xoá"))));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void deleted_announcement_is_absent_from_the_student_feed() throws Exception {
        ClassEntity c = saveClass("Ann-DelStu", lecturer.getId(), "ANN05");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Ẩn khỏi học viên</p>");
        enrollStudent(c);
        a.softDelete();
        announcementRepository.saveAndFlush(a);

        mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Ẩn khỏi học viên"))));
    }

    // ───────────────────── 7.3 Rejection paths ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void blank_content_rerenders_with_inline_error_and_writes_no_row() throws Exception {
        ClassEntity c = saveClass("Ann-Blank", lecturer.getId(), "ANN06");
        long before = announcementRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không được để trống")));

        assertThat(announcementRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void whitespace_only_content_is_rejected() throws Exception {
        ClassEntity c = saveClass("Ann-Ws", lecturer.getId(), "ANN07");
        long before = announcementRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, "   <p> </p>  "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không được để trống")));

        assertThat(announcementRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void script_only_content_is_rejected_as_empty() throws Exception {
        ClassEntity c = saveClass("Ann-Script", lecturer.getId(), "ANN08");
        long before = announcementRepository.count();

        // Non-empty before sanitization, empty after — must be rejected (design D9).
        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không được để trống")));

        assertThat(announcementRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void content_over_the_length_cap_is_rejected() throws Exception {
        ClassEntity c = saveClass("Ann-Long", lecturer.getId(), "ANN09");
        long before = announcementRepository.count();
        String tooLong = "<p>" + "a".repeat(MAX_ANNOUNCEMENT_LENGTH + 100) + "</p>";

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, tooLong))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("quá dài")));

        assertThat(announcementRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void unrelated_lecturer_post_is_forbidden_and_writes_nothing() throws Exception {
        ClassEntity c = saveClass("Ann-Other", leader.getId(), "ANN10");
        long before = announcementRepository.count();
        long activityBefore = activityRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, BODY))
                .andExpect(status().isForbidden());

        assertThat(announcementRepository.count()).isEqualTo(before);
        assertThat(activityRepository.count()).isEqualTo(activityBefore);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_cannot_reach_the_publish_endpoint() throws Exception {
        ClassEntity c = saveClass("Ann-Stu", lecturer.getId(), "ANN11");
        long before = announcementRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, BODY))
                .andExpect(status().isForbidden());

        assertThat(announcementRepository.count()).isEqualTo(before);
    }

    /**
     * Spec scenario "Students still cannot edit or delete an announcement". The
     * comment feature is the first write path a {@code ROLE_STUDENT} caller may
     * execute on class-owned content; that relaxation must stay confined to the
     * comment sub-resource.
     *
     * <p>The student is <b>actively enrolled and has commented</b>, exactly as
     * the spec words it — so nothing about membership can be what rejects, only
     * the announcement write gate. Both assertions read persisted state rather
     * than status: per {@code .claude/rules/authorization-check.md} a POST can
     * return 200 while writing nothing and 403 while having written, so the
     * content string and the row count carry the security claim and the status
     * is checked afterwards.
     */
    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void enrolled_commenting_student_cannot_edit_an_announcement() throws Exception {
        ClassEntity c = saveClass("Ann-StuEdit", lecturer.getId(), "ANN23");
        enrollStudent(c);
        ClassAnnouncement a = seedAnnouncement(c, "<p>Nội dung gốc</p>");
        // Prove the student really can write comments, isolating the denial to
        // the announcement gate rather than to class membership.
        mockMvc.perform(post("/my/classes/" + c.getId() + "/announcements/"
                        + a.getId() + "/comments").with(csrf())
                        .param(CONTENT, "Học viên bình luận được"))
                .andExpect(status().is3xxRedirection());

        int status = mockMvc.perform(post("/lecturer/classes/" + c.getId()
                        + "/announcements/" + a.getId()).with(csrf())
                        .param(CONTENT, "<p>Học viên sửa trộm</p>"))
                .andReturn().getResponse().getStatus();

        // State first: an unchanged body is what proves the write was blocked.
        assertThat(reload(a).getContent())
                .as("a student must not be able to rewrite an announcement body")
                .contains("Nội dung gốc")
                .doesNotContain("Học viên sửa trộm");
        assertThat(status).isEqualTo(403);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void enrolled_commenting_student_cannot_delete_an_announcement() throws Exception {
        ClassEntity c = saveClass("Ann-StuDel", lecturer.getId(), "ANN24");
        enrollStudent(c);
        ClassAnnouncement a = seedAnnouncement(c, "<p>Không được xoá</p>");
        mockMvc.perform(post("/my/classes/" + c.getId() + "/announcements/"
                        + a.getId() + "/comments").with(csrf())
                        .param(CONTENT, "Học viên bình luận được"))
                .andExpect(status().is3xxRedirection());
        long before = announcementRepository.count();

        int status = mockMvc.perform(post("/lecturer/classes/" + c.getId()
                        + "/announcements/" + a.getId() + "/delete").with(csrf()))
                .andReturn().getResponse().getStatus();

        // A soft delete keeps the row but hides it, so counting rows alone is not
        // enough: the announcement must still be READABLE afterwards.
        em.flush();
        em.clear();
        assertThat(announcementRepository.findById(a.getId()))
                .as("a student must not be able to soft-delete an announcement")
                .isPresent();
        assertThat(announcementRepository.count()).isEqualTo(before);
        assertThat(status).isEqualTo(403);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void editing_through_the_wrong_class_id_returns_404_and_leaves_content_intact()
            throws Exception {
        // Both classes are editable by the caller, so getEditable passes and the
        // ownership check is what rejects — 404, not 403 (anti-enumeration).
        ClassEntity classC = saveClass("Ann-C", lecturer.getId(), "ANN12");
        ClassEntity classD = saveClass("Ann-D", lecturer.getId(), "ANN13");
        ClassAnnouncement inD = seedAnnouncement(classD, "<p>Thuộc lớp D</p>");

        mockMvc.perform(post("/lecturer/classes/" + classC.getId()
                        + "/announcements/" + inD.getId()).with(csrf())
                        .param(CONTENT, "<p>Sửa trộm</p>"))
                .andExpect(status().isNotFound());

        assertThat(reload(inD).getContent()).contains("Thuộc lớp D").doesNotContain("Sửa trộm");
    }

    // ───────────────────── 7.3b Pagination ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void feed_pages_at_the_configured_size_without_duplicates_across_the_boundary()
            throws Exception {
        ClassEntity c = saveClass("Ann-Page", lecturer.getId(), "ANN14");
        int total = DEFAULT_ANNOUNCEMENT_PAGE_SIZE + 1;
        for (int i = 0; i < total; i++) {
            seedAnnouncement(c, "<p>Thông báo số " + i + "</p>");
        }

        String first = mockMvc.perform(get("/lecturer/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/lecturer/classes/" + c.getId() + "/board")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(countFeedItems(first)).isEqualTo(DEFAULT_ANNOUNCEMENT_PAGE_SIZE);
        assertThat(countFeedItems(second)).isEqualTo(total - DEFAULT_ANNOUNCEMENT_PAGE_SIZE);

        // No announcement may appear on both pages, and none may be dropped.
        for (int i = 0; i < total; i++) {
            String marker = "Thông báo số " + i + "<";
            boolean onFirst = first.contains(marker);
            boolean onSecond = second.contains(marker);
            assertThat(onFirst && onSecond)
                    .as("announcement %d duplicated across the page boundary", i).isFalse();
            assertThat(onFirst || onSecond)
                    .as("announcement %d missing from both pages", i).isTrue();
        }
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void negative_page_parameter_clamps_to_the_first_page() throws Exception {
        ClassEntity c = saveClass("Ann-Neg", lecturer.getId(), "ANN15");
        seedAnnouncement(c, "<p>Trang đầu tiên</p>");

        mockMvc.perform(get("/lecturer/classes/" + c.getId() + "/board")
                        .param("page", "-5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trang đầu tiên")));
    }

    // ───────────────────── 7.4 Read gating ─────────────────────

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_route_returns_404_for_a_class_the_student_is_not_enrolled_in() throws Exception {
        ClassEntity c = saveClass("Ann-NoEnrol", lecturer.getId(), "ANN16");

        mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_route_returns_the_same_404_for_a_nonexistent_class() throws Exception {
        ClassEntity c = saveClass("Ann-Indist", lecturer.getId(), "ANN17");
        long missingId = 99_000_000L;

        int notEnrolled = mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andReturn().getResponse().getStatus();
        int nonexistent = mockMvc.perform(get("/my/classes/" + missingId + "/board"))
                .andReturn().getResponse().getStatus();

        // Indistinguishable: class existence must not leak on /my/classes/{id}/board.
        assertThat(nonexistent).isEqualTo(404);
        assertThat(nonexistent).isEqualTo(notEnrolled);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void enrolled_student_reads_the_feed_on_the_student_route() throws Exception {
        ClassEntity c = saveClass("Ann-Enrol", lecturer.getId(), "ANN18");
        enrollStudent(c);
        seedAnnouncement(c, "<p>Học viên đọc được</p>");

        mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Học viên đọc được")));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void admin_reads_any_board_on_the_lecturer_route() throws Exception {
        ClassEntity c = saveClass("Ann-Admin", lecturer.getId(), "ANN19");
        seedAnnouncement(c, "<p>Admin xem được</p>");

        mockMvc.perform(get("/lecturer/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin xem được")));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void student_route_stays_student_only_at_the_role_gate() throws Exception {
        ClassEntity c = saveClass("Ann-AdmStu", lecturer.getId(), "ANN20");

        // Pre-existing PREAUTH_STUDENT gate on /my/classes/**; must not widen.
        mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andExpect(status().isForbidden());
    }

    // ───────────────────── 7.5 Notifications ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void publishing_notifies_only_actively_enrolled_students_and_queues_no_mail()
            throws Exception {
        ClassEntity c = saveClass("Ann-Notify", lecturer.getId(), "ANN21");
        // Two ACTIVE students and one non-ACTIVE (PENDING) student.
        User s1 = ensureUser("ann-s1@ksh.edu.vn", "Ann S1", Role.STUDENT);
        User s2 = ensureUser("ann-s2@ksh.edu.vn", "Ann S2", Role.STUDENT);
        User s3 = ensureUser("ann-s3@ksh.edu.vn", "Ann S3", Role.STUDENT);
        enrollActive(s1, c);
        enrollActive(s2, c);
        enrollPending(s3, c);

        long notifBefore = notificationRepository.count();
        long mailBefore = mailOutboxRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, BODY))
                .andExpect(status().is3xxRedirection());
        em.flush();

        List<Notification> created = announcementNotificationsFor(c);
        assertThat(created).hasSize(2);
        assertThat(notificationRepository.count()).isEqualTo(notifBefore + 2);
        assertThat(created).extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(s1.getId(), s2.getId());
        for (Notification n : created) {
            assertThat(n.getReferenceType()).isEqualTo(NotificationType.REF_CLASS);
            // referenceId is the CLASS id — the deep-link interpolates it as one.
            assertThat(n.getReferenceId()).isEqualTo(c.getId());
        }

        // CLASS_ANNOUNCEMENT is outside EMAIL_TYPES, so nothing is queued.
        assertThat(mailOutboxRepository.count()).isEqualTo(mailBefore);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void editing_an_announcement_creates_no_additional_notifications() throws Exception {
        ClassEntity c = saveClass("Ann-NoRenotify", lecturer.getId(), "ANN22");
        User s1 = ensureUser("ann-s4@ksh.edu.vn", "Ann S4", Role.STUDENT);
        enrollActive(s1, c);
        ClassAnnouncement a = seedAnnouncement(c, "<p>Trước khi sửa</p>");
        long before = notificationRepository.count();

        mockMvc.perform(post("/lecturer/classes/" + c.getId()
                        + "/announcements/" + a.getId()).with(csrf())
                        .param(CONTENT, "<p>Sau khi sửa</p>"))
                .andExpect(status().is3xxRedirection());
        em.flush();

        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    // ───────────────────── 7.6 Sanitization ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void dangerous_markup_is_stripped_before_persistence() throws Exception {
        ClassEntity c = saveClass("Ann-Sanitize", lecturer.getId(), "ANN23");
        String dirty = "<p><strong>Giữ lại</strong></p>"
                + "<script>alert('xss')</script>"
                + "<p onclick=\"steal()\">Có onclick</p>"
                + "<p style=\"color:red\">Có style</p>"
                + "<ul><li>Mục một</li></ul>";

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param(CONTENT, dirty))
                .andExpect(status().is3xxRedirection());

        // Assertions read the persisted row, not the HTTP response body.
        String stored = onlyAnnouncementOf(c).getContent();
        // Assert the attribute forms: the visible text deliberately contains the
        // bare words "onclick"/"style", so a substring check on those would be
        // satisfied by harmless body copy and prove nothing.
        assertThat(stored).doesNotContain("<script");
        assertThat(stored).doesNotContain("onclick=");
        assertThat(stored).doesNotContain("style=");
        assertThat(stored).contains("<strong>Giữ lại</strong>");
        assertThat(stored).contains("<li>Mục một</li>");
        assertThat(stored).contains("Có onclick").contains("Có style");
    }

    // ───────────────────── Helpers ─────────────────────

    /**
     * Saves an ACTIVE class. {@code approve} flips the constructor's DRAFT status,
     * which the student routes require — {@code StudentClassDetailService.get}
     * 404s on a class that is not ACTIVE.
     */
    private ClassEntity saveClass(String name, Long lecturerId, String code) {
        ClassEntity e = new ClassEntity(name, lecturerId, lecturerId, null, null, null, 100);
        userRepository.findById(lecturerId).map(User::getSubjectId).ifPresent(e::setSubjectId);
        e.approve(lecturerId, java.time.LocalDateTime.now());
        e.setCode(code);
        return classRepository.saveAndFlush(e);
    }

    private ClassAnnouncement seedAnnouncement(ClassEntity c, String content) {
        return announcementRepository.saveAndFlush(
                new ClassAnnouncement(c.getId(), content, lecturer.getId()));
    }

    private ClassAnnouncement reload(ClassAnnouncement a) {
        em.flush();
        em.clear();
        return announcementRepository.findById(a.getId()).orElseThrow();
    }

    private ClassAnnouncement onlyAnnouncementOf(ClassEntity c) {
        em.flush();
        List<ClassAnnouncement> rows = announcementRepository
                .findByClassIdOrderByCreatedAtDescIdDesc(
                        c.getId(), org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    /**
     * The single audit row belonging to one class. Scoping by class id isolates the
     * assertion from seeded noise without depending on {@code findAll} ordering,
     * and the size check makes "exactly one row" explicit.
     */
    private ClassActivity onlyActivityOf(ClassEntity c) {
        em.flush();
        List<ClassActivity> rows = activityRepository.findAll().stream()
                .filter(r -> c.getId().equals(r.getClassId()))
                .toList();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void enrollStudent(ClassEntity c) {
        enrollActive(student, c);
    }

    private void enrollActive(User u, ClassEntity c) {
        enrollmentRepository.saveAndFlush(
                Enrollment.createFor(u, c.getId(), Enrollment.JoinedVia.REQUEST, null));
    }

    private void enrollPending(User u, ClassEntity c) {
        enrollmentRepository.saveAndFlush(
                Enrollment.createPending(u, c.getId(), Enrollment.JoinedVia.REQUEST, null));
    }

    /** Announcement notifications for one class, isolated from seeded noise. */
    private List<Notification> announcementNotificationsFor(ClassEntity c) {
        return notificationRepository.findAll().stream()
                .filter(n -> NotificationType.CLASS_ANNOUNCEMENT.equals(n.getType()))
                .filter(n -> c.getId().equals(n.getReferenceId()))
                .toList();
    }

    /** Counts rendered feed items by their wrapper class. */
    private static int countFeedItems(String html) {
        int count = 0;
        int idx = html.indexOf("class=\"ann-item\"");
        while (idx >= 0) {
            count++;
            idx = html.indexOf("class=\"ann-item\"", idx + 1);
        }
        return count;
    }

    private User ensureUser(String email, String fullName, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = UserFactory.newAdminCreated(
                    email,
                    "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                    fullName,
                    role,
                    true,
                    null,
                    null);
            return userRepository.saveAndFlush(u);
        });
    }
}