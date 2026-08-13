package com.ksh.features.lessons.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonAttachment;
import com.ksh.entities.LibraryAsset;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.features.library.service.LibraryService;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.upload.LessonAttachmentStorageService;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Safe direct-bind contracts; every storage collaborator is mocked. */
@ExtendWith(MockitoExtension.class)
class PersonalLibrarySupplementaryBindServiceTest {

    @Mock private LessonAttachmentRepository attachmentRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private LessonAttachmentStorageService attachmentStorage;
    @Mock private ObjectStorage objectStorage;
    @Mock private LibraryService libraryService;
    @Mock private ClassesService classesService;
    @Mock private LessonsReorderService reorderService;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ClassRepository classRepository;
    @Mock private LessonActivityWriter activityWriter;
    @Mock private LessonContentTypeSwitcher contentTypeSwitcher;
    @Mock private ClassEntity clazz;
    @Mock private Lesson lesson;
    @Mock private LibraryAsset asset;

    private LessonAttachmentsService service;

    @BeforeEach
    void setUp() {
        service = new LessonAttachmentsService(
                attachmentRepository, lessonRepository, sectionRepository,
                attachmentStorage, objectStorage, libraryService, classesService,
                reorderService, enrollmentRepository, classRepository,
                activityWriter, contentTypeSwitcher);
    }

    @Test
    void owner_bind_creates_class_private_reference_without_storage_io() {
        when(classesService.getOwnerManaged(1L, 7L, Role.LECTURER)).thenReturn(clazz);
        when(lessonRepository.findByIdAndSectionIdForUpdate(3L, 2L))
                .thenReturn(Optional.of(lesson));
        when(libraryService.getOwnedAssetForUpdate(7L, 11L)).thenReturn(asset);
        when(asset.getId()).thenReturn(11L);
        when(asset.getKind()).thenReturn(LibraryAsset.KIND_DOCUMENT);
        when(asset.getOriginalFilename()).thenReturn("private.pdf");
        when(asset.getMimeType()).thenReturn("application/pdf");
        when(asset.getSizeBytes()).thenReturn(25L);
        when(libraryService.requireOwnedStorageKey(7L, asset))
                .thenReturn("library/7/private.pdf");
        when(attachmentRepository.existsByLessonIdAndLibraryAssetId(3L, 11L))
                .thenReturn(false);
        when(attachmentRepository.save(any(LessonAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.bindAttachmentFromLibrary(1L, 2L, 3L, 11L, 7L, Role.LECTURER);

        ArgumentCaptor<LessonAttachment> saved =
                ArgumentCaptor.forClass(LessonAttachment.class);
        verify(attachmentRepository).save(saved.capture());
        assertThat(saved.getValue().getOriginScope())
                .isEqualTo(LessonAttachment.ORIGIN_CLASS_PRIVATE);
        assertThat(saved.getValue().getStoredPath()).isEqualTo("library/7/private.pdf");
        InOrder lockOrder = inOrder(
                classesService, reorderService, libraryService,
                lessonRepository, attachmentRepository);
        lockOrder.verify(classesService)
                .getOwnerManaged(1L, 7L, Role.LECTURER);
        lockOrder.verify(reorderService)
                .verifySectionBelongsToClass(2L, 1L);
        lockOrder.verify(libraryService)
                .getOwnedAssetForUpdate(7L, 11L);
        lockOrder.verify(lessonRepository)
                .findByIdAndSectionIdForUpdate(3L, 2L);
        lockOrder.verify(attachmentRepository)
                .existsByLessonIdAndLibraryAssetId(3L, 11L);
        verifyNoInteractions(attachmentStorage, objectStorage);
    }

    @Test
    void duplicate_asset_is_rejected_under_lesson_lock_without_storage_io() {
        when(classesService.getOwnerManaged(1L, 7L, Role.LECTURER)).thenReturn(clazz);
        when(lessonRepository.findByIdAndSectionIdForUpdate(3L, 2L))
                .thenReturn(Optional.of(lesson));
        when(libraryService.getOwnedAssetForUpdate(7L, 11L)).thenReturn(asset);
        when(asset.getId()).thenReturn(11L);
        when(asset.getKind()).thenReturn(LibraryAsset.KIND_DOCUMENT);
        when(libraryService.requireOwnedStorageKey(7L, asset))
                .thenReturn("library/7/private.pdf");
        when(attachmentRepository.existsByLessonIdAndLibraryAssetId(3L, 11L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.bindAttachmentFromLibrary(
                1L, 2L, 3L, 11L, 7L, Role.LECTURER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã được gắn");

        verify(attachmentRepository, never()).save(any());
        verifyNoInteractions(attachmentStorage, objectStorage);
    }

    @Test
    void cross_owner_asset_is_not_found_and_never_reaches_storage() {
        when(classesService.getOwnerManaged(1L, 7L, Role.LECTURER)).thenReturn(clazz);
        when(libraryService.getOwnedAssetForUpdate(7L, 99L))
                .thenThrow(new EntityNotFoundException("Không tìm thấy học liệu"));

        assertThatThrownBy(() -> service.bindAttachmentFromLibrary(
                1L, 2L, 3L, 99L, 7L, Role.LECTURER))
                .isInstanceOf(EntityNotFoundException.class);

        verify(lessonRepository, never())
                .findByIdAndSectionIdForUpdate(3L, 2L);
        verifyNoInteractions(attachmentStorage, objectStorage);
    }

    @Test
    void canonical_main_pdf_override_is_conflict_before_asset_or_storage_lookup() {
        when(classesService.getEditable(1L, 7L, Role.LECTURER)).thenReturn(clazz);
        when(lessonRepository.findByIdAndSectionId(3L, 2L))
                .thenReturn(Optional.of(lesson));
        when(lesson.getSourceLessonTemplateId()).thenReturn(55L);

        assertThatThrownBy(() -> service.bindPdfFromLibrary(
                1L, 2L, 3L, 11L, 7L, Role.LECTURER))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));

        verifyNoInteractions(libraryService, attachmentStorage, objectStorage);
    }
}
