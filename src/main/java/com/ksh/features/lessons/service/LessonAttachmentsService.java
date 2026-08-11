package com.ksh.features.lessons.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonActivity;
import com.ksh.entities.LessonAttachment;
import com.ksh.entities.LibraryAsset;
import com.ksh.entities.Section;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.lessons.dto.LessonDtos.LessonAttachmentRow;
import com.ksh.features.lessons.dto.LessonDtos.LessonForm;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.features.library.service.LibraryService;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.StorageKeys;
import com.ksh.features.storage.StorageTransactionLifecycle;
import com.ksh.features.upload.LessonAttachmentStorageService;
import com.ksh.features.upload.LessonAttachmentStorageService.StoredAttachment;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ksh.common.IConstant.CONTENT_TYPE_PDF;
import static com.ksh.common.IConstant.LESSON_STATUS_PUBLISHED;
import static com.ksh.common.IConstant.MSG_ATTACHMENT_NOT_FOUND;
import static com.ksh.common.IConstant.MSG_FORBIDDEN_FOR_CLASS;
import static com.ksh.common.IConstant.MSG_LESSON_NOT_FOUND;
import static com.ksh.common.IConstant.MSG_LIBRARY_BIND_INVALID_KIND;
import static com.ksh.common.IConstant.MSG_LIBRARY_BIND_NOT_PDF;
import static com.ksh.entities.LibraryAsset.KIND_DOCUMENT;
import static com.ksh.entities.LessonAttachment.ORIGIN_CANONICAL_TEMPLATE;
import static com.ksh.entities.LessonAttachment.ORIGIN_CLASS_PRIVATE;

/**
 * Business service for lesson attachments.
 *
 * <p>Three-layer auth on upload/delete/list: class-level edit via
 * {@link ClassesService#getEditable}, section↔class binding via
 * {@link LessonsReorderService#verifySectionBelongsToClass}, and
 * lesson↔section binding via {@link LessonRepository#findByIdAndSectionId}.
 * Download widens this: enrolled students may download only when the
 * parent lesson is {@code PUBLISHED}. Cascade on lesson soft-delete is
 * application-level — {@link LessonsService#delete} calls
 * {@link #deleteAllByLesson(Long)} BEFORE markDeleted.
 */
@Service
public class LessonAttachmentsService {

    private static final String MSG_CANONICAL_CONTENT_LOCKED =
            "Bài giảng được phân phối từ Kho bài giảng; hãy sửa nội dung chính tại bản chuẩn";
    private static final String MSG_CANONICAL_ATTACHMENT_LOCKED =
            "Tài liệu chuẩn phải được gỡ trong Kho bài giảng";
    private static final String MSG_LIBRARY_ALREADY_BOUND =
            "Tài liệu này đã được gắn vào bài giảng";

    private final LessonAttachmentRepository attachmentRepository;
    private final LessonRepository lessonRepository;
    private final SectionRepository sectionRepository;
    private final LessonAttachmentStorageService storage;
    private final ObjectStorage objectStorage;
    private final LibraryService libraryService;
    private final ClassesService classesService;
    private final LessonsReorderService reorderService;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonActivityWriter activityWriter;
    private final LessonContentTypeSwitcher contentTypeSwitcher;

    public LessonAttachmentsService(LessonAttachmentRepository attachmentRepository,
                                    LessonRepository lessonRepository,
                                    SectionRepository sectionRepository,
                                    LessonAttachmentStorageService storage,
                                    ObjectStorage objectStorage,
                                    LibraryService libraryService,
                                    ClassesService classesService,
                                    LessonsReorderService reorderService,
                                    EnrollmentRepository enrollmentRepository,
                                    LessonActivityWriter activityWriter,
                                    LessonContentTypeSwitcher contentTypeSwitcher) {
        this.attachmentRepository = attachmentRepository;
        this.lessonRepository = lessonRepository;
        this.sectionRepository = sectionRepository;
        this.storage = storage;
        this.objectStorage = objectStorage;
        this.libraryService = libraryService;
        this.classesService = classesService;
        this.reorderService = reorderService;
        this.enrollmentRepository = enrollmentRepository;
        this.activityWriter = activityWriter;
        this.contentTypeSwitcher = contentTypeSwitcher;
    }

    /** Lists attachments of a lesson — used to preload the edit page. */
    @Transactional(readOnly = true)
    public List<LessonAttachmentRow> listForLesson(Long classId, Long sectionId, Long lessonId,
                                                   Long userId, Role role) {
        classesService.getEditable(classId, userId, role);
        reorderService.verifySectionBelongsToClass(sectionId, classId);
        loadLesson(sectionId, lessonId);
        return mapRows(attachmentRepository.findByLessonIdOrderByUploadedAtAsc(lessonId));
    }

    /**
     * Stores the uploaded file on disk and persists the metadata row.
     *
     * @throws IllegalArgumentException with a Vietnamese-friendly message
     *                                  for any validation failure
     * @throws IOException              if the file cannot be written to disk
     */
    @Transactional
    public LessonAttachmentRow upload(Long classId, Long sectionId, Long lessonId,
                                      MultipartFile file, Long userId, Role role) throws IOException {
        classesService.getEditable(classId, userId, role);
        reorderService.verifySectionBelongsToClass(sectionId, classId);
        loadLesson(sectionId, lessonId);

        StoredAttachment stored = storage.store(file, lessonId);
        StorageTransactionLifecycle.deleteOnRollback(
                () -> storage.delete(stored.storedPath()));
        LessonAttachment row = new LessonAttachment(lessonId, stored.originalFilename(),
                stored.storedPath(), stored.mimeType(), stored.sizeBytes(), userId,
                null, ORIGIN_CLASS_PRIVATE);
        LessonAttachment saved = attachmentRepository.save(row);
        activityWriter.write(lessonId, LessonActivity.TYPE_ATTACHMENT_ADDED,
                "Thêm tệp đính kèm: " + saved.getOriginalFilename(), userId);
        return toRow(saved);
    }

    /**
     * Uploads a PDF and binds it as the lesson's main PDF content body.
     * When the lesson already has a main PDF the previous attachment row
     * + on-disk file are deleted first so disk usage stays bounded and
     * a single main PDF invariant holds.
     *
     * @throws IllegalArgumentException when the file is not a PDF or the
     *                                  upload validation fails
     */
    @Transactional
    public LessonAttachmentRow uploadMainPdf(Long classId, Long sectionId, Long lessonId,
                                             MultipartFile file, Long userId, Role role)
            throws IOException {
        classesService.getEditable(classId, userId, role);
        reorderService.verifySectionBelongsToClass(sectionId, classId);
        Lesson lesson = loadLesson(sectionId, lessonId);
        rejectCanonicalMainOverride(lesson);

        if (file == null || !"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("Chỉ chấp nhận tệp PDF cho bài giảng dạng PDF");
        }

        Long previousMainId = lesson.getPdfAttachmentId();

        // Save new PDF first so the CHECK constraint (content_type=PDF
        // requires pdf_attachment_id NOT NULL) is never violated.
        StoredAttachment stored = storage.store(file, lessonId);
        StorageTransactionLifecycle.deleteOnRollback(
                () -> storage.delete(stored.storedPath()));
        LessonAttachment row = new LessonAttachment(lessonId, stored.originalFilename(),
                stored.storedPath(), stored.mimeType(), stored.sizeBytes(), userId,
                null, ORIGIN_CLASS_PRIVATE);
        LessonAttachment saved = attachmentRepository.saveAndFlush(row);
        lesson.setPdfAttachmentId(saved.getId());
        lessonRepository.saveAndFlush(lesson);

        // Clean up old main PDF now that the new one is in place.
        if (previousMainId != null && !previousMainId.equals(saved.getId())) {
            attachmentRepository.findById(previousMainId).ifPresent(this::removeAttachmentRow);
        }
        activityWriter.write(lessonId, LessonActivity.TYPE_PDF_UPLOADED,
                "Tải lên PDF chính: " + saved.getOriginalFilename(), userId);
        return toRow(saved);
    }

    /**
     * Binds an owned DOCUMENT library asset (PDF MIME) as the lesson main PDF
     * without copying disk bytes, then switches the lesson content type to PDF
     * so student views render the PDF body (wizard path has no form save).
     */
    @Transactional
    public LessonAttachmentRow bindPdfFromLibrary(Long classId, Long sectionId, Long lessonId,
                                                   Long assetId, Long userId, Role role) {
        classesService.getEditable(classId, userId, role);
        reorderService.verifySectionBelongsToClass(sectionId, classId);
        Lesson lesson = loadLesson(sectionId, lessonId);
        rejectCanonicalMainOverride(lesson);
        LibraryAsset asset = libraryService.getOwnedAssetForUpdate(userId, assetId);
        if (!KIND_DOCUMENT.equals(asset.getKind())
                || !"application/pdf".equalsIgnoreCase(asset.getMimeType())) {
            throw new IllegalArgumentException(MSG_LIBRARY_BIND_NOT_PDF);
        }

        Long previousMainId = lesson.getPdfAttachmentId();
        LessonAttachment row = new LessonAttachment(
                lessonId, asset.getOriginalFilename(), asset.getStoredPath(),
                asset.getMimeType(), asset.getSizeBytes(), userId, asset.getId(),
                ORIGIN_CLASS_PRIVATE);
        LessonAttachment saved = attachmentRepository.saveAndFlush(row);
        // Required data must exist before type switch validates PDF shape.
        lesson.setPdfAttachmentId(saved.getId());
        lessonRepository.saveAndFlush(lesson);

        if (previousMainId != null && !previousMainId.equals(saved.getId())) {
            attachmentRepository.findById(previousMainId).ifPresent(this::removeAttachmentRow);
        }
        // Standalone bind (wizard) never hits lesson-form save — flip type here.
        contentTypeSwitcher.applyTo(lesson, typeSwitchForm(lesson, CONTENT_TYPE_PDF));
        activityWriter.write(lessonId, LessonActivity.TYPE_PDF_UPLOADED,
                "Gắn PDF từ kho: " + saved.getOriginalFilename(), userId);
        return toRow(saved);
    }

    /** Minimal form carrying only the target content type for type-switch. */
    private static LessonForm typeSwitchForm(Lesson lesson, String contentType) {
        return new LessonForm(lesson.getTitle(), lesson.getStatus(), null,
                contentType, lesson.getVideoUrl(), lesson.getVideoProvider());
    }

    /**
     * Creates a supplementary attachment row referencing an owned DOCUMENT
     * library asset (no disk copy).
     */
    @Transactional
    public LessonAttachmentRow bindAttachmentFromLibrary(Long classId, Long sectionId, Long lessonId,
                                                          Long assetId, Long userId, Role role) {
        // Personal sharing is intentionally narrower than general class edit:
        // immutable class owner or ADMIN only (no co-lecturer/leader widening).
        classesService.getOwnerManaged(classId, userId, role);
        reorderService.verifySectionBelongsToClass(sectionId, classId);
        // Global lock order for library-backed lesson mutations is asset then
        // lesson. Canonical snapshot refresh already locks its referenced
        // assets before flushing lesson changes; matching that order prevents
        // a direct share and a refresh from waiting on each other in reverse.
        LibraryAsset asset = libraryService.getOwnedAssetForUpdate(userId, assetId);
        if (!KIND_DOCUMENT.equals(asset.getKind())) {
            throw new IllegalArgumentException(MSG_LIBRARY_BIND_INVALID_KIND);
        }
        String ownedKey = libraryService.requireOwnedStorageKey(userId, asset);
        loadLessonForUpdate(sectionId, lessonId);
        if (attachmentRepository.existsByLessonIdAndLibraryAssetId(lessonId, asset.getId())) {
            throw new IllegalArgumentException(MSG_LIBRARY_ALREADY_BOUND);
        }
        LessonAttachment row = new LessonAttachment(
                lessonId, asset.getOriginalFilename(), ownedKey,
                asset.getMimeType(), asset.getSizeBytes(), userId, asset.getId(),
                ORIGIN_CLASS_PRIVATE);
        LessonAttachment saved = attachmentRepository.save(row);
        activityWriter.write(lessonId, LessonActivity.TYPE_ATTACHMENT_ADDED,
                "Gắn tệp từ kho: " + saved.getOriginalFilename(), userId);
        return toRow(saved);
    }

    /** Hard-deletes a single attachment row; skips disk delete when library-backed. */
    @Transactional
    public void delete(Long classId, Long sectionId, Long lessonId, Long attachmentId,
                       Long userId, Role role) {
        classesService.getEditable(classId, userId, role);
        reorderService.verifySectionBelongsToClass(sectionId, classId);
        Lesson lesson = loadLesson(sectionId, lessonId);
        LessonAttachment att = attachmentRepository.findByIdAndLessonId(attachmentId, lessonId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_ATTACHMENT_NOT_FOUND));
        if (att.isCanonicalTemplate()
                || (lesson.getSourceLessonTemplateId() != null
                    && attachmentId.equals(lesson.getPdfAttachmentId()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, MSG_CANONICAL_ATTACHMENT_LOCKED);
        }
        // If this is the main PDF, switch the lesson to RICHTEXT first
        // so clearing pdf_attachment_id doesn't violate the CHECK constraint.
        if (attachmentId.equals(lesson.getPdfAttachmentId())) {
            lesson.updateContent("");
            lesson.switchContentTypeTo(Lesson.CONTENT_TYPE_RICHTEXT);
            lessonRepository.saveAndFlush(lesson);
        } else {
            lessonRepository.clearPdfAttachmentId(attachmentId);
        }
        String removedName = att.getOriginalFilename();
        removeAttachmentRow(att);
        activityWriter.write(lessonId, LessonActivity.TYPE_ATTACHMENT_REMOVED,
                "Xoá tệp đính kèm: " + removedName, userId);
    }

    /**
     * Cascade cleanup invoked from {@link LessonsService#delete} BEFORE the
     * lesson is soft-deleted. Removes every attachment row; library blobs stay.
     */
    @Transactional
    public void deleteAllByLesson(Long lessonId) {
        List<LessonAttachment> rows = attachmentRepository.findByLessonIdOrderByUploadedAtAsc(lessonId);
        for (LessonAttachment att : rows) {
            // Clear FK before delete — lessons.pdf_attachment_id is RESTRICT with no ON DELETE clause.
            lessonRepository.clearPdfAttachmentId(att.getId());
            // Skip storage.delete for library-backed rows (shared blobs).
            removeAttachmentRow(att);
        }
    }

    /**
     * Removes one attachment row. Deletes the on-disk file only for one-off
     * uploads — library-backed rows only drop the DB reference.
     */
    public void removeAttachmentRow(LessonAttachment att) {
        if (att == null) return;
        if (!att.isLibraryBacked()) {
            StorageTransactionLifecycle.deleteAfterCommit(
                    () -> storage.delete(att.getStoredPath()));
        }
        attachmentRepository.delete(att);
    }

    /**
     * Authorizes a download request and returns the resolved file handle.
     * Lecturers/leaders/admins of the owning class always pass; an enrolled
     * student passes only when the parent lesson is {@code PUBLISHED}.
     */
    @Transactional(readOnly = true)
    public DownloadHandle download(Long lessonId, Long attachmentId, Long userId, Role role) {
        LessonAttachment att = attachmentRepository.findByIdAndLessonId(attachmentId, lessonId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_ATTACHMENT_NOT_FOUND));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_LESSON_NOT_FOUND));

        Long classId = resolveClassId(lesson);
        boolean allowed = isLecturerOrAbove(role)
                ? canEditClass(classId, userId, role)
                : isEnrolledStudentForPublishedLesson(classId, userId, lesson);
        if (!allowed) throw new AccessDeniedException(MSG_FORBIDDEN_FOR_CLASS);

        // Library-backed rows must still match their asset owner's prefix;
        // classic one-off rows retain the generic safe-key check.
        String key = att.isLibraryBacked()
                ? libraryService.requireReferencedStorageKey(
                        att.getLibraryAssetId(), att.getStoredPath())
                : StorageKeys.requireSafeKey(att.getStoredPath());
        return new DownloadHandle(key, att.getOriginalFilename(),
                att.getMimeType(), att.getSizeBytes());
    }

    /**
     * True when the attachment blob exists in object storage.
     * Shared with public-view so both entry points stay consistent.
     */
    boolean attachmentExists(LessonAttachment att) {
        try {
            String key = att.isLibraryBacked()
                    ? libraryService.requireReferencedStorageKey(
                            att.getLibraryAssetId(), att.getStoredPath())
                    : StorageKeys.requireSafeKey(att.getStoredPath());
            return objectStorage.exists(key);
        } catch (IllegalArgumentException | EntityNotFoundException ex) {
            return false;
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private Lesson loadLesson(Long sectionId, Long lessonId) {
        return lessonRepository.findByIdAndSectionId(lessonId, sectionId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_LESSON_NOT_FOUND));
    }

    private Lesson loadLessonForUpdate(Long sectionId, Long lessonId) {
        return lessonRepository.findByIdAndSectionIdForUpdate(lessonId, sectionId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_LESSON_NOT_FOUND));
    }

    private static void rejectCanonicalMainOverride(Lesson lesson) {
        if (lesson != null && lesson.getSourceLessonTemplateId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_CANONICAL_CONTENT_LOCKED);
        }
    }

    /** Traverses lesson → section → class id. */
    private Long resolveClassId(Lesson lesson) {
        Section section = sectionRepository.findById(lesson.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException(MSG_LESSON_NOT_FOUND));
        return section.getClassId();
    }

    private boolean canEditClass(Long classId, Long userId, Role role) {
        try {
            classesService.getEditable(classId, userId, role);
            return true;
        } catch (AccessDeniedException | EntityNotFoundException ex) {
            return false;
        }
    }

    private boolean isEnrolledStudentForPublishedLesson(Long classId, Long userId, Lesson lesson) {
        if (!LESSON_STATUS_PUBLISHED.equals(lesson.getStatus())) return false;
        Optional<Enrollment> enrollment = enrollmentRepository.findByUserIdAndClassId(userId, classId);
        return enrollment.filter(e -> Enrollment.STATUS_ACTIVE.equals(e.getStatus())).isPresent();
    }

    private static boolean isLecturerOrAbove(Role role) {
        return role == Role.LECTURER || role == Role.LEADER || role == Role.ADMIN;
    }

    private static List<LessonAttachmentRow> mapRows(List<LessonAttachment> rows) {
        List<LessonAttachmentRow> out = new ArrayList<>(rows.size());
        for (LessonAttachment a : rows) out.add(toRow(a));
        return out;
    }

    private static LessonAttachmentRow toRow(LessonAttachment a) {
        return new LessonAttachmentRow(a.getId(), a.getOriginalFilename(),
                a.getMimeType(), a.getSizeBytes(), a.getUploadedAt(),
                "/api/lessons/" + a.getLessonId() + "/attachments/" + a.getId() + "/download");
    }

    /** Tuple returned by {@link #download} so the controller can stream the file. */
    public record DownloadHandle(String storageKey, String originalFilename,
                                 String mimeType, long sizeBytes) {
    }
}
