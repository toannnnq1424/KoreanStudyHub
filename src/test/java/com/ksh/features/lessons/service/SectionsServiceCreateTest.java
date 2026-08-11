package com.ksh.features.lessons.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Section;
import com.ksh.entities.SectionActivity;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.lessons.dto.SectionDtos.SectionRow;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SectionsServiceCreateTest {

    private final SectionRepository sectionRepository = mock(SectionRepository.class);
    private final ClassesService classesService = mock(ClassesService.class);
    private final SectionActivityWriter activityWriter = mock(SectionActivityWriter.class);
    private final SectionsReorderService reorderService = mock(SectionsReorderService.class);

    private final SectionsService service =
            new SectionsService(
                    sectionRepository,
                    classesService,
                    activityWriter,
                    reorderService
            );

    @Test
    void create_withValidInput_returnsSectionRowAndWritesCreatedActivity() {
        ClassEntity clazz = classEntity(100L, 5L);

        when(classesService.getEditableForUpdate(100L, 5L, Role.LECTURER))
                .thenReturn(clazz);
        when(sectionRepository.findMaxDisplayOrder(100L)).thenReturn((short) 0);
        when(sectionRepository.save(any(Section.class)))
                .thenAnswer(invocation -> {
                    Section section = invocation.getArgument(0);
                    ReflectionTestUtils.setField(section, "id", 10L);
                    return section;
                });

        SectionRow result = service.create(100L, "Chapter 1", 5L, Role.LECTURER);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.title()).isEqualTo("Chapter 1");
        assertThat(result.displayOrder()).isEqualTo((short) 1);

        verify(activityWriter).write(
                eq(10L),
                eq(SectionActivity.TYPE_CREATED),
                eq("Tạo chương Chapter 1"),
                eq(5L)
        );
    }

    @Test
    void create_appendsAfterCurrentLastDisplayOrder() {
        ClassEntity clazz = classEntity(100L, 5L);

        when(classesService.getEditableForUpdate(100L, 5L, Role.LECTURER))
                .thenReturn(clazz);
        when(sectionRepository.findMaxDisplayOrder(100L)).thenReturn((short) 5);
        when(sectionRepository.save(any(Section.class)))
                .thenAnswer(invocation -> {
                    Section section = invocation.getArgument(0);
                    ReflectionTestUtils.setField(section, "id", 11L);
                    return section;
                });

        SectionRow result = service.create(100L, "Chapter 2", 5L, Role.LECTURER);

        assertThat(result.title()).isEqualTo("Chapter 2");
        assertThat(result.displayOrder()).isEqualTo((short) 6);
    }

    @Test
    void create_withOneCharacterTitle_returnsSectionRow() {
        ClassEntity clazz = classEntity(100L, 5L);

        when(classesService.getEditableForUpdate(100L, 5L, Role.LECTURER))
                .thenReturn(clazz);
        when(sectionRepository.findMaxDisplayOrder(100L)).thenReturn((short) 0);
        when(sectionRepository.save(any(Section.class)))
                .thenAnswer(invocation -> {
                    Section section = invocation.getArgument(0);
                    ReflectionTestUtils.setField(section, "id", 12L);
                    return section;
                });

        SectionRow result = service.create(100L, "A", 5L, Role.LECTURER);

        assertThat(result.title()).isEqualTo("A");
        assertThat(result.displayOrder()).isEqualTo((short) 1);
    }

    @Test
    void create_withUnknownClass_throwsEntityNotFoundException() {
        when(classesService.getEditableForUpdate(999L, 5L, Role.LECTURER))
                .thenThrow(new EntityNotFoundException("Class not found"));

        assertThatThrownBy(() -> service.create(999L, "Chapter 1", 5L, Role.LECTURER))
                .isInstanceOf(EntityNotFoundException.class);

        verify(sectionRepository, never()).save(any());
        verify(activityWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    void create_withoutEditPermission_throwsAccessDeniedException() {
        when(classesService.getEditableForUpdate(100L, 6L, Role.LECTURER))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.create(100L, "Chapter 1", 6L, Role.LECTURER))
                .isInstanceOf(AccessDeniedException.class);

        verify(sectionRepository, never()).save(any());
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