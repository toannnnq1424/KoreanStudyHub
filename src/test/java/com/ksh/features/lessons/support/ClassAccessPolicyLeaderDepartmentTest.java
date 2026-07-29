package com.ksh.features.lessons.support;

import com.ksh.entities.ClassEntity;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.ClassRoleAccessPolicy;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ClassAccessPolicyLeaderDepartmentTest {

    @Test
    void foreignDepartmentLeaderIsNeitherModeratorNorEnrollmentBypass() {
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        ClassRoleAccessPolicy rolePolicy = mock(ClassRoleAccessPolicy.class);
        ClassEntity clazz = mock(ClassEntity.class);
        when(clazz.getId()).thenReturn(9L);
        when(clazz.getLecturerId()).thenReturn(42L);
        when(rolePolicy.canAccess(clazz, 7L, Role.LEADER)).thenReturn(false);
        ClassAccessPolicy access = new ClassAccessPolicy(enrollments, rolePolicy);

        assertThat(access.isModerator(clazz, 7L, Role.LEADER)).isFalse();
        assertThatThrownBy(() -> access.requireModeratorOrEnrolled(
                clazz, 7L, Role.LEADER))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void sameDepartmentLeaderRemainsModerator() {
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        ClassRoleAccessPolicy rolePolicy = mock(ClassRoleAccessPolicy.class);
        ClassEntity clazz = mock(ClassEntity.class);
        when(clazz.getLecturerId()).thenReturn(42L);
        when(rolePolicy.canAccess(clazz, 7L, Role.LEADER)).thenReturn(true);
        ClassAccessPolicy access = new ClassAccessPolicy(enrollments, rolePolicy);

        assertThat(access.isModerator(clazz, 7L, Role.LEADER)).isTrue();
        access.requireModeratorOrEnrolled(clazz, 7L, Role.LEADER);
        verifyNoInteractions(enrollments);
    }
}
