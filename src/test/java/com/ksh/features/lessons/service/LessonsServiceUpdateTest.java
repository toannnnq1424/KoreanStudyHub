package com.ksh.features.lessons.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.lessons.dto.LessonDtos.LessonForm;
import com.ksh.features.lessons.dto.LessonDtos.LessonRow;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.library.service.LibraryService;
import com.ksh.features.upload.LessonVideoStorageService;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.ksh.common.IConstant.CONTENT_TYPE_RICHTEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonsServiceUpdateTest {

    private final LessonRepository lessonRepository = mock(LessonRepository.class);
    private final ClassesService classesService = mock(ClassesService.class);
    private final LessonActivityWriter activityWriter = mock(LessonActivityWriter.class);
    private final LessonsReorderService reorderService = mock(LessonsReorderService.class);
    private final LessonsUpdateHelper updateHelper = mock(LessonsUpdateHelper.class);
    private final LessonAttachmentsService attachmentsService = mock(LessonAttachmentsService.class);
    private final LessonContentTypeSwitcher contentTypeSwitcher =
            mock(LessonContentTypeSwitcher.class);
    private final LessonVideoStorageService videoStorageService =
            mock(LessonVideoStorageService.class);
    private final LibraryService libraryService = mock(LibraryService.class);

    private final LessonsService service =
            new LessonsService(
                    lessonRepository,
                    classesService,
                    activityWriter,
                    reorderService,
                    updateHelper,
                    attachmentsService,
                    contentTypeSwitcher,
                    videoStorageService,
                    libraryService
            );

    @Test
    void update_withChangedTitleAndContent_returnsUpdatedLessonRow() {
        Lesson lesson = lesson(30L, 20L, "Lesson 1", "<p>Old</p>");
        LessonForm form = new LessonForm(
                "Updated Lesson",
                "DRAFT",
                "<p>Updated</p>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(lessonRepository.findByIdAndSectionId(30L, 20L))
                .thenReturn(Optional.of(lesson));
        when(lessonRepository.save(lesson)).thenReturn(lesson);

        LessonRow result = service.update(100L, 20L, 30L, form, 5L, Role.LECTURER);

        assertThat(result.id()).isEqualTo(30L);
        assertThat(result.title()).isEqualTo("Updated Lesson");
        assertThat(lesson.getContentRichtext()).contains("<p>Updated</p>");
        verify(reorderService).verifySectionBelongsToClass(20L, 100L);
        verify(updateHelper).writeUpdateActivity(
                any(Lesson.class),
                any(),
                any(),
                any(),
                any(Boolean.class),
                any(Boolean.class),
                any(Boolean.class),
                any(),
                any(),
                any()
        );
        verify(updateHelper).applyStatusTransition(lesson, "DRAFT", 5L);
    }

    @Test
    void update_withUnsafeRichText_sanitizesContentBeforeSaving() {
        Lesson lesson = lesson(30L, 20L, "Lesson 1", "<p>Old</p>");
        LessonForm form = new LessonForm(
                "Lesson 1",
                "DRAFT",
                "<p>OK</p><script>x()</script>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(lessonRepository.findByIdAndSectionId(30L, 20L))
                .thenReturn(Optional.of(lesson));
        when(lessonRepository.save(lesson)).thenReturn(lesson);

        LessonRow result = service.update(100L, 20L, 30L, form, 5L, Role.LECTURER);

        assertThat(result.title()).isEqualTo("Lesson 1");
        assertThat(lesson.getContentRichtext()).contains("<p>OK</p>");
        assertThat(lesson.getContentRichtext()).doesNotContain("<script>");
    }

    @Test
    void update_withUnchangedFields_returnsLessonRowWithoutUpdateActivity() {
        Lesson lesson = lesson(30L, 20L, "Lesson 1", "<p>Same</p>");
        LessonForm form = new LessonForm(
                "Lesson 1",
                "DRAFT",
                "<p>Same</p>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(lessonRepository.findByIdAndSectionId(30L, 20L))
                .thenReturn(Optional.of(lesson));
        when(lessonRepository.save(lesson)).thenReturn(lesson);

        LessonRow result = service.update(100L, 20L, 30L, form, 5L, Role.LECTURER);

        assertThat(result.id()).isEqualTo(30L);
        assertThat(result.title()).isEqualTo("Lesson 1");
        verify(updateHelper, never()).writeUpdateActivity(
                any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()
        );
        verify(updateHelper).applyStatusTransition(lesson, "DRAFT", 5L);
    }

    @Test
    void update_withUnknownLesson_throwsEntityNotFoundException() {
        LessonForm form = validForm();

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(lessonRepository.findByIdAndSectionId(999L, 20L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(100L, 20L, 999L, form, 5L, Role.LECTURER))
                .isInstanceOf(EntityNotFoundException.class);

        verify(lessonRepository, never()).save(any());
    }

    @Test
    void update_withoutEditPermission_throwsAccessDeniedException() {
        LessonForm form = validForm();

        when(classesService.getEditable(100L, 6L, Role.LECTURER))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.update(100L, 20L, 30L, form, 6L, Role.LECTURER))
                .isInstanceOf(AccessDeniedException.class);

        verify(lessonRepository, never()).save(any());
    }

    private static LessonForm validForm() {
        return new LessonForm(
                "Updated Lesson",
                "DRAFT",
                "<p>Updated</p>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );
    }

    private static Lesson lesson(Long id, Long sectionId, String title, String contentHtml) {
        Lesson lesson = new Lesson(sectionId, title, (short) 0, 5L);
        lesson.updateContent(contentHtml);
        ReflectionTestUtils.setField(lesson, "id", id);
        return lesson;
    }

    private static ClassEntity classEntity(Long classId, Long lecturerId) {
        ClassEntity clazz = new ClassEntity(
                "Korean Basic 1",
                lecturerId,
                lecturerId,
                null,
                null,
                null,
                100
        );
        ReflectionTestUtils.setField(clazz, "id", classId);
        return clazz;
    }
}
