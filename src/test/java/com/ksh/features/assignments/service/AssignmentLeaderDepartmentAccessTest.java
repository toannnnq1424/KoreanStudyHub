package com.ksh.features.assignments.service;

import com.ksh.entities.ClassEntity;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.ClassRoleAccessPolicy;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AssignmentLeaderDepartmentAccessTest {

    @Test
    void assignmentAccessDelegatesLeaderScopeToCanonicalPolicy() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        UserRepository users = mock(UserRepository.class);
        ClassRepository classes = mock(ClassRepository.class);
        ClassRoleAccessPolicy policy = mock(ClassRoleAccessPolicy.class);
        ClassEntity clazz = mock(ClassEntity.class);
        when(clazz.isDeleted()).thenReturn(false);
        when(classes.findById(9L)).thenReturn(Optional.of(clazz));
        AssignmentAccessSupport access =
                new AssignmentAccessSupport(assignments, enrollments, users, classes, policy);

        when(policy.canAccess(clazz, 7L, Role.LEADER)).thenReturn(false);
        assertThatThrownBy(() -> access.requireEditableClass(9L, 7L, Role.LEADER))
                .isInstanceOf(EntityNotFoundException.class);

        when(policy.canAccess(clazz, 7L, Role.LEADER)).thenReturn(true);
        assertThatCode(() -> access.requireEditableClass(9L, 7L, Role.LEADER))
                .doesNotThrowAnyException();
    }
}
