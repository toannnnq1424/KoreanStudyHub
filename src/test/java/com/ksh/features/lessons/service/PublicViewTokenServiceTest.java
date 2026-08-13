package com.ksh.features.lessons.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonAttachment;
import com.ksh.entities.PublicViewToken;
import com.ksh.entities.Section;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.lessons.dto.LessonDtos.LessonAttachmentRow;
import com.ksh.features.lessons.dto.LessonDtos.LessonRow;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.PublicViewTokenRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.features.lessons.service.PublicViewTokenService.AttachmentHandle;
import com.ksh.features.library.dto.LibraryDtos.LibraryAssetRow;
import com.ksh.features.library.service.LibraryService;
import com.ksh.security.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PublicViewTokenService}. Boots the full Spring
 * context with MySQL so the FK from {@code public_view_tokens.attachment_id}
 * to {@code lesson_attachments} and the token TTL/cleanup logic are exercised
 * end-to-end. Covers create → resolve → expiry → scheduled cleanup (KSH-4.x).
 */
@SpringBootTest
@Transactional
class PublicViewTokenServiceTest {

    @Autowired private PublicViewTokenService tokenService;
    @Autowired private PublicViewTokenRepository tokenRepository;
    @Autowired private LessonAttachmentRepository lessonAttachmentRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LibraryService libraryService;
    @Autowired private LessonsService lessonsService;
    @Autowired private LessonAttachmentsService attachmentsService;
    @Autowired private EntityManager entityManager;

    private User lecturer;
    private LessonAttachment attachment;
    private ClassEntity clazz;
    private Section section;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        clazz = saveClass("Token test class", "PVTCLS");
        section = sectionRepository.saveAndFlush(
                new Section(clazz.getId(), "Chương 1", (short) 0, lecturer.getId()));
        lesson = new Lesson(section.getId(), "Bài có tệp", (short) 0, lecturer.getId());
        lesson.updateContent("");
        lesson.publish();
        lesson = lessonRepository.saveAndFlush(lesson);
        attachment = lessonAttachmentRepository.saveAndFlush(new LessonAttachment(
                lesson.getId(), "slides.pptx", "stored/slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                4096L, lecturer.getId()));
    }

    private static byte[] pdfBytes() {
        return new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37, 0x0A};
    }

    @Test
    void createPublicViewUrl_persists_token_and_returns_public_url() {
        String url = tokenService.createPublicViewUrl(attachment.getId());

        assertThat(url).contains("/public/view/");
        String token = url.substring(url.lastIndexOf('/') + 1);
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");

        PublicViewToken persisted = tokenRepository
                .findByToken(PublicViewTokenService.hashToken(token)).orElseThrow();
        assertThat(persisted.getAttachmentId()).isEqualTo(attachment.getId());
        assertThat(persisted.getToken()).isNotEqualTo(token);
        // Default TTL is 1 hour; allow a small clock skew window.
        assertThat(persisted.getExpiresAt())
                .isAfter(LocalDateTime.now().plusMinutes(55))
                .isBefore(LocalDateTime.now().plusMinutes(65));
    }

    @Test
    void resolve_valid_token_returns_attachment_handle() {
        String url = tokenService.createPublicViewUrl(attachment.getId());
        String token = url.substring(url.lastIndexOf('/') + 1);

        AttachmentHandle handle = tokenService.resolve(token);

        assertThat(handle.originalFilename()).isEqualTo("slides.pptx");
        assertThat(handle.mimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        assertThat(handle.sizeBytes()).isEqualTo(4096L);
        assertThat(handle.storageKey()).isNotNull();
        assertThat(handle.storageKey()).endsWith("slides.pptx");
    }

    @Test
    void resolve_rejects_token_after_lesson_is_unpublished() {
        String url = tokenService.createPublicViewUrl(attachment.getId());
        String token = url.substring(url.lastIndexOf('/') + 1);
        lesson.unpublish();
        lessonRepository.saveAndFlush(lesson);
        entityManager.clear();

        assertThatThrownBy(() -> tokenService.resolve(token))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Attachment not publicly available");
    }

    @Test
    void resolve_rejects_token_issued_before_lesson_is_republished() {
        String url = tokenService.createPublicViewUrl(attachment.getId());
        String token = url.substring(url.lastIndexOf('/') + 1);
        lesson.unpublish();
        lesson.publish();
        lessonRepository.saveAndFlush(lesson);
        entityManager.clear();

        assertThatThrownBy(() -> tokenService.resolve(token))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Attachment not publicly available");
    }

    @Test
    void resolve_rejects_token_after_class_is_softDeleted() {
        String url = tokenService.createPublicViewUrl(attachment.getId());
        String token = url.substring(url.lastIndexOf('/') + 1);
        clazz.softDelete();
        classRepository.saveAndFlush(clazz);
        entityManager.clear();

        assertThatThrownBy(() -> tokenService.resolve(token))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Attachment not publicly available");
    }

    @Test
    void resolve_legacy_32_hex_token_remains_compatible_until_expiry() {
        String legacyToken = "abcdef0123456789abcdef0123456789";
        tokenRepository.saveAndFlush(new PublicViewToken(
                attachment.getId(), legacyToken, LocalDateTime.now().plusMinutes(30)));

        AttachmentHandle handle = tokenService.resolve(legacyToken);

        assertThat(handle.originalFilename()).isEqualTo("slides.pptx");
    }

    @Test
    void stored_digest_is_not_itself_an_accepted_bearer_credential() {
        String url = tokenService.createPublicViewUrl(attachment.getId());
        String rawToken = url.substring(url.lastIndexOf('/') + 1);
        String storedDigest = PublicViewTokenService.hashToken(rawToken);

        assertThatThrownBy(() -> tokenService.resolve(storedDigest))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Invalid token");
    }

    @Test
    void minting_a_replacement_revokes_the_previous_live_token() {
        String firstUrl = tokenService.createPublicViewUrl(attachment.getId());
        String firstToken = firstUrl.substring(firstUrl.lastIndexOf('/') + 1);
        String secondUrl = tokenService.createPublicViewUrl(attachment.getId());
        String secondToken = secondUrl.substring(secondUrl.lastIndexOf('/') + 1);

        assertThatThrownBy(() -> tokenService.resolve(firstToken))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Invalid token");
        assertThat(tokenService.resolve(secondToken).originalFilename())
                .isEqualTo("slides.pptx");
    }

    @Test
    void resolve_unknown_token_throws() {
        assertThatThrownBy(() -> tokenService.resolve("deadbeefdeadbeefdeadbeefdeadbeef"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Invalid token");
    }

    @Test
    void resolve_expired_token_throws_and_deletes_it() {
        PublicViewToken expired = tokenRepository.saveAndFlush(new PublicViewToken(
                attachment.getId(), "0123456789abcdef0123456789abcdef",
                LocalDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> tokenService.resolve(expired.getToken()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Token expired");

        // The expired row is purged on access.
        entityManager.flush();
        entityManager.clear();
        assertThat(tokenRepository.findByToken(expired.getToken())).isEmpty();
    }

    @Test
    void cleanupExpired_removes_only_expired_tokens() {
        PublicViewToken expired = tokenRepository.saveAndFlush(new PublicViewToken(
                attachment.getId(), "expiredsweeptokenexpiredsweep000",
                LocalDateTime.now().minusHours(2)));
        PublicViewToken valid = tokenRepository.saveAndFlush(new PublicViewToken(
                attachment.getId(), "validsweeptokenvalidsweeptoken00",
                LocalDateTime.now().plusHours(1)));

        int deleted = tokenService.cleanupExpired();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        entityManager.clear();
        assertThat(tokenRepository.findByToken(expired.getToken())).isEmpty();
        assertThat(tokenRepository.findByToken(valid.getToken())).isPresent();
    }

    @Test
    void resolve_library_backed_attachment_uses_library_root_and_existing_file() throws Exception {
        LibraryAssetRow asset = libraryService.upload(
                lecturer.getId(),
                new MockMultipartFile("file", "lib-slides.pdf", "application/pdf", pdfBytes()),
                "DOCUMENT");
        LessonRow lesson = lessonsService.create(
                clazz.getId(), section.getId(), "Bài library view", "PUBLISHED", "",
                lecturer.getId(), Role.LECTURER);
        LessonAttachmentRow bound = attachmentsService.bindAttachmentFromLibrary(
                clazz.getId(), section.getId(), lesson.id(), asset.id(),
                lecturer.getId(), Role.LECTURER);

        String url = tokenService.createPublicViewUrl(bound.id());
        String token = url.substring(url.lastIndexOf('/') + 1);

        AttachmentHandle handle = tokenService.resolve(token);

        assertThat(handle.originalFilename()).isEqualTo("lib-slides.pdf");
        assertThat(handle.mimeType()).isEqualTo("application/pdf");
        assertThat(handle.storageKey()).contains("library");
        assertThat(handle.storageKey()).startsWith("library/");
    }

    private ClassEntity saveClass(String name, String code) {
        ClassEntity entity = new ClassEntity(name, lecturer.getId(), lecturer.getId(),
                null, null, null, 100);
        // Unique code per run to avoid collisions with leftover rows.
        entity.setCode(code + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        try {
            return classRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            entity.setCode(code + "x" + UUID.randomUUID().toString().substring(0, 3));
            return classRepository.saveAndFlush(entity);
        }
    }
}
