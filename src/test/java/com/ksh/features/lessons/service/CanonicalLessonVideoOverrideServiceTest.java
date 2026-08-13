package com.ksh.features.lessons.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.library.service.LibraryService;
import com.ksh.features.upload.LessonVideoStorageService;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalLessonVideoOverrideServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private ClassesService classesService;
    @Mock private LessonActivityWriter activityWriter;
    @Mock private LessonsReorderService reorderService;
    @Mock private LessonsUpdateHelper updateHelper;
    @Mock private LessonAttachmentsService attachmentsService;
    @Mock private LessonContentTypeSwitcher contentTypeSwitcher;
    @Mock private LessonVideoStorageService videoStorage;
    @Mock private LibraryService libraryService;
    @Mock private ClassEntity clazz;
    @Mock private Lesson lesson;

    @Test
    void canonical_main_video_override_is_conflict_before_asset_or_storage_lookup() {
        LessonsService service = new LessonsService(
                lessonRepository, classesService, activityWriter, reorderService,
                updateHelper, attachmentsService, contentTypeSwitcher,
                videoStorage, libraryService);
        when(classesService.getEditable(1L, 7L, Role.LECTURER)).thenReturn(clazz);
        when(lessonRepository.findByIdAndSectionId(3L, 2L))
                .thenReturn(Optional.of(lesson));
        when(lesson.getSourceLessonTemplateId()).thenReturn(55L);

        assertThatThrownBy(() -> service.bindVideoFromLibrary(
                1L, 2L, 3L, 11L, 7L, Role.LECTURER))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));

        verifyNoInteractions(libraryService, videoStorage);
    }
}
