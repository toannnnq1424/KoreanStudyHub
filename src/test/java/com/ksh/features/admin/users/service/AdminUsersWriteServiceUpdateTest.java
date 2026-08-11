package com.ksh.features.admin.users.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.SystemSetting;
import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.admin.departments.service.DepartmentService;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import com.ksh.features.admin.users.dto.EditUserForm;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsersWriteServiceUpdateTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminUsersGuard guard = mock(AdminUsersGuard.class);
    private final AdminUsersAuditWriter auditWriter = mock(AdminUsersAuditWriter.class);
    private final ClassRepository classRepository = mock(ClassRepository.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final SystemSettingsRepository systemSettingsRepository =
            mock(SystemSettingsRepository.class);

    private final AdminUsersWriteService service =
            new AdminUsersWriteService(
                    userRepository,
                    passwordEncoder,
                    guard,
                    auditWriter,
                    classRepository,
                    departmentRepository,
                    systemSettingsRepository
            );

    @Test
    void update_withValidInput_updatesUserAndReturnsEmptyWarnings() {
        User target = user(10L, "old.user@ksh.test", Role.STUDENT);
        EditUserForm form = form(
                "updated.user@ksh.test",
                "Updated User",
                Role.STUDENT,
                "0912345678",
                "Updated bio"
        );

        mockLeaderLock();
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(target));
        when(departmentRepository.findFirstByLeaderUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findFirstByEmailIgnoreCaseAndIdNot(
                "updated.user@ksh.test", 10L))
                .thenReturn(Optional.empty());
        when(userRepository.save(target)).thenReturn(target);

        List<String> result = service.update(10L, form, 1L);

        assertThat(result).isEmpty();
        assertThat(target.getEmail()).isEqualTo("updated.user@ksh.test");
        assertThat(target.getFullName()).isEqualTo("Updated User");
        assertThat(target.getPhone()).isEqualTo("0912345678");
        assertThat(target.getBio()).isEqualTo("Updated bio");
        verify(userRepository).save(target);
        verify(auditWriter).write(
                eq(10L),
                eq(UserActivity.TYPE_UPDATED),
                eq("Cập nhật tài khoản updated.user@ksh.test"),
                any(),
                eq(1L)
        );
    }

    @Test
    void update_withMixedCaseAndSpacesEmail_normalizesEmailBeforeSaving() {
        User target = user(10L, "old.user@ksh.test", Role.STUDENT);
        EditUserForm form = form(
                " Updated.User@KSH.Test ",
                "Updated User",
                Role.STUDENT,
                null,
                null
        );

        mockLeaderLock();
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(target));
        when(departmentRepository.findFirstByLeaderUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findFirstByEmailIgnoreCaseAndIdNot(
                "updated.user@ksh.test", 10L))
                .thenReturn(Optional.empty());
        when(userRepository.save(target)).thenReturn(target);

        List<String> result = service.update(10L, form, 1L);

        assertThat(result).isEmpty();
        assertThat(target.getEmail()).isEqualTo("updated.user@ksh.test");
        verify(userRepository)
                .findFirstByEmailIgnoreCaseAndIdNot("updated.user@ksh.test", 10L);
    }

    @Test
    void update_withDuplicateEmail_throwsEmailAlreadyUsedException() {
        User target = user(10L, "old.user@ksh.test", Role.STUDENT);
        User existing = user(99L, "existing.user@ksh.test", Role.STUDENT);
        EditUserForm form = form(
                "existing.user@ksh.test",
                "Updated User",
                Role.STUDENT,
                null,
                null
        );

        mockLeaderLock();
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(target));
        when(departmentRepository.findFirstByLeaderUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findFirstByEmailIgnoreCaseAndIdNot(
                "existing.user@ksh.test", 10L))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(10L, form, 1L))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(target);
        verify(auditWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void update_whenAdminChangesOwnRole_throwsAccessDeniedException() {
        User target = user(10L, "admin.user@ksh.test", Role.ADMIN);
        EditUserForm form = form(
                "admin.user@ksh.test",
                "Admin User",
                Role.STUDENT,
                null,
                null
        );

        mockLeaderLock();
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(target));
        when(departmentRepository.findFirstByLeaderUserId(10L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new AccessDeniedException("self role change denied"))
                .when(guard)
                .requireNotSelf(10L, 10L, "thay đổi vai trò");

        assertThatThrownBy(() -> service.update(10L, form, 10L))
                .isInstanceOf(AccessDeniedException.class);

        verify(userRepository, never()).save(target);
    }

    @Test
    void update_whenDemotingLecturerWithOwnedClasses_returnsWarning() {
        User target = user(20L, "lecturer.user@ksh.test", Role.LECTURER);
        EditUserForm form = form(
                "lecturer.user@ksh.test",
                "Lecturer User",
                Role.STUDENT,
                null,
                null
        );
        ClassEntity classA = mock(ClassEntity.class);
        ClassEntity classB = mock(ClassEntity.class);

        mockLeaderLock();
        when(userRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(target));
        when(departmentRepository.findFirstByLeaderUserId(20L)).thenReturn(Optional.empty());
        when(userRepository.findFirstByEmailIgnoreCaseAndIdNot(
                "lecturer.user@ksh.test", 20L))
                .thenReturn(Optional.empty());
        when(userRepository.save(target)).thenReturn(target);
        when(classA.getName()).thenReturn("Korean 101");
        when(classB.getName()).thenReturn("Korean 102");
        when(classRepository.findAllByLecturerId(20L))
                .thenReturn(List.of(classA, classB));

        List<String> result = service.update(20L, form, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .contains("2 lớp")
                .contains("Korean 101")
                .contains("Korean 102");
    }

    private void mockLeaderLock() {
        when(systemSettingsRepository.findBySettingKeyForUpdate(
                DepartmentService.LEADER_ASSIGNMENT_LOCK_SETTING_KEY))
                .thenReturn(Optional.of(new SystemSetting(
                        DepartmentService.LEADER_ASSIGNMENT_LOCK_SETTING_KEY,
                        "",
                        "ADMIN"
                )));
    }

    private static EditUserForm form(
            String email,
            String fullName,
            Role role,
            String phone,
            String bio
    ) {
        return new EditUserForm(
                email,
                fullName,
                role,
                null,
                phone,
                bio,
                true
        );
    }

    private static User user(Long id, String email, Role role) {
        User user = UserFactory.newAdminCreated(
                email,
                "encoded-password",
                "Old User",
                role,
                true,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}