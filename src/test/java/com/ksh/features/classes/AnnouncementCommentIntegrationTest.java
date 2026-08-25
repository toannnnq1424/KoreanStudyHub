package com.ksh.features.classes;

import com.ksh.entities.AnnouncementComment;
import com.ksh.entities.ClassAnnouncement;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.AnnouncementCommentRepository;
import com.ksh.features.classes.repository.ClassAnnouncementRepository;
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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static com.ksh.common.IConstant.DEFAULT_COMMENT_PREVIEW_SIZE;
import static com.ksh.common.IConstant.MAX_COMMENT_LENGTH;
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
 * Integration coverage for announcement comments
 * ({@code announcement-images-and-comments}).
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
class AnnouncementCommentIntegrationTest {

    private static final String CONTENT = "content";
    private static final String TEXT = "Câu hỏi của học viên";
    /**
     * Marks one rendered comment row. The trailing quote matters: without it this
     * also matches {@code ann-comment-count}, {@code ann-comment-form} and every
     * other {@code ann-comment-*} class, so a zero-comment page would count as
     * several rows.
     */
    private static final String COMMENT_ROW_MARKER = "class=\"ann-comment\"";

    @Autowired private MockMvc mockMvc;
    @Autowired private ClassRepository classRepository;
    @Autowired private ClassAnnouncementRepository announcementRepository;
    @Autowired private AnnouncementCommentRepository commentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private MailOutboxRepository mailOutboxRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    private User lecturer;
    private User leader;
    private User student;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        leader = userRepository.findByEmailIgnoreCase("leader@ksh.edu.vn").orElseThrow();
        student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
    }

    // ───────────────────── 7.2 Creation ─────────────────────

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void enrolled_student_creates_a_comment() throws Exception {
        ClassEntity c = saveClass("Cmt-Stu", lecturer.getId(), "CMT01");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, TEXT))
                .andExpect(status().is3xxRedirection());

        assertThat(commentRepository.count()).isEqualTo(before + 1);
        AnnouncementComment saved = onlyCommentOf(a);
        assertThat(saved.getCreatedBy()).isEqualTo(student.getId());
        assertThat(saved.getClassId()).isEqualTo(c.getId());
        assertThat(saved.getContent()).isEqualTo(TEXT);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void owning_lecturer_creates_a_comment() throws Exception {
        ClassEntity c = saveClass("Cmt-Lec", lecturer.getId(), "CMT02");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        long before = commentRepository.count();

        mockMvc.perform(post(lecturerCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, "Giảng viên trả lời"))
                .andExpect(status().is3xxRedirection());

        assertThat(commentRepository.count()).isEqualTo(before + 1);
        assertThat(onlyCommentOf(a).getCreatedBy()).isEqualTo(lecturer.getId());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void blank_comment_is_rejected_without_writing_a_row() throws Exception {
        ClassEntity c = saveClass("Cmt-Blank", lecturer.getId(), "CMT03");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không được để trống")));

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void whitespace_only_comment_is_rejected_without_writing_a_row() throws Exception {
        ClassEntity c = saveClass("Cmt-Ws", lecturer.getId(), "CMT04");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, "     "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không được để trống")));

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void comment_over_the_length_cap_is_rejected() throws Exception {
        ClassEntity c = saveClass("Cmt-Long", lecturer.getId(), "CMT05");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, "a".repeat(MAX_COMMENT_LENGTH + 1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("quá dài")));

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    // ───────────────────── 7.3 Authorization ─────────────────────

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void non_enrolled_student_cannot_comment() throws Exception {
        ClassEntity c = saveClass("Cmt-NoEnrol", lecturer.getId(), "CMT06");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, TEXT))
                .andExpect(status().isNotFound());

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_with_a_non_active_enrollment_cannot_comment() throws Exception {
        ClassEntity c = saveClass("Cmt-Pending", lecturer.getId(), "CMT07");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollPending(student, c);
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, TEXT))
                .andExpect(status().isNotFound());

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void commenting_on_a_soft_deleted_announcement_is_refused() throws Exception {
        ClassEntity c = saveClass("Cmt-DelAnn", lecturer.getId(), "CMT08");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Sắp xoá</p>");
        enrollActive(student, c);
        a.softDelete();
        announcementRepository.saveAndFlush(a);
        em.clear();
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, TEXT))
                .andExpect(status().isNotFound());

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void commenting_through_the_wrong_class_id_is_refused() throws Exception {
        ClassEntity classC = saveClass("Cmt-C", lecturer.getId(), "CMT09");
        ClassEntity classD = saveClass("Cmt-D", lecturer.getId(), "CMT10");
        ClassAnnouncement inD = seedAnnouncement(classD, "<p>Thuộc lớp D</p>");
        // Enrolled in BOTH, so only the class-scope check can reject.
        enrollActive(student, classC);
        enrollActive(student, classD);
        long before = commentRepository.count();

        mockMvc.perform(post(studentCommentUrl(classC, inD)).with(csrf())
                        .param(CONTENT, TEXT))
                .andExpect(status().isNotFound());

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_cannot_delete_another_students_comment() throws Exception {
        ClassEntity c = saveClass("Cmt-OtherDel", lecturer.getId(), "CMT11");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        User other = ensureUser("cmt-other@ksh.edu.vn", "Cmt Other", Role.STUDENT);
        enrollActive(student, c);
        enrollActive(other, c);
        AnnouncementComment theirs = seedComment(c, a, other, "Bình luận của người khác");

        // 403, not 404: membership is proven, only ownership failed.
        mockMvc.perform(post(studentCommentDeleteUrl(c, a, theirs)).with(csrf()))
                .andExpect(status().isForbidden());

        em.clear();
        assertThat(commentRepository.findById(theirs.getId())).isPresent();
        mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bình luận của người khác")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void author_deletes_their_own_comment() throws Exception {
        ClassEntity c = saveClass("Cmt-SelfDel", lecturer.getId(), "CMT12");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        AnnouncementComment mine = seedComment(c, a, student, "Bình luận của tôi");

        mockMvc.perform(post(studentCommentDeleteUrl(c, a, mine)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertSoftDeleted(mine);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void lecturer_deletes_a_students_comment() throws Exception {
        ClassEntity c = saveClass("Cmt-LecDel", lecturer.getId(), "CMT13");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        AnnouncementComment theirs = seedComment(c, a, student, "Bình luận học viên");

        mockMvc.perform(post(lecturerCommentDeleteUrl(c, a, theirs)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertSoftDeleted(theirs);
    }

    /**
     * Spec scenario "Delete affordance is not rendered for callers who lack the
     * right". The 403 half is covered above; this is the rendering half.
     *
     * <p>Both directions are asserted in one render, which is what makes it
     * discriminate. A template that always emits the control passes the
     * "author sees it" check alone; one that never emits it passes the
     * "other student does not" check alone. Requiring exactly one delete form on
     * a page holding two comments — and requiring it to target the viewer's own
     * comment id — rejects both.
     *
     * <p>The spec is explicit that hiding the control does not substitute for the
     * server-side check, so this complements rather than replaces
     * {@code student_cannot_delete_another_students_comment}.
     */
    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void delete_affordance_renders_only_for_the_comments_the_viewer_may_delete()
            throws Exception {
        ClassEntity c = saveClass("Cmt-Afford", lecturer.getId(), "CMT23");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        User other = ensureUser("cmt-afford@ksh.edu.vn", "Cmt Afford", Role.STUDENT);
        enrollActive(student, c);
        enrollActive(other, c);
        AnnouncementComment mine = seedComment(c, a, student, "Bình luận của tôi");
        AnnouncementComment theirs = seedComment(c, a, other, "Bình luận của người khác");
        em.clear();

        String html = boardHtml("/my/classes/" + c.getId() + "/board");

        // Both comments render, so absence below means "control hidden", not
        // "comment missing".
        assertThat(html).contains("Bình luận của tôi", "Bình luận của người khác");

        // Exactly one delete control, and it targets the viewer's own comment.
        assertThat(renderedCommentCount(html, "class=\"student-ann-comment-delete\""))
                .as("a viewer may delete only their own comment, so exactly one control")
                .isEqualTo(1);
        assertThat(html)
                .as("the rendered control must target the viewer's own comment")
                .contains("/comments/" + mine.getId() + "/delete");
        assertThat(html)
                .as("no delete control may target another member's comment")
                .doesNotContain("/comments/" + theirs.getId() + "/delete");
    }

    /**
     * The privileged half of the same rule: a lecturer sees a delete control on a
     * student's comment. Without this, a template that hid the control from
     * everyone would still pass the student-side assertion above.
     */
    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void delete_affordance_renders_for_privileged_viewers() throws Exception {
        ClassEntity c = saveClass("Cmt-AffordLec", lecturer.getId(), "CMT24");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        AnnouncementComment theirs = seedComment(c, a, student, "Bình luận học viên");
        em.clear();

        String html = boardHtml("/lecturer/classes/" + c.getId() + "/board");

        assertThat(html).contains("Bình luận học viên");
        assertThat(html)
                .as("teaching staff may delete any comment in their class")
                .contains("/comments/" + theirs.getId() + "/delete");
    }

    // ───────────────────── 7.4 Counts and notifications ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void soft_deleted_comments_are_excluded_from_the_count() throws Exception {
        ClassEntity c = saveClass("Cmt-Count", lecturer.getId(), "CMT14");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        seedComment(c, a, student, "Bình luận một");
        seedComment(c, a, student, "Bình luận hai");
        AnnouncementComment gone = seedComment(c, a, student, "Bình luận ba");
        gone.softDelete();
        commentRepository.saveAndFlush(gone);
        em.clear();

        mockMvc.perform(get("/lecturer/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 bình luận")))
                .andExpect(content().string(not(containsString("Bình luận ba"))));
    }

    /**
     * Preview truncation and the total count are separate numbers, and only a
     * seed larger than {@code DEFAULT_COMMENT_PREVIEW_SIZE} can tell them apart:
     * with 3 or fewer comments a broken cap still renders the right rows.
     */
    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void preview_truncates_to_three_while_the_count_reports_all_seven() throws Exception {
        ClassEntity c = saveClass("Cmt-Trunc", lecturer.getId(), "CMT19");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        for (int i = 1; i <= 7; i++) {
            seedComment(c, a, student, "Bình luận số " + i);
        }
        em.clear();

        String html = boardHtml("/lecturer/classes/" + c.getId() + "/board");

        assertThat(html).contains("7 bình luận");
        assertThat(renderedCommentCount(html, "Bình luận số ")).isEqualTo(
                DEFAULT_COMMENT_PREVIEW_SIZE);
    }

    /**
     * The discriminating ordering assertion. The repository selects newest-first
     * and the service reverses each truncated group, so the preview must hold
     * comments 5, 6, 7 rendered in that order. Every plausible break produces a
     * different set or a different order:
     * dropping the reverse yields 7, 6, 5; ordering the query oldest-first yields
     * 1, 2, 3. Asserting the exact triple in the exact positions rejects all of
     * them, which a "contains the newest comment" assertion would not.
     */
    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void preview_keeps_the_newest_three_rendered_oldest_of_those_first() throws Exception {
        ClassEntity c = saveClass("Cmt-Newest", lecturer.getId(), "CMT20");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        // Seeded in ascending order, so ascending id is ascending age. created_at
        // is second-resolution DATETIME and may tie; the query's `id DESC`
        // tiebreaker makes the intended order deterministic regardless.
        for (int i = 1; i <= 7; i++) {
            seedComment(c, a, student, "Bình luận số " + i);
        }
        em.clear();

        String html = boardHtml("/lecturer/classes/" + c.getId() + "/board");

        // The newest three, and only those.
        assertThat(html).contains("Bình luận số 5", "Bình luận số 6", "Bình luận số 7");
        assertThat(html).doesNotContain("Bình luận số 1", "Bình luận số 2",
                "Bình luận số 3", "Bình luận số 4");
        // Rendered oldest-of-those-three first: 5 before 6 before 7.
        assertThat(html.indexOf("Bình luận số 5"))
                .as("comment 5 must render before comment 6")
                .isLessThan(html.indexOf("Bình luận số 6"));
        assertThat(html.indexOf("Bình luận số 6"))
                .as("comment 6 must render before comment 7")
                .isLessThan(html.indexOf("Bình luận số 7"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void an_announcement_with_no_comments_reads_zero_and_renders_no_rows() throws Exception {
        ClassEntity c = saveClass("Cmt-Zero", lecturer.getId(), "CMT21");
        seedAnnouncement(c, "<p>Chưa ai bình luận</p>");
        em.clear();

        String html = boardHtml("/lecturer/classes/" + c.getId() + "/board");

        assertThat(html).contains("0 bình luận");
        assertThat(renderedCommentCount(html, COMMENT_ROW_MARKER)).isZero();
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void deleting_a_comment_decrements_the_rendered_count() throws Exception {
        ClassEntity c = saveClass("Cmt-DelCount", lecturer.getId(), "CMT22");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        seedComment(c, a, student, "Bình luận giữ lại");
        AnnouncementComment doomed = seedComment(c, a, student, "Bình luận sẽ xoá");
        em.clear();
        assertThat(boardHtml("/my/classes/" + c.getId() + "/board")).contains("2 bình luận");

        mockMvc.perform(post(studentCommentDeleteUrl(c, a, doomed)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        // Flush before clearing: the soft-delete flag is set on a managed instance
        // and @SQLRestriction filters SQL, so an unflushed delete stays invisible
        // to the re-read and the count would still report both comments.
        em.flush();
        em.clear();

        String after = boardHtml("/my/classes/" + c.getId() + "/board");
        assertThat(after).contains("1 bình luận");
        assertThat(after).doesNotContain("Bình luận sẽ xoá");
        assertThat(after).contains("Bình luận giữ lại");
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void commenting_notifies_the_announcement_author_and_queues_no_mail() throws Exception {
        ClassEntity c = saveClass("Cmt-Notify", lecturer.getId(), "CMT15");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        long notifBefore = notificationRepository.count();
        long mailBefore = mailOutboxRepository.count();

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, TEXT))
                .andExpect(status().is3xxRedirection());
        em.flush();

        List<Notification> created = commentNotificationsFor(c);
        assertThat(created).hasSize(1);
        assertThat(notificationRepository.count()).isEqualTo(notifBefore + 1);
        assertThat(created.get(0).getUserId()).isEqualTo(lecturer.getId());
        assertThat(created.get(0).getReferenceType()).isEqualTo(NotificationType.REF_CLASS);
        // referenceId is the CLASS id — the deep-link interpolates it as one.
        assertThat(created.get(0).getReferenceId()).isEqualTo(c.getId());

        // ANNOUNCEMENT_COMMENT is outside EMAIL_TYPES, so nothing is queued.
        assertThat(mailOutboxRepository.count()).isEqualTo(mailBefore);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void self_comment_creates_no_notification() throws Exception {
        ClassEntity c = saveClass("Cmt-Self", lecturer.getId(), "CMT16");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        long before = notificationRepository.count();

        mockMvc.perform(post(lecturerCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, "Tự bình luận"))
                .andExpect(status().is3xxRedirection());
        em.flush();

        assertThat(commentNotificationsFor(c)).isEmpty();
        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void opening_a_comment_notification_lands_on_the_board_not_lessons() throws Exception {
        ClassEntity c = saveClass("Cmt-Deep", lecturer.getId(), "CMT17");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        User other = ensureUser("cmt-deep@ksh.edu.vn", "Cmt Deep", Role.STUDENT);
        enrollActive(other, c);
        seedComment(c, a, other, "Kích hoạt thông báo");

        // Seeding the row directly keeps this test focused on the redirect
        // resolver; the create-path notification is asserted separately above.
        Notification n = notificationRepository.saveAndFlush(new Notification(
                lecturer.getId(),
                "Có bình luận mới về thông báo của bạn",
                "nội dung",
                NotificationType.ANNOUNCEMENT_COMMENT,
                NotificationType.REF_CLASS,
                c.getId()));

        // Without the resolveClassRedirect branch this lands on /lessons.
        mockMvc.perform(post("/my/notifications/" + n.getId() + "/open").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my/classes/" + c.getId() + "/board"));
    }

    /**
     * Guards the repository <b>shape</b> only: neither query method may offer a
     * single-id form that invites a per-announcement call.
     *
     * <p><b>This does not cover the no-N+1 scenario</b> and must not be cited as
     * doing so. It reflects on signatures, so an implementation that calls the
     * batched method once per announcement inside a loop passes it unchanged.
     * The scenario "One page of announcements issues a fixed number of comment
     * queries" is covered by {@link AnnouncementCommentQueryCountTest}, which
     * observes real SQL prepare counts through Hibernate {@code Statistics}.
     *
     * <p>Kept because it fails earlier and more legibly than a query count when
     * someone adds a single-id overload — the change that makes an N+1 easy to
     * write in the first place.
     */
    @Test
    void comment_repository_exposes_no_single_id_query_method() {
        List<Method> queryMethods = List.of(AnnouncementCommentRepository.class.getMethods())
                .stream()
                .filter(m -> m.getName().equals("countByAnnouncementIds")
                        || m.getName().equals("findForAnnouncementIds"))
                .toList();

        assertThat(queryMethods).hasSize(2);
        for (Method m : queryMethods) {
            assertThat(m.getParameterCount()).isEqualTo(1);
            assertThat(Collection.class)
                    .as("%s must take a Collection of ids, not a single id", m.getName())
                    .isAssignableFrom(m.getParameterTypes()[0]);
        }
    }

    // ───────────────────── 7.5 Rendering safety ─────────────────────

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void comment_markup_renders_escaped_proving_th_text() throws Exception {
        ClassEntity c = saveClass("Cmt-Xss", lecturer.getId(), "CMT18");
        ClassAnnouncement a = seedAnnouncement(c, "<p>Thông báo</p>");
        enrollActive(student, c);
        String payload = "<script>alert(1)</script>";

        mockMvc.perform(post(studentCommentUrl(c, a)).with(csrf())
                        .param(CONTENT, payload))
                .andExpect(status().is3xxRedirection());
        em.flush();
        em.clear();

        // Stored verbatim: escaping happens at render, not on write.
        assertThat(onlyCommentOf(a).getContent()).isEqualTo(payload);

        // The rendered page must carry the escaped form and no live script tag.
        String html = mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain(payload);
    }

    // ───────────────────── Helpers ─────────────────────

    /** Fetches a board page and returns its rendered HTML. */
    private String boardHtml(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Counts non-overlapping occurrences of a marker in rendered HTML. */
    private static int renderedCommentCount(String html, String marker) {
        int count = 0;
        for (int at = html.indexOf(marker); at >= 0;
             at = html.indexOf(marker, at + marker.length())) {
            count++;
        }
        return count;
    }

    private static String lecturerCommentUrl(ClassEntity c, ClassAnnouncement a) {
        return "/lecturer/classes/" + c.getId() + "/announcements/" + a.getId() + "/comments";
    }

    private static String lecturerCommentDeleteUrl(ClassEntity c, ClassAnnouncement a,
                                                   AnnouncementComment cm) {
        return lecturerCommentUrl(c, a) + "/" + cm.getId() + "/delete";
    }

    private static String studentCommentUrl(ClassEntity c, ClassAnnouncement a) {
        return "/my/classes/" + c.getId() + "/announcements/" + a.getId() + "/comments";
    }

    private static String studentCommentDeleteUrl(ClassEntity c, ClassAnnouncement a,
                                                  AnnouncementComment cm) {
        return studentCommentUrl(c, a) + "/" + cm.getId() + "/delete";
    }

    /**
     * Asserts a comment survives with {@code is_deleted = 1}. {@code @SQLRestriction}
     * filters SQL rather than managed instances, so the flag is read through a
     * native query and the persistence context is cleared before the JPA check.
     */
    private void assertSoftDeleted(AnnouncementComment cm) {
        em.flush();
        Object flag = em.createNativeQuery(
                        "SELECT is_deleted FROM announcement_comments WHERE id = :id")
                .setParameter("id", cm.getId())
                .getSingleResult();
        int deletedFlag = (flag instanceof Boolean b) ? (b ? 1 : 0) : ((Number) flag).intValue();
        assertThat(deletedFlag).isEqualTo(1);

        em.clear();
        assertThat(commentRepository.findById(cm.getId())).isEmpty();
    }

    /**
     * Saves an ACTIVE class. {@code approve} flips the constructor's DRAFT status,
     * which the student routes require.
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

    private AnnouncementComment seedComment(ClassEntity c, ClassAnnouncement a,
                                            User author, String content) {
        return commentRepository.saveAndFlush(
                new AnnouncementComment(a.getId(), c.getId(), content, author.getId()));
    }

    private AnnouncementComment onlyCommentOf(ClassAnnouncement a) {
        em.flush();
        List<AnnouncementComment> rows =
                commentRepository.findForAnnouncementIds(List.of(a.getId()));
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void enrollActive(User u, ClassEntity c) {
        enrollmentRepository.saveAndFlush(
                Enrollment.createFor(u, c.getId(), Enrollment.JoinedVia.REQUEST, null));
    }

    private void enrollPending(User u, ClassEntity c) {
        enrollmentRepository.saveAndFlush(
                Enrollment.createPending(u, c.getId(), Enrollment.JoinedVia.REQUEST, null));
    }

    /** Comment notifications for one class, isolated from seeded noise. */
    private List<Notification> commentNotificationsFor(ClassEntity c) {
        return notificationRepository.findAll().stream()
                .filter(n -> NotificationType.ANNOUNCEMENT_COMMENT.equals(n.getType()))
                .filter(n -> c.getId().equals(n.getReferenceId()))
                .toList();
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