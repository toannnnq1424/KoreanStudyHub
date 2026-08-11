package com.ksh.features.admin.users.service;

import com.ksh.entities.Department;
import com.ksh.entities.SystemSetting;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.admin.departments.service.DepartmentService;
import com.ksh.features.admin.departments.service.DepartmentValidationException;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import com.ksh.features.admin.users.dto.EditUserForm;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.auth.service.CredentialRotationService;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.profile.service.SessionRevocationService;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUsersLeaderAssignmentGuardTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminUsersGuard guard = mock(AdminUsersGuard.class);
    private final AdminUsersAuditWriter auditWriter = mock(AdminUsersAuditWriter.class);
    private final ClassRepository classes = mock(ClassRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final SystemSettingsRepository settings = mock(SystemSettingsRepository.class);
    private final SessionRevocationService sessionRevocation = mock(SessionRevocationService.class);
    private final CredentialRotationService credentialRotation = mock(CredentialRotationService.class);

    @Test
    void editLocksSharedAnchorBeforeUserAndRejectsBreakingLeaderPointer() {
        User leader = user(20L, Role.LEADER, 10L);
        Department department = department(10L, 20L);
        when(settings.findBySettingKeyForUpdate(
                DepartmentService.LEADER_ASSIGNMENT_LOCK_SETTING_KEY))
                .thenReturn(Optional.of(new SystemSetting(
                        DepartmentService.LEADER_ASSIGNMENT_LOCK_SETTING_KEY,
                        "", "AI")));
        when(users.findByIdForUpdate(20L)).thenReturn(Optional.of(leader));
        when(departments.findFirstByLeaderUserId(20L))
                .thenReturn(Optional.of(department));

        EditUserForm demotion = new EditUserForm(
                leader.getEmail(), leader.getFullName(), Role.LECTURER,
                10L, null, null, true);

        assertThatThrownBy(() -> service().update(20L, demotion, 99L))
                .isInstanceOf(DepartmentValidationException.class)
                .hasMessageContaining("màn hình Bộ môn");

        var order = inOrder(settings, users, departments);
        order.verify(settings).findBySettingKeyForUpdate(
                DepartmentService.LEADER_ASSIGNMENT_LOCK_SETTING_KEY);
        order.verify(users).findByIdForUpdate(20L);
        order.verify(departments).findFirstByLeaderUserId(20L);
        verify(users, never()).save(leader);
        verifyNoInteractions(auditWriter);
    }

    private AdminUsersWriteService service() {
        return new AdminUsersWriteService(
                users, passwordEncoder, guard, auditWriter,
                departments, settings, sessionRevocation, credentialRotation);
    }

    private static User user(Long id, Role role, Long subjectId) {
        User user = UserFactory.newAdminCreated(
                "leader-" + id + "@ksh.test",
                "unused-test-hash",
                "Leader " + id,
                role,
                true,
                null,
                null);
        ReflectionTestUtils.setField(user, "id", id);
        user.setSubjectId(subjectId);
        return user;
    }

    private static Department department(Long id, Long leaderUserId) {
        Department department = new Department("Tiếng Hàn", "KR", null, true);
        ReflectionTestUtils.setField(department, "id", id);
        department.assignLeader(leaderUserId);
        return department;
    }
}
