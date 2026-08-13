package com.ksh.features.library.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.entities.LibraryAsset;
import com.ksh.entities.Section;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalLibraryClassTargetServiceTest {

    @Mock private LibraryService libraryService;
    @Mock private ClassRepository classRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private LibraryAsset asset;
    @Mock private ClassEntity clazz;
    @Mock private Section section;
    @Mock private Lesson lesson;

    @Test
    void lecturer_target_tree_uses_three_batched_reads_and_never_mutates() {
        PersonalLibraryClassTargetService service = new PersonalLibraryClassTargetService(
                libraryService, classRepository, sectionRepository, lessonRepository);
        when(libraryService.getOwnedAsset(7L, 11L)).thenReturn(asset);
        when(asset.getId()).thenReturn(11L);
        when(asset.getKind()).thenReturn(LibraryAsset.KIND_DOCUMENT);
        when(libraryService.requireOwnedStorageKey(7L, asset))
                .thenReturn("library/7/private.pdf");
        when(classRepository.findAllByLecturerIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(clazz));
        when(clazz.getId()).thenReturn(1L);
        when(clazz.getName()).thenReturn("Lớp riêng");
        when(clazz.getStatus()).thenReturn(ClassEntity.STATUS_ACTIVE);
        when(section.getClassId()).thenReturn(1L);
        when(sectionRepository.findByClassIdInOrderByClassIdAscDisplayOrderAsc(List.of(1L)))
                .thenReturn(List.of(section));
        when(section.getId()).thenReturn(2L);
        when(section.getTitle()).thenReturn("Chương 1");
        when(lesson.getSectionId()).thenReturn(2L);
        when(lessonRepository.findBySectionIdInOrderBySectionIdAscDisplayOrderAsc(List.of(2L)))
                .thenReturn(List.of(lesson));
        when(lesson.getId()).thenReturn(3L);
        when(lesson.getTitle()).thenReturn("Bài 1");
        when(lesson.getStatus()).thenReturn(Lesson.STATUS_PUBLISHED);
        when(lesson.getSourceLessonTemplateId()).thenReturn(55L);

        var targets = service.targets(7L, Role.LECTURER, 11L);

        assertThat(targets.assetId()).isEqualTo(11L);
        assertThat(targets.classes()).singleElement().satisfies(classTarget -> {
            assertThat(classTarget.id()).isEqualTo(1L);
            assertThat(classTarget.sections()).singleElement().satisfies(sectionTarget ->
                    assertThat(sectionTarget.lessons()).singleElement().satisfies(lessonTarget ->
                            assertThat(lessonTarget.canonicalSnapshot()).isTrue()));
        });
        verify(classRepository, never()).findAllByOrderByCreatedAtDesc();
        verify(classRepository, times(1)).findAllByLecturerIdOrderByCreatedAtDesc(7L);
        verify(sectionRepository, times(1))
                .findByClassIdInOrderByClassIdAscDisplayOrderAsc(List.of(1L));
        verify(lessonRepository, times(1))
                .findBySectionIdInOrderBySectionIdAscDisplayOrderAsc(List.of(2L));
        verify(sectionRepository, never()).findByClassIdOrderByDisplayOrderAsc(1L);
        verify(lessonRepository, never()).findBySectionIdOrderByDisplayOrderAsc(2L);
        verify(classRepository, never()).save(clazz);
        verify(sectionRepository, never()).save(section);
        verify(lessonRepository, never()).save(lesson);
    }

    @Test
    void target_queries_split_large_id_sets_into_bounded_batches() {
        PersonalLibraryClassTargetService service = new PersonalLibraryClassTargetService(
                libraryService, classRepository, sectionRepository, lessonRepository);
        when(libraryService.getOwnedAsset(7L, 11L)).thenReturn(asset);
        when(asset.getId()).thenReturn(11L);
        when(asset.getKind()).thenReturn(LibraryAsset.KIND_DOCUMENT);
        when(libraryService.requireOwnedStorageKey(7L, asset))
                .thenReturn("library/7/private.pdf");

        int classCount = PersonalLibraryClassTargetService.TARGET_QUERY_BATCH_SIZE + 1;
        List<ClassEntity> classes = new ArrayList<>();
        IntStream.rangeClosed(1, classCount).forEach(index -> {
            ClassEntity candidate = mock(ClassEntity.class);
            when(candidate.getId()).thenReturn((long) index);
            when(candidate.getName()).thenReturn("Lớp " + index);
            when(candidate.getStatus()).thenReturn(ClassEntity.STATUS_ACTIVE);
            classes.add(candidate);
        });
        when(classRepository.findAllByLecturerIdOrderByCreatedAtDesc(7L))
                .thenReturn(classes);
        when(sectionRepository.findByClassIdInOrderByClassIdAscDisplayOrderAsc(anyCollection()))
                .thenReturn(List.of());

        var targets = service.targets(7L, Role.LECTURER, 11L);

        assertThat(targets.classes()).hasSize(classCount);
        verify(sectionRepository, times(2))
                .findByClassIdInOrderByClassIdAscDisplayOrderAsc(anyCollection());
        verify(lessonRepository, never())
                .findBySectionIdInOrderBySectionIdAscDisplayOrderAsc(anyCollection());
    }
}
