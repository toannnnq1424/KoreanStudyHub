package com.ksh.features.classes.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassRoleAccessPolicyTest {

    private final LeaderDepartmentResolver resolver = mock(LeaderDepartmentResolver.class);
    private final ClassRoleAccessPolicy policy = new ClassRoleAccessPolicy(resolver);

    @Test
    void leaderIsLimitedToResolvedDepartment() {
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(10L);
        when(resolver.resolve(7L)).thenReturn(Optional.of(department));
        ClassEntity same = classEntity(42L, 10L);
        ClassEntity foreign = classEntity(42L, 11L);

        assertThat(policy.canAccess(same, 7L, Role.LEADER)).isTrue();
        assertThat(policy.canAccess(foreign, 7L, Role.LEADER)).isFalse();
    }

    @Test
    void adminIsGlobalAndLecturerIsOwnerScoped() {
        ClassEntity clazz = classEntity(42L, 10L);

        assertThat(policy.canAccess(clazz, 1L, Role.ADMIN)).isTrue();
        assertThat(policy.canAccess(clazz, 42L, Role.LECTURER)).isTrue();
        assertThat(policy.canAccess(clazz, 99L, Role.LECTURER)).isFalse();
    }

    private static ClassEntity classEntity(Long lecturerId, Long departmentId) {
        ClassEntity clazz = mock(ClassEntity.class);
        when(clazz.getLecturerId()).thenReturn(lecturerId);
        when(clazz.getDepartmentId()).thenReturn(departmentId);
        return clazz;
    }
}
