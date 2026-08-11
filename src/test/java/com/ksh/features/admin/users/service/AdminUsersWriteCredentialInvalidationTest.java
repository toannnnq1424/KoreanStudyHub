package com.ksh.features.admin.users.service;

import com.ksh.entities.SystemSetting;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.admin.departments.service.DepartmentService;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import com.ksh.features.admin.users.dto.EditUserForm;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.auth.service.CredentialRotationService;
import com.ksh.features.profile.service.SessionRevocationService;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsersWriteCredentialInvalidationTest {

    private UserRepository users;
    private CredentialRotationService credentials;
    private SessionRevocationService sessions;
    private SystemSettingsRepository settings;
    private AdminUsersWriteService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        credentials = mock(CredentialRotationService.class);
        sessions = mock(SessionRevocationService.class);
        settings = mock(SystemSettingsRepository.class);
        when(settings.findBySettingKeyForUpdate(
                DepartmentService.LEADER_ASSIGNMENT_LOCK_SETTING_KEY))
                .thenReturn(Optional.of(new SystemSetting(
                        DepartmentService.LEADER_ASSIGNMENT_LOCK_SETTING_KEY,
                        "", "AUTH")));
        service = new AdminUsersWriteService(
                users,
                mock(PasswordEncoder.class),
                mock(AdminUsersGuard.class),
                mock(AdminUsersAuditWriter.class),
                mock(DepartmentRepository.class),
                settings,
                sessions,
                credentials);
    }

    @Test
    void changingEmailInvalidatesLinksSentToTheOldMailbox() {
        User target = user(41L, "old-address@example.test", "Student Name");
        when(users.findByIdForUpdate(41L)).thenReturn(Optional.of(target));
        when(users.save(target)).thenReturn(target);
        EditUserForm form = formFor(target, "new-address@example.test", "Student Name");

        service.update(41L, form, 1L);

        verify(credentials).invalidateRecoveryTokens(41L);
        verify(sessions).revokeAllSessions("old-address@example.test");
    }

    @Test
    void profileOnlyEditDoesNotInvalidateRecoveryLinks() {
        User target = user(42L, "same-address@example.test", "Old Name");
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(target));
        when(users.save(target)).thenReturn(target);
        EditUserForm form = formFor(target, "same-address@example.test", "New Name");

        service.update(42L, form, 1L);

        verify(credentials, never()).invalidateRecoveryTokens(42L);
        verify(sessions, never()).revokeAllSessions("same-address@example.test");
    }

    private static User user(Long id, String email, String fullName) {
        User user = UserFactory.newAdminCreated(
                email,
                "existing-hash",
                fullName,
                Role.STUDENT,
                true,
                null,
                null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static EditUserForm formFor(User user, String email, String fullName) {
        return new EditUserForm(
                email,
                fullName,
                user.getRole(),
                user.getSubjectId(),
                user.getPhone(),
                user.getBio(),
                user.isEmailVerified());
    }
}
