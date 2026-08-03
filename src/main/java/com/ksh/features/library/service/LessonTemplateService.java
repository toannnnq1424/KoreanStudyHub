package com.ksh.features.library.service;

import com.ksh.common.HtmlSanitizer;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonActivity;
import com.ksh.entities.LessonAttachment;
import com.ksh.entities.LessonTemplate;
import com.ksh.entities.LessonTemplateAttachment;
import com.ksh.entities.LibraryAsset;
import com.ksh.entities.Section;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.features.lessons.service.LessonActivityWriter;
import com.ksh.features.lessons.service.LessonsReorderService;
import com.ksh.features.lessons.service.SectionsService;
import com.ksh.features.lessons.support.VimeoEmbedUrl;
import com.ksh.features.lessons.support.YouTubeEmbedUrl;
import com.ksh.features.classes.dto.ClassesDtos.ClassRow;
import com.ksh.features.library.dto.LibraryDtos.AttachTargetClassRow;
import com.ksh.features.library.dto.LibraryDtos.LessonCloneResult;
import com.ksh.features.library.dto.LibraryDtos.MaterialOption;
import com.ksh.features.library.dto.LibraryDtos.LessonTemplatePageView;
import com.ksh.features.library.dto.LibraryDtos.LessonTemplateRow;
import com.ksh.features.library.dto.LibraryDtos.SubjectContext;
import com.ksh.features.library.dto.LessonTemplateForm;
import com.ksh.features.library.repository.LessonTemplateAttachmentRepository;
import com.ksh.features.library.repository.LessonTemplateRepository;
import com.ksh.features.library.repository.LibraryAssetRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.io.IOException;

import static com.ksh.common.IConstant.CONTENT_TYPE_PDF;
import static com.ksh.common.IConstant.CONTENT_TYPE_RICHTEXT;
import static com.ksh.common.IConstant.CONTENT_TYPE_VIDEO;
import static com.ksh.common.IConstant.DEFAULT_LIBRARY_PAGE_SIZE;
import static com.ksh.common.IConstant.MAX_LIBRARY_PAGE_SIZE;
import static com.ksh.common.IConstant.MSG_TEMPLATE_BODY_INCOMPLETE;
import static com.ksh.common.IConstant.MSG_TEMPLATE_NOT_FOUND;
import static com.ksh.common.IConstant.VIDEO_PROVIDER_UPLOAD;
import static com.ksh.common.IConstant.VIDEO_PROVIDER_VIMEO;
import static com.ksh.common.IConstant.VIDEO_PROVIDER_YOUTUBE;
import static com.ksh.entities.LibraryAsset.KIND_DOCUMENT;
import static com.ksh.entities.LibraryAsset.KIND_VIDEO;
import static com.ksh.common.IConstant.MSG_STORAGE_UPLOAD_FAILED;

/**
 * Canonical subject lessons authored in Library and distributed as published
 * snapshots to one or more classes with the same subject code.
 */
@Service
public class LessonTemplateService {

    private final LessonTemplateRepository templateRepository;
    private final LessonTemplateAttachmentRepository templateAttachmentRepository;
    private final LibraryAssetRepository assetRepository;
    private final LibraryService libraryService;
    private final LessonRepository lessonRepository;
    private final LessonAttachmentRepository attachmentRepository;
    private final SectionRepository sectionRepository;
    private final ClassRepository classRepository;
    private final LessonsReorderService reorderService;
    private final SectionsService sectionsService;
    private final ClassesService classesService;
    private final LessonActivityWriter activityWriter;
    private final LibrarySubjectResolver subjectResolver;

    public LessonTemplateService(LessonTemplateRepository templateRepository,
                                 LessonTemplateAttachmentRepository templateAttachmentRepository,
                                 LibraryAssetRepository assetRepository,
                                 LibraryService libraryService,
                                 LessonRepository lessonRepository,
                                 LessonAttachmentRepository attachmentRepository,
                                 SectionRepository sectionRepository,
                                 ClassRepository classRepository,
                                 LessonsReorderService reorderService,
                                 SectionsService sectionsService,
                                 ClassesService classesService,
                                 LessonActivityWriter activityWriter,
                                 LibrarySubjectResolver subjectResolver) {
        this.templateRepository = templateRepository;
        this.templateAttachmentRepository = templateAttachmentRepository;
        this.assetRepository = assetRepository;
        this.libraryService = libraryService;
        this.lessonRepository = lessonRepository;
        this.attachmentRepository = attachmentRepository;
        this.sectionRepository = sectionRepository;
        this.classRepository = classRepository;
        this.reorderService = reorderService;
        this.sectionsService = sectionsService;
        this.classesService = classesService;
        this.activityWriter = activityWriter;
        this.subjectResolver = subjectResolver;
    }

    /** Dropdown options: every class owned by the lecturer (capped). */
    private List<AttachTargetClassRow> listOwnedClassOptions(Long lecturerId, Role role,
                                                             Long subjectId) {
        Page<ClassRow> owned = classesService.listForUser(
                lecturerId, role, PageRequest.of(0, MAX_LIBRARY_PAGE_SIZE));
        List<AttachTargetClassRow> options = new ArrayList<>(owned.getNumberOfElements());
        for (ClassRow row : owned.getContent()) {
            classRepository.findById(row.id())
                    .filter(clazz -> subjectId.equals(clazz.getDepartmentId()))
                    .filter(clazz -> !ClassEntity.STATUS_ARCHIVED.equals(clazz.getStatus()))
                    .ifPresent(clazz -> options.add(
                            new AttachTargetClassRow(row.id(), row.name(), row.code())));
        }
        return options;
    }

    /** Saved templates only (secondary list / management). */
    @Transactional(readOnly = true)
    public LessonTemplatePageView list(Long ownerId, Role role, String q, int page, int size) {
        Department subject = subjectResolver.require(ownerId, role);
        PageRequest pr = pageRequest(page, size);
        String qNorm = normalizeQ(q);
        Page<LessonTemplate> result = templateRepository.searchOwnedSubject(
                ownerId, subject.getId(), qNorm, pr);
        Page<LessonTemplateRow> rows = result.map(t -> toRow(t, subject.getCode(),
                templateAttachmentRepository.findByTemplateIdOrderByDisplayOrderAsc(t.getId()).size()));
        long templateCount = templateRepository.countByOwnerIdAndSubjectId(ownerId, subject.getId());
        return new LessonTemplatePageView(
                rows,
                qNorm == null ? "" : qNorm,
                subject.getCode(),
                subject.getName(),
                listOwnedClassOptions(ownerId, role, subject.getId()),
                templateCount);
    }

    @Transactional(readOnly = true)
    public LessonTemplateForm loadForm(Long ownerId, Role role, Long templateId) {
        Department subject = subjectResolver.require(ownerId, role);
        LessonTemplateForm form = new LessonTemplateForm();
        if (templateId == null) {
            return form;
        }
        LessonTemplate template = getOwned(ownerId, templateId);
        requireTemplateSubject(template, subject.getId());
        form.setId(template.getId());
        form.setChapterTitle(template.getChapterTitle());
        form.setTitle(template.getTitle());
        form.setContentType(template.getContentType());
        form.setContentRichtext(template.getContentRichtext());
        form.setPdfLibraryAssetId(template.getPdfLibraryAssetId());
        form.setVideoProvider(template.getVideoProvider());
        form.setVideoUrl(template.getVideoUrl());
        form.setVideoLibraryAssetId(template.getVideoLibraryAssetId());
        form.setMaterialAssetIds(templateAttachmentRepository
                .findByTemplateIdOrderByDisplayOrderAsc(templateId).stream()
                .map(LessonTemplateAttachment::getLibraryAssetId).toList());
        return form;
    }

    @Transactional(readOnly = true)
    public List<MaterialOption> materialOptions(Long ownerId) {
        return assetRepository.findByOwnerIdOrderByTitleAsc(ownerId).stream()
                .map(asset -> new MaterialOption(
                        asset.getId(), asset.getTitle(), asset.getKind(), asset.getMimeType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectContext subjectContext(Long ownerId, Role role) {
        Department subject = subjectResolver.require(ownerId, role);
        return new SubjectContext(subject.getId(), subject.getCode(), subject.getName());
    }

    @Transactional
    public LessonTemplateRow saveForm(Long ownerId, Role role, LessonTemplateForm form) {
        Department subject = subjectResolver.require(ownerId, role);
        String chapter = requireText(form.getChapterTitle(), "Tên chương không được để trống");
        String title = requireText(form.getTitle(), "Tên bài học không được để trống");
        String type = form.getContentType();
        Lesson.validateContentType(type);
        ingestInlineUploads(ownerId, form);

        LessonTemplate template;
        if (form.getId() == null) {
            template = new LessonTemplate(ownerId, subject.getId(), chapter, title, type);
        } else {
            template = getOwned(ownerId, form.getId());
            requireTemplateSubject(template, subject.getId());
            template.updateAuthoring(chapter, title, type);
        }
        applyFormBody(template, form, ownerId);
        LessonTemplate saved = templateRepository.saveAndFlush(template);

        templateAttachmentRepository.deleteByTemplateId(saved.getId());
        int order = 0;
        List<Long> selectedMaterials = form.getMaterialAssetIds() == null
                ? List.of() : form.getMaterialAssetIds();
        for (Long assetId : new LinkedHashSet<>(selectedMaterials)) {
            if (assetId == null) continue;
            LibraryAsset asset = libraryService.getOwnedAssetForUpdate(ownerId, assetId);
            if (!KIND_DOCUMENT.equals(asset.getKind())) {
                throw new IllegalArgumentException("Materials chỉ chấp nhận tài liệu");
            }
            templateAttachmentRepository.save(new LessonTemplateAttachment(
                    saved.getId(), asset.getId(), asset.getOriginalFilename(),
                    asset.getMimeType(), asset.getSizeBytes(), order++));
        }
        return toRow(saved, subject.getCode(), order);
    }

    private void ingestInlineUploads(Long ownerId, LessonTemplateForm form) {
        try {
            if (form.getPdfUpload() != null && !form.getPdfUpload().isEmpty()) {
                form.setPdfLibraryAssetId(
                        libraryService.upload(ownerId, form.getPdfUpload(), KIND_DOCUMENT).id());
            }
            if (form.getVideoUpload() != null && !form.getVideoUpload().isEmpty()) {
                form.setVideoLibraryAssetId(
                        libraryService.upload(ownerId, form.getVideoUpload(), KIND_VIDEO).id());
                form.setVideoProvider(VIDEO_PROVIDER_UPLOAD);
            }
            LinkedHashSet<Long> materialIds = new LinkedHashSet<>(
                    form.getMaterialAssetIds() == null ? List.of() : form.getMaterialAssetIds());
            if (form.getMaterialUploads() != null) {
                for (var upload : form.getMaterialUploads()) {
                    if (upload != null && !upload.isEmpty()) {
                        materialIds.add(libraryService.upload(ownerId, upload, KIND_DOCUMENT).id());
                    }
                }
            }
            form.setMaterialAssetIds(new ArrayList<>(materialIds));
        } catch (IOException exception) {
            throw new IllegalStateException(MSG_STORAGE_UPLOAD_FAILED, exception);
        }
    }

    @Transactional
    public List<LessonCloneResult> distribute(Long templateId, List<Long> classIds,
                                               Long userId, Role role) {
        if (classIds == null || classIds.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một lớp");
        }
        Department subject = subjectResolver.require(userId, role);
        LessonTemplate template = getOwned(userId, templateId);
        requireTemplateSubject(template, subject.getId());
        List<LessonCloneResult> results = new ArrayList<>();
        for (Long classId : new LinkedHashSet<>(classIds)) {
            if (classId == null) continue;
            ClassEntity clazz = classesService.getEditable(classId, userId, role);
            if (!subject.getId().equals(clazz.getDepartmentId())
                    || ClassEntity.STATUS_ARCHIVED.equals(clazz.getStatus())) {
                throw new IllegalArgumentException("Chỉ được phân phối tới lớp cùng mã môn đang sử dụng");
            }
            Section section = sectionRepository.findByClassIdOrderByDisplayOrderAsc(classId).stream()
                    .filter(row -> row.getTitle().equalsIgnoreCase(template.getChapterTitle()))
                    .findFirst()
                    .orElseGet(() -> {
                        Long createdId = sectionsService.create(classId, template.getChapterTitle(),
                                userId, role).id();
                        return sectionRepository.findByIdAndClassId(createdId, classId)
                                .orElseThrow(() -> new EntityNotFoundException("Chương không tồn tại"));
                    });
            if (lessonRepository.findFirstBySectionIdAndTitleIgnoreCase(
                    section.getId(), template.getTitle()).isPresent()) {
                throw new IllegalArgumentException(
                        "Lớp " + clazz.getName() + " đã có bài học cùng tên trong chương này");
            }
            LessonCloneResult result = snapshotTemplateToSection(
                    template, classId, section.getId(), userId);
            Lesson distributed = lessonRepository.findById(result.lessonId())
                    .orElseThrow(() -> new EntityNotFoundException(MSG_TEMPLATE_NOT_FOUND));
            distributed.publish();
            lessonRepository.save(distributed);
            activityWriter.write(distributed.getId(), LessonActivity.TYPE_PUBLISHED,
                    "Phân phối từ Library: " + distributed.getTitle(), userId);
            results.add(result);
        }
        return results;
    }

    /** Soft-deletes an owned template (attachment rows stay for FK integrity). */
    @Transactional
    public void softDelete(Long ownerId, Long templateId) {
        LessonTemplate template = getOwned(ownerId, templateId);
        template.markDeleted();
        templateRepository.save(template);
    }

    /** Materializes one canonical Library lesson as a class-owned snapshot. */
    private LessonCloneResult snapshotTemplateToSection(LessonTemplate template, Long classId,
                                                        Long sectionId, Long userId) {
        reorderService.lockSectionForUpdate(sectionId, classId);

        Lesson lesson = materializeDraft(sectionId, template.getTitle(),
                template.getContentType(), userId);
        applyTemplateBodyToLesson(lesson, template, userId);
        Lesson saved = lessonRepository.saveAndFlush(lesson);

        List<LessonTemplateAttachment> extras =
                templateAttachmentRepository.findByTemplateIdOrderByDisplayOrderAsc(template.getId());
        for (LessonTemplateAttachment extra : extras) {
            LibraryAsset asset = libraryService.getOwnedAssetForUpdate(
                    userId, extra.getLibraryAssetId());
            LessonAttachment row = new LessonAttachment(
                    saved.getId(), asset.getOriginalFilename(), asset.getStoredPath(),
                    asset.getMimeType(), asset.getSizeBytes(), userId, asset.getId());
            attachmentRepository.save(row);
        }

        activityWriter.write(saved.getId(), LessonActivity.TYPE_CREATED,
                "Tạo bài giảng (clone từ mẫu): " + saved.getTitle(), userId);
        return new LessonCloneResult(saved.getId(), classId, sectionId, saved.getTitle());
    }

    // ── Body mapping ────────────────────────────────────────────────────

    private void applyFormBody(LessonTemplate template, LessonTemplateForm form, Long ownerId) {
        String type = template.getContentType();
        if (CONTENT_TYPE_RICHTEXT.equals(type)) {
            String html = form.getContentRichtext() == null ? "" : form.getContentRichtext();
            template.setContentRichtext(HtmlSanitizer.sanitize(html));
            return;
        }
        if (CONTENT_TYPE_PDF.equals(type)) {
            if (form.getPdfLibraryAssetId() == null) {
                throw new IllegalArgumentException("Vui lòng chọn PDF chính");
            }
            LibraryAsset asset = libraryService.getOwnedAssetForUpdate(
                    ownerId, form.getPdfLibraryAssetId());
            if (!KIND_DOCUMENT.equals(asset.getKind())
                    || !"application/pdf".equalsIgnoreCase(asset.getMimeType())) {
                throw new IllegalArgumentException("PDF chính không hợp lệ");
            }
            template.setPdfLibraryAssetId(asset.getId());
            return;
        }
        if (CONTENT_TYPE_VIDEO.equals(type)) {
            String provider = form.getVideoProvider() == null
                    ? "" : form.getVideoProvider().trim().toUpperCase();
            if (VIDEO_PROVIDER_UPLOAD.equals(provider)) {
                if (form.getVideoLibraryAssetId() == null) {
                    throw new IllegalArgumentException("Vui lòng chọn video trong Library");
                }
                LibraryAsset asset = libraryService.getOwnedAssetForUpdate(
                        ownerId, form.getVideoLibraryAssetId());
                if (!KIND_VIDEO.equals(asset.getKind())) {
                    throw new IllegalArgumentException("Video đã chọn không hợp lệ");
                }
                template.setVideoProvider(VIDEO_PROVIDER_UPLOAD);
                template.setVideoLibraryAssetId(asset.getId());
                template.setVideoUrl(asset.getStoredPath());
                return;
            }
            String videoUrl = form.getVideoUrl() == null ? "" : form.getVideoUrl().trim();
            boolean validExternalUrl = VIDEO_PROVIDER_YOUTUBE.equals(provider)
                    ? YouTubeEmbedUrl.matches(videoUrl)
                    : VIDEO_PROVIDER_VIMEO.equals(provider) && VimeoEmbedUrl.matches(videoUrl);
            if (validExternalUrl) {
                template.setVideoProvider(provider);
                template.setVideoUrl(videoUrl);
                return;
            }
            throw new IllegalArgumentException("Vui lòng cấu hình nguồn video hợp lệ");
        }
        throw new IllegalArgumentException(MSG_TEMPLATE_BODY_INCOMPLETE);
    }

    private void applyTemplateBodyToLesson(Lesson lesson, LessonTemplate template, Long userId) {
        String type = template.getContentType();
        if (CONTENT_TYPE_RICHTEXT.equals(type)) {
            lesson.switchContentTypeTo(CONTENT_TYPE_RICHTEXT);
            String html = template.getContentRichtext() == null ? "" : template.getContentRichtext();
            lesson.updateContent(HtmlSanitizer.sanitize(html));
            return;
        }
        if (CONTENT_TYPE_PDF.equals(type)) {
            LibraryAsset asset = libraryService.getOwnedAssetForUpdate(
                    userId, template.getPdfLibraryAssetId());
            // Attachment row first so pdf_attachment_id CHECK can pass after type switch.
            LessonAttachment row = new LessonAttachment(
                    lesson.getId(), asset.getOriginalFilename(), asset.getStoredPath(),
                    asset.getMimeType(), asset.getSizeBytes(), userId, asset.getId());
            LessonAttachment savedAtt = attachmentRepository.saveAndFlush(row);
            lesson.setPdfAttachmentId(savedAtt.getId());
            lesson.switchContentTypeTo(CONTENT_TYPE_PDF);
            // switchContentTypeTo nulls pdf_attachment_id — restore after switch.
            lesson.setPdfAttachmentId(savedAtt.getId());
            return;
        }
        if (CONTENT_TYPE_VIDEO.equals(type)) {
            applyTemplateVideoToLesson(lesson, template, userId);
            return;
        }
        throw new IllegalArgumentException(MSG_TEMPLATE_BODY_INCOMPLETE);
    }

    private void applyTemplateVideoToLesson(Lesson lesson, LessonTemplate template, Long userId) {
        String provider = template.getVideoProvider();
        if (VIDEO_PROVIDER_YOUTUBE.equals(provider) || VIDEO_PROVIDER_VIMEO.equals(provider)) {
            lesson.switchContentTypeTo(CONTENT_TYPE_VIDEO);
            lesson.setVideoProvider(provider);
            lesson.setVideoUrl(template.getVideoUrl());
            return;
        }
        if (VIDEO_PROVIDER_UPLOAD.equals(provider)) {
            LibraryAsset asset = libraryService.getOwnedAssetForUpdate(
                    userId, template.getVideoLibraryAssetId());
            lesson.switchContentTypeTo(CONTENT_TYPE_VIDEO);
            lesson.setVideoProvider(VIDEO_PROVIDER_UPLOAD);
            lesson.setVideoLibraryAssetId(asset.getId());
            lesson.setVideoUrl(asset.getStoredPath());
            return;
        }
        throw new IllegalArgumentException(MSG_TEMPLATE_BODY_INCOMPLETE);
    }

    private Lesson materializeDraft(Long sectionId, String title, String contentType, Long userId) {
        short nextOrder = (short) (lessonRepository.findMaxDisplayOrder(sectionId) + 1);
        Lesson lesson = new Lesson(sectionId, title, nextOrder, userId);
        // Constructor defaults RICHTEXT+""; PDF/VIDEO body filled after first save
        // so we have a lesson id for attachment FKs.
        Lesson saved = lessonRepository.saveAndFlush(lesson);
        if (!CONTENT_TYPE_RICHTEXT.equals(contentType)) {
            // Keep as RICHTEXT empty until body is applied — avoids CHECK violation.
            return saved;
        }
        return saved;
    }

    private LessonTemplate getOwned(Long ownerId, Long templateId) {
        return templateRepository.findByIdAndOwnerId(templateId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_TEMPLATE_NOT_FOUND));
    }

    private static void requireTemplateSubject(LessonTemplate template, Long subjectId) {
        if (!subjectId.equals(template.getSubjectId())) {
            throw new EntityNotFoundException(MSG_TEMPLATE_NOT_FOUND);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static LessonTemplateRow toRow(LessonTemplate t, String subjectCode,
                                           int attachmentCount) {
        return new LessonTemplateRow(
                t.getId(), subjectCode, t.getChapterTitle(), t.getTitle(), t.getContentType(),
                t.getUpdatedAt(), attachmentCount);
    }

    private static PageRequest pageRequest(int page, int size) {
        int p = Math.max(page, 0);
        int s = size <= 0 ? DEFAULT_LIBRARY_PAGE_SIZE
                : Math.min(size, MAX_LIBRARY_PAGE_SIZE);
        return PageRequest.of(p, s);
    }

    private static String normalizeQ(String q) {
        if (q == null) return null;
        String t = q.trim();
        return t.isEmpty() ? null : t;
    }
}
