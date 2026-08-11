package com.ksh.features.classes;

import com.ksh.entities.ClassAnnouncement;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassAnnouncementRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.upload.AnnouncementImageStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.imageio.ImageIO;

import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_INVALID;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_TOO_LARGE;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for announcement images
 * ({@code announcement-images-and-comments}).
 *
 * <p>The retrievability assertion is the one that catches a missing
 * {@code PublicUploadsController.PUBLIC_FOLDERS} entry — without it every upload
 * still reports success while the image renders broken, which reads as a
 * frontend defect (design D7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnnouncementImageIntegrationTest {

    private static final String FIELD = "file";
    private static final String UPLOADS_PREFIX = "/uploads/announcements/";

    @Autowired private MockMvc mockMvc;
    @Autowired private ClassRepository classRepository;
    @Autowired private ClassAnnouncementRepository announcementRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AnnouncementImageStorageService imageStorage;
    @Autowired private ObjectStorage objectStorage;
    @PersistenceContext private EntityManager em;

    private final ObjectMapper mapper = new ObjectMapper();
    private User lecturer;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
    }

    // ───────────────────── Upload validation ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void valid_png_uploads_and_returns_its_url() throws Exception {
        ClassEntity c = saveClass("Img-Ok", lecturer.getId(), "IMG01");

        String url = uploadPng(c, "photo.png", pngBytes(40, 40));

        assertThat(url).startsWith(UPLOADS_PREFIX);
        // Durable and staged keys alike must stay two path segments, or
        // PublicUploadsController 404s them.
        assertThat(url.substring("/uploads/".length()).split("/")).hasSize(2);
    }

    /**
     * The spec requires a Vietnamese message naming the 2 MB limit AND that
     * nothing reaches storage. Status alone is not enough: a handler that wrote
     * the object and then reported 400 would satisfy a status-only assertion
     * while leaving an orphan, and a 400 carrying an English stack message would
     * satisfy it too.
     */
    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void oversized_image_is_rejected_with_the_vietnamese_message_and_stores_nothing()
            throws Exception {
        ClassEntity c = saveClass("Img-Big", lecturer.getId(), "IMG02");
        // Valid PNG magic bytes, but past the 2 MB cap.
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1024];
        byte[] header = pngBytes(8, 8);
        System.arraycopy(header, 0, tooBig, 0, header.length);
        int keysBefore = stagedKeyCount();

        String body = mockMvc.perform(multipart(uploadUrl(c))
                        .file(new MockMultipartFile(FIELD, "big.png", "image/png", tooBig))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(MSG_ANNOUNCEMENT_IMAGE_TOO_LARGE);
        assertThat(stagedKeyCount())
                .as("a rejected oversized upload must not reach storage")
                .isEqualTo(keysBefore);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void disallowed_mime_type_is_rejected_with_the_vietnamese_message_and_stores_nothing()
            throws Exception {
        ClassEntity c = saveClass("Img-Gif", lecturer.getId(), "IMG03");
        int keysBefore = stagedKeyCount();

        String body = mockMvc.perform(multipart(uploadUrl(c))
                        .file(new MockMultipartFile(FIELD, "a.gif", "image/gif",
                                new byte[] {'G', 'I', 'F', '8', '9', 'a'}))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(MSG_ANNOUNCEMENT_IMAGE_TYPE);
        assertThat(stagedKeyCount())
                .as("a rejected disallowed type must not reach storage")
                .isEqualTo(keysBefore);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void text_file_renamed_png_is_rejected_by_the_magic_byte_check() throws Exception {
        ClassEntity c = saveClass("Img-Forged", lecturer.getId(), "IMG04");
        int keysBefore = stagedKeyCount();

        // Declared image/png with a .png name, but the bytes are plain text.
        String body = mockMvc.perform(multipart(uploadUrl(c))
                        .file(new MockMultipartFile(FIELD, "photo.png", "image/png",
                                "this is definitely not an image".getBytes()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(MSG_ANNOUNCEMENT_IMAGE_INVALID);
        assertThat(stagedKeyCount())
                .as("bytes failing the magic-byte check must not reach storage")
                .isEqualTo(keysBefore);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_cannot_reach_the_upload_endpoint() throws Exception {
        ClassEntity c = saveClass("Img-Stu", lecturer.getId(), "IMG05");

        mockMvc.perform(multipart(uploadUrl(c))
                        .file(new MockMultipartFile(FIELD, "photo.png", "image/png",
                                pngBytes(10, 10)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ───────────────────── Public retrieval ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void uploaded_image_is_retrievable_over_http() throws Exception {
        ClassEntity c = saveClass("Img-Serve", lecturer.getId(), "IMG06");
        String url = uploadPng(c, "served.png", pngBytes(24, 24));

        // Fails with 404 if "announcements" is missing from PUBLIC_FOLDERS.
        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(served).isNotEmpty();
    }

    // ───────────────────── Claim on publish ─────────────────────

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void publishing_promotes_a_staged_image_to_a_durable_url() throws Exception {
        ClassEntity c = saveClass("Img-Claim", lecturer.getId(), "IMG07");
        String stagedUrl = uploadPng(c, "claimed.png", pngBytes(32, 32));
        assertThat(stagedUrl).contains("staged-");

        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param("content", "<p>Kèm ảnh</p><img src=\"" + stagedUrl + "\">"))
                .andExpect(status().is3xxRedirection());

        String stored = onlyAnnouncementOf(c).getContent();
        assertThat(stored).contains(UPLOADS_PREFIX);
        assertThat(stored).doesNotContain("staged-");

        // The promoted URL must itself be publicly retrievable.
        String durable = extractFirstImageSrc(stored);
        mockMvc.perform(get(durable)).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void image_only_announcement_is_accepted() throws Exception {
        ClassEntity c = saveClass("Img-Only", lecturer.getId(), "IMG08");
        String stagedUrl = uploadPng(c, "onlyimg.png", pngBytes(20, 20));
        long before = announcementRepository.count();

        // No text at all — must not be rejected as empty.
        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param("content", "<img src=\"" + stagedUrl + "\">"))
                .andExpect(status().is3xxRedirection());

        assertThat(announcementRepository.count()).isEqualTo(before + 1);
        assertThat(onlyAnnouncementOf(c).getContent()).contains(UPLOADS_PREFIX);
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void script_only_body_is_still_rejected_after_the_image_rule_change() throws Exception {
        ClassEntity c = saveClass("Img-Script", lecturer.getId(), "IMG09");
        long before = announcementRepository.count();

        // Loosening the emptiness rule to admit images must not regress this.
        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param("content", "<script>alert(1)</script>"))
                .andExpect(status().isOk());

        assertThat(announcementRepository.count()).isEqualTo(before);
    }

    /**
     * Covers both halves of the spec scenario: the newly added image is promoted,
     * <b>and</b> images already durable from the original post are unaffected.
     *
     * <p>The second clause is the one that discriminates. A claim pass that
     * re-copied or re-keyed every {@code img[src]} rather than only staged ones
     * would still promote the new image and still pass a "contains a durable URL"
     * assertion, while silently orphaning the original's object. Asserting the
     * original URL survives byte-for-byte, and that its object still exists,
     * rejects that.
     */
    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void editing_promotes_a_newly_added_image_and_leaves_durable_ones_untouched()
            throws Exception {
        ClassEntity c = saveClass("Img-Edit", lecturer.getId(), "IMG10");

        // An image already durable from the original post: uploaded, then claimed
        // by the original publish, exactly as a real first post would leave it.
        String firstStaged = uploadPng(c, "original.png", pngBytes(12, 12));
        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param("content", "<p>Bản gốc</p><img src=\"" + firstStaged + "\">"))
                .andExpect(status().is3xxRedirection());
        em.flush();
        em.clear();

        ClassAnnouncement a = onlyAnnouncementOf(c);
        String originalDurableUrl = extractFirstImageSrc(a.getContent());
        String originalDurableKey = originalDurableUrl.substring("/uploads/".length());
        assertThat(objectStorage.exists(originalDurableKey)).isTrue();

        String stagedUrl = uploadPng(c, "edited.png", pngBytes(16, 16));

        mockMvc.perform(post("/lecturer/classes/" + c.getId()
                        + "/announcements/" + a.getId()).with(csrf())
                        .param("content", "<p>Đã sửa</p><img src=\"" + originalDurableUrl
                                + "\"><img src=\"" + stagedUrl + "\">"))
                .andExpect(status().is3xxRedirection());

        em.flush();
        em.clear();
        String stored = announcementRepository.findById(a.getId()).orElseThrow().getContent();

        // The new image was promoted.
        assertThat(stored).contains(UPLOADS_PREFIX);
        assertThat(stored).doesNotContain("staged-");

        // The already-durable image is unaffected: same URL, same live object.
        assertThat(stored)
                .as("an already-durable image must keep its URL across an edit")
                .contains(originalDurableUrl);
        assertThat(objectStorage.exists(originalDurableKey))
                .as("an already-durable image's object must survive an edit")
                .isTrue();
    }

    /**
     * Spec scenario "Images render in both feeds". Every other image assertion in
     * this class reads POST input or a persisted row; none proves an
     * {@code <img>} survives {@code HtmlSanitizer} and the {@code th:utext}
     * render path onto a page. A sanitizer safelist change dropping {@code img},
     * or a template switching to {@code th:text}, would leave every other test
     * green while no image ever displayed.
     */
    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void a_published_image_renders_on_the_lecturer_feed() throws Exception {
        ClassEntity c = saveClass("Img-FeedLec", lecturer.getId(), "IMG13");
        String durableUrl = publishImageAnnouncement(c, "feed-lec.png");

        // A live <img> tag carrying the durable src, not an escaped &lt;img&gt; —
        // which is what a th:text regression would emit.
        String html = mockMvc.perform(get("/lecturer/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(displayBody(html, "ann-body"))
                .as("the lecturer feed must render a live img tag")
                .contains("<img src=\"" + durableUrl + "\"")
                .doesNotContain("&lt;img");
    }

    /**
     * The student half of the same scenario. Split into its own test because
     * {@code /my/**} carries {@code PREAUTH_STUDENT}: a single lecturer-
     * authenticated test 403s on the student board, so "both feeds" cannot be
     * asserted from one authenticated session.
     *
     * <p>The announcement is published by the lecturer through a nested
     * authenticated session, so the durable URL under test is produced by the
     * real publish-and-claim path rather than hand-written.
     */
    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void a_published_image_renders_on_the_student_feed() throws Exception {
        ClassEntity c = saveClass("Img-FeedStu", lecturer.getId(), "IMG14");
        enrollmentRepository.saveAndFlush(Enrollment.createFor(
                userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow(),
                c.getId(), Enrollment.JoinedVia.REQUEST, null));

        // Seed the published body through the storage service directly: this test
        // authenticates as the student, who may not reach the lecturer publish
        // endpoint at all.
        String stagedUrl = imageStorage.store(lecturer.getId(), new MockMultipartFile(
                FIELD, "feed-stu.png", "image/png", pngBytes(22, 22)));
        String body = imageStorage.beginClaim(lecturer.getId())
                .claimIn("<p>Ảnh hiển thị</p><img src=\"" + stagedUrl + "\">");
        announcementRepository.saveAndFlush(
                new ClassAnnouncement(c.getId(), body, lecturer.getId()));
        em.flush();
        em.clear();

        String durableUrl = extractFirstImageSrc(onlyAnnouncementOf(c).getContent());
        assertThat(durableUrl).startsWith(UPLOADS_PREFIX).doesNotContain("staged-");

        String html = mockMvc.perform(get("/my/classes/" + c.getId() + "/board"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(displayBody(html, "student-ann-body"))
                .as("the student feed must render a live img tag")
                .contains("<img src=\"" + durableUrl + "\"")
                .doesNotContain("&lt;img");
    }

    /** Publishes an announcement carrying one uploaded image; returns its durable URL. */
    private String publishImageAnnouncement(ClassEntity c, String filename) throws Exception {
        String stagedUrl = uploadPng(c, filename, pngBytes(22, 22));
        mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements").with(csrf())
                        .param("content", "<p>Ảnh hiển thị</p><img src=\"" + stagedUrl + "\">"))
                .andExpect(status().is3xxRedirection());
        em.flush();
        em.clear();
        return extractFirstImageSrc(onlyAnnouncementOf(c).getContent());
    }

    /**
     * The rendered display body of the first announcement on a feed.
     *
     * <p>Scoped rather than asserted against the whole page because the lecturer
     * board also carries an inline edit {@code <textarea>} holding the same
     * announcement as escaped source — {@code &lt;img ...} — which is correct
     * there. A page-wide "no escaped img" assertion would fail on that editor,
     * and relaxing it to a page-wide "contains an img tag" would pass even if the
     * display body escaped everything. Isolating the display div is what makes
     * the {@code th:utext} contract the only thing under test.
     */
    private static String displayBody(String html, String bodyClass) {
        String marker = "class=\"" + bodyClass + "\"";
        int start = html.indexOf(marker);
        assertThat(start).as("feed must render a %s element", bodyClass).isNotNegative();
        int end = html.indexOf("</div>", start);
        assertThat(end).as("%s element must be closed", bodyClass).isNotNegative();
        return html.substring(start, end);
    }

    /**
     * The owner-binding rule at {@code AnnouncementImageStorageService.claimOne}.
     * Lecturer A uploads; admin B — who clears both the role gate and the
     * class-scope check, so only the ownership rule can reject — publishes a body
     * referencing A's staged URL.
     *
     * <p>Asserting on persisted state rather than status: a refusal that still
     * wrote the row, or that silently dropped the {@code <img>} and saved the
     * rest, would both return a plausible status while leaking A's upload.
     */
    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void one_lecturer_cannot_claim_another_users_staged_image() throws Exception {
        ClassEntity c = saveClass("Img-Cross", lecturer.getId(), "IMG11");

        // A uploads. Storing through the service directly binds the staged key to
        // A's id exactly as the endpoint does, without needing a second
        // authenticated MockMvc session inside this @WithUserDetails test.
        String stagedUrl = imageStorage.store(lecturer.getId(),
                new MockMultipartFile(FIELD, "a-owned.png", "image/png", pngBytes(28, 28)));
        assertThat(stagedUrl).contains("staged-" + lecturer.getId() + "-");
        String stagedKey = stagedUrl.substring("/uploads/".length());

        long before = announcementRepository.count();

        // B publishes a body pointing at A's staged object. B clears both the role
        // gate and the class-scope check, so only the ownership rule can reject.
        var result = mockMvc.perform(post("/lecturer/classes/" + c.getId() + "/announcements")
                        .with(csrf())
                        .param("content",
                                "<p>Ảnh của người khác</p><img src=\"" + stagedUrl + "\">"))
                .andReturn();

        // Persisted state is asserted FIRST and carries the security claim: no
        // announcement row means nothing was promoted and no partially-saved body
        // kept a reference to A's upload. Status is checked afterwards so a
        // regression fails on the state assertion, not merely on a status code.
        em.flush();
        assertThat(announcementRepository.count())
                .as("a refused cross-user claim must not persist an announcement")
                .isEqualTo(before);

        // A's staged object is untouched: the refusal neither promoted nor
        // consumed it.
        assertThat(objectStorage.exists(stagedKey))
                .as("the victim's staged upload must survive the refused claim")
                .isTrue();

        // Re-render with the inline error rather than a redirect to the board.
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    /**
     * Rollback compensation registered at
     * {@code AnnouncementImageStorageService.claimOne}. {@link TestTransaction}
     * ends the test's own transaction as rolled back, which fires the real
     * {@code afterCompletion} synchronizations rather than simulating them — so
     * the assertion covers the hook as it behaves in production.
     *
     * <p>The staged upload is written before the claim and must survive the
     * rollback: only the durable copy made by this attempt is compensated, and
     * the staged source is left for the scheduled sweeper.
     */
    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void rollback_after_a_claim_leaves_no_durable_orphan() throws Exception {
        ClassEntity c = saveClass("Img-Rollback", lecturer.getId(), "IMG12");
        String stagedUrl = uploadPng(c, "rolled-back.png", pngBytes(18, 18));
        String stagedKey = stagedUrl.substring("/uploads/".length());

        String rewritten = imageStorage.beginClaim(lecturer.getId()).claimIn(
                "<p>Ảnh</p><img src=\"" + stagedUrl + "\">");
        assertThat(rewritten).doesNotContain("staged-");

        // Read the durable key straight off the rewritten body, so the assertion
        // targets the exact object this claim created.
        String durableKey = extractFirstImageSrc(rewritten).substring("/uploads/".length());
        assertThat(durableKey).startsWith("announcements/").doesNotContain("staged-");
        assertThat(objectStorage.exists(durableKey))
                .as("the claim must create a durable copy before rollback")
                .isTrue();

        // A genuine rollback of the surrounding transaction.
        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(objectStorage.exists(durableKey))
                .as("rollback must delete the durable copy created by this attempt")
                .isFalse();
        assertThat(objectStorage.exists(stagedKey))
                .as("rollback must not delete the staged source")
                .isTrue();

        // Restart so the class-level @Transactional cleanup contract still holds.
        TestTransaction.start();
    }

    // ───────────────────── Helpers ─────────────────────

    private static String uploadUrl(ClassEntity c) {
        return "/lecturer/classes/" + c.getId() + "/announcements/images";
    }

    /**
     * Number of staged objects currently in storage. A rejected upload must
     * leave this unchanged — status alone cannot distinguish "validated before
     * writing" from "wrote, then reported an error".
     */
    private int stagedKeyCount() throws Exception {
        return objectStorage.listKeys("announcements/staged-").size();
    }

    /** Uploads a PNG and returns the staged URL carried at {@code data.url}. */
    private String uploadPng(ClassEntity c, String name, byte[] bytes) throws Exception {
        return parseUploadedUrl(mockMvc.perform(multipart(uploadUrl(c))
                        .file(new MockMultipartFile(FIELD, name, "image/png", bytes))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String parseUploadedUrl(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        assertThat(root.path("ok").asBoolean()).isTrue();
        String url = root.path("data").path("url").asText();
        assertThat(url).isNotBlank();
        return url;
    }

    /** Real PNG bytes, so the magic-byte check passes. */
    private static byte[] pngBytes(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String extractFirstImageSrc(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("src=\"([^\"]+)\"").matcher(html);
        assertThat(m.find()).as("stored body must contain an img src").isTrue();
        return m.group(1);
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

    private ClassEntity saveClass(String name, Long lecturerId, String code) {
        ClassEntity e = new ClassEntity(name, lecturerId, lecturerId, null, null, null, 100);
        userRepository.findById(lecturerId).map(User::getSubjectId).ifPresent(e::setSubjectId);
        e.approve(lecturerId, java.time.LocalDateTime.now());
        e.setCode(code);
        return classRepository.saveAndFlush(e);
    }
}