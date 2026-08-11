package com.ksh.features.classes.service;

import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.classes.dto.ClassesDtos.ClassForm;
import com.ksh.features.classes.repository.ClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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

class ClassesServiceCreateTest {

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
    void create_withValidInput_returnsSavedClassAndWritesCreatedActivity() {
        ClassForm form = new ClassForm(
                "Korean Basic 1",
                "Beginner Korean class",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 31),
                50,
                12L
        );

        Department subject = subject(12L, "KR");
        when(subjectRepository.findById(12L)).thenReturn(Optional.of(subject));
        when(classRepository.saveAndFlush(any(ClassEntity.class)))
                .thenAnswer(invocation -> {
                    ClassEntity entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 100L);
                    return entity;
                });

        ClassEntity result = service.create(form, 5L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("Korean Basic 1");
        assertThat(result.getLecturerId()).isEqualTo(5L);
        assertThat(result.getSubjectId()).isEqualTo(12L);
        assertThat(result.getStatus()).isEqualTo(ClassEntity.STATUS_DRAFT);

        verify(activityWriter).write(
                eq(100L),
                eq(ClassActivity.TYPE_CREATED),
                eq("Tạo lớp Korean Basic 1"),
                eq(5L)
        );
    }

    @Test
    void create_withMinimumMaxStudentsAndOptionalFields_returnsSavedClass() {
        ClassForm form = new ClassForm(
                "Korean Basic 1",
                null,
                null,
                null,
                1,
                12L
        );

        Department subject = subject(12L, "KR");
        when(subjectRepository.findById(12L)).thenReturn(Optional.of(subject));
        when(classRepository.saveAndFlush(any(ClassEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClassEntity result = service.create(form, 5L);

        assertThat(result.getName()).isEqualTo("Korean Basic 1");
        assertThat(result.getDescription()).isNull();
        assertThat(result.getMaxStudents()).isEqualTo(1);
        assertThat(result.getSubjectId()).isEqualTo(12L);
    }

    @Test
    void create_withUnknownSubject_throwsIllegalArgumentException() {
        ClassForm form = new ClassForm(
                "Korean Basic 1",
                "Beginner Korean class",
                null,
                null,
                50,
                99L
        );

        when(subjectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(form, 5L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(classRepository, never()).saveAndFlush(any());
        verify(activityWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    void create_whenRepositorySaveFails_throwsExceptionAndDoesNotWriteActivity() {
        ClassForm form = new ClassForm(
                "Korean Basic 1",
                "Beginner Korean class",
                null,
                null,
                50,
                12L
        );

        Department subject = subject(12L, "KR");
        when(subjectRepository.findById(12L)).thenReturn(Optional.of(subject));
        when(classRepository.saveAndFlush(any(ClassEntity.class)))
                .thenThrow(new DataIntegrityViolationException("save failed"));

        assertThatThrownBy(() -> service.create(form, 5L))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(activityWriter, never()).write(any(), any(), any(), any());
    }

    private static Department subject(Long id, String code) {
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(id);
        when(department.getCode()).thenReturn(code);
        when(department.isActive()).thenReturn(true);
        return department;
    }
}
