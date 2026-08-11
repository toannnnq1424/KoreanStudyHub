package com.ksh.features.classes.service;

import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassEntity;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.classes.dto.ClassesDtos.ClassForm;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassesServiceUpdateTest {

    private final ClassRepository classRepository = mock(ClassRepository.class);
    private final ClassActivityWriter activityWriter = mock(ClassActivityWriter.class);
    private final DepartmentRepository subjectRepository = mock(DepartmentRepository.class);
    private final ClassRoleAccessPolicy accessPolicy = mock(ClassRoleAccessPolicy.class);
    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);

    private final ClassesService service =
            new ClassesService(
                    classRepository,
                    activityWriter,
                    subjectRepository,
                    accessPolicy,
                    eventPublisher
            );

    @Test
    void update_withValidInput_returnsSavedClassAndWritesUpdateActivity() {
        ClassEntity existing = classEntity(100L, "Korean Basic 1", 5L);
        ClassForm form = new ClassForm(
                "Korean Basic 2",
                "Updated description",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 31),
                60,
                12L
        );

        when(classRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(accessPolicy.canManageClass(existing, 5L, Role.LECTURER)).thenReturn(true);
        when(classRepository.save(existing)).thenReturn(existing);

        ClassEntity result = service.update(100L, form, 5L, Role.LECTURER);

        assertThat(result.getName()).isEqualTo("Korean Basic 2");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(result.getMaxStudents()).isEqualTo(60);

        verify(activityWriter).write(
                eq(100L),
                eq(ClassActivity.TYPE_UPDATED),
                eq("Cập nhật lớp Korean Basic 2"),
                any(),
                eq(5L)
        );
    }

    @Test
    void update_withMinimumMaxStudentsAndOptionalFields_returnsSavedClass() {
        ClassEntity existing = classEntity(100L, "Korean Basic 1", 5L);
        ClassForm form = new ClassForm(
                "Korean Basic 2",
                null,
                null,
                null,
                1,
                12L
        );

        when(classRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(accessPolicy.canManageClass(existing, 5L, Role.LECTURER)).thenReturn(true);
        when(classRepository.save(existing)).thenReturn(existing);

        ClassEntity result = service.update(100L, form, 5L, Role.LECTURER);

        assertThat(result.getName()).isEqualTo("Korean Basic 2");
        assertThat(result.getDescription()).isNull();
        assertThat(result.getStartDate()).isNull();
        assertThat(result.getEndDate()).isNull();
        assertThat(result.getMaxStudents()).isEqualTo(1);
    }

    @Test
    void update_withUnknownClass_throwsEntityNotFoundException() {
        ClassForm form = validForm();

        when(classRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, form, 5L, Role.LECTURER))
                .isInstanceOf(EntityNotFoundException.class);

        verify(classRepository, never()).save(any());
        verify(activityWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void update_withoutManagePermission_throwsAccessDeniedException() {
        ClassEntity existing = classEntity(100L, "Korean Basic 1", 5L);
        ClassForm form = validForm();

        when(classRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(accessPolicy.canManageClass(existing, 6L, Role.LECTURER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(100L, form, 6L, Role.LECTURER))
                .isInstanceOf(AccessDeniedException.class);

        verify(classRepository, never()).save(existing);
        verify(activityWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void update_whenRepositorySaveFails_throwsExceptionAndDoesNotWriteActivity() {
        ClassEntity existing = classEntity(100L, "Korean Basic 1", 5L);
        ClassForm form = validForm();

        when(classRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(accessPolicy.canManageClass(existing, 5L, Role.LECTURER)).thenReturn(true);
        when(classRepository.save(existing)).thenThrow(new RuntimeException("save failed"));

        assertThatThrownBy(() -> service.update(100L, form, 5L, Role.LECTURER))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("save failed");

        verify(activityWriter, never()).write(any(), any(), any(), any(), any());
    }

    private static ClassForm validForm() {
        return new ClassForm(
                "Korean Basic 2",
                "Updated description",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 31),
                60,
                12L
        );
    }

    private static ClassEntity classEntity(Long id, String name, Long lecturerId) {
        ClassEntity entity = new ClassEntity(
                name,
                lecturerId,
                lecturerId,
                "Old description",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 11, 30),
                50
        );
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSubjectId(12L);
        return entity;
    }
}