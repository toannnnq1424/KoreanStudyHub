package com.ksh.features.lessons.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonActivity;
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

import static com.ksh.common.IConstant.CONTENT_TYPE_RICHTEXT;
import static com.ksh.common.IConstant.LESSON_STATUS_PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonsServiceCreateTest {

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
    void create_withDraftRichText_returnsLessonRowAndWritesCreatedActivity() {
        LessonForm form = new LessonForm(
                "Lesson 1",
                "DRAFT",
                "<p>Hello</p>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(lessonRepository.findMaxDisplayOrder(20L)).thenReturn((short) 0);
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> {
                    Lesson lesson = invocation.getArgument(0);
                    ReflectionTestUtils.setField(lesson, "id", 30L);
                    return lesson;
                });

        LessonRow result = service.create(100L, 20L, form, 5L, Role.LECTURER);

        assertThat(result.id()).isEqualTo(30L);
        assertThat(result.title()).isEqualTo("Lesson 1");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.displayOrder()).isEqualTo((short) 1);
        assertThat(result.contentType()).isEqualTo(CONTENT_TYPE_RICHTEXT);

        verify(reorderService).lockSectionForUpdate(20L, 100L);
        verify(activityWriter).write(
                eq(30L),
                eq(LessonActivity.TYPE_CREATED),
                eq("Tạo bài giảng Lesson 1"),
                eq(5L)
        );
    }

    @Test
    void create_withPublishedStatus_writesCreatedAndPublishedActivities() {
        LessonForm form = new LessonForm(
                "Lesson 2",
                LESSON_STATUS_PUBLISHED,
                "<p>Hello</p>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(lessonRepository.findMaxDisplayOrder(20L)).thenReturn((short) 1);
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> {
                    Lesson lesson = invocation.getArgument(0);
                    ReflectionTestUtils.setField(lesson, "id", 31L);
                    return lesson;
                });

        LessonRow result = service.create(100L, 20L, form, 5L, Role.LECTURER);

        assertThat(result.status()).isEqualTo(LESSON_STATUS_PUBLISHED);
        verify(activityWriter).write(
                eq(31L),
                eq(LessonActivity.TYPE_CREATED),
                eq("Tạo bài giảng Lesson 2"),
                eq(5L)
        );
        verify(activityWriter).write(
                eq(31L),
                eq(LessonActivity.TYPE_PUBLISHED),
                eq("Xuất bản bài giảng Lesson 2"),
                eq(5L)
        );
    }

    @Test
    void create_withUnsafeRichText_sanitizesContentBeforeSaving() {
        LessonForm form = new LessonForm(
                "Lesson 3",
                "DRAFT",
                "<p>OK</p><script>x()</script>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(lessonRepository.findMaxDisplayOrder(20L)).thenReturn((short) 0);
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> {
                    Lesson lesson = invocation.getArgument(0);
                    ReflectionTestUtils.setField(lesson, "id", 32L);
                    assertThat(lesson.getContentRichtext()).contains("<p>OK</p>");
                    assertThat(lesson.getContentRichtext()).doesNotContain("<script>");
                    return lesson;
                });

        LessonRow result = service.create(100L, 20L, form, 5L, Role.LECTURER);

        assertThat(result.title()).isEqualTo("Lesson 3");
    }

    @Test
    void create_withInvalidSection_throwsExceptionAndDoesNotSaveLesson() {
        LessonForm form = new LessonForm(
                "Lesson 1",
                "DRAFT",
                "<p>Hello</p>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 5L, Role.LECTURER))
                .thenReturn(classEntity(100L, 5L));
        when(reorderService.lockSectionForUpdate(99L, 100L))
                .thenThrow(new EntityNotFoundException("Section not found"));

        assertThatThrownBy(() -> service.create(100L, 99L, form, 5L, Role.LECTURER))
                .isInstanceOf(EntityNotFoundException.class);

        verify(lessonRepository, never()).save(any());
        verify(activityWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    void create_withoutEditPermission_throwsAccessDeniedException() {
        LessonForm form = new LessonForm(
                "Lesson 1",
                "DRAFT",
                "<p>Hello</p>",
                CONTENT_TYPE_RICHTEXT,
                null,
                null
        );

        when(classesService.getEditable(100L, 6L, Role.LECTURER))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.create(100L, 20L, form, 6L, Role.LECTURER))
                .isInstanceOf(AccessDeniedException.class);

        verify(lessonRepository, never()).save(any());
        verify(activityWriter, never()).write(any(), any(), any(), any());
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