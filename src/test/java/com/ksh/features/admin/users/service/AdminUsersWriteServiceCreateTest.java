package com.ksh.features.admin.users.service;

import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import com.ksh.features.admin.users.dto.CreateUserForm;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsersWriteServiceCreateTest {

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
    void create_withValidInput_returnsSavedUserAndWritesAudit() {
        CreateUserForm form = new CreateUserForm(
                "new.user@ksh.test",
                "New User",
                Role.STUDENT,
                null,
                "0912345678",
                "Korean learner",
                true,
                "TempPass@123"
        );

        when(userRepository.findFirstByEmailIgnoreCase("new.user@ksh.test"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("TempPass@123"))
                .thenReturn("encoded-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 10L);
                    return user;
                });

        User result = service.create(form, 1L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getEmail()).isEqualTo("new.user@ksh.test");
        assertThat(result.getFullName()).isEqualTo("New User");
        assertThat(result.getRole()).isEqualTo(Role.STUDENT);
        assertThat(result.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(result.isEmailVerified()).isTrue();

        verify(auditWriter).write(
                eq(10L),
                eq("CREATED"),
                eq("Tạo tài khoản new.user@ksh.test"),
                eq(null),
                eq(1L)
        );
    }

    @Test
    void create_withMixedCaseAndSpacesEmail_normalizesEmailBeforeSaving() {
        CreateUserForm form = new CreateUserForm(
                " New.User@KSH.Test ",
                "New User",
                Role.STUDENT,
                null,
                null,
                null,
                false,
                "TempPass@123"
        );

        when(userRepository.findFirstByEmailIgnoreCase("new.user@ksh.test"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("TempPass@123"))
                .thenReturn("encoded-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.create(form, 1L);

        assertThat(result.getEmail()).isEqualTo("new.user@ksh.test");
        verify(userRepository).findFirstByEmailIgnoreCase("new.user@ksh.test");
    }

    @Test
    void create_withBlankOptionalFields_storesPhoneAndBioAsNull() {
        CreateUserForm form = new CreateUserForm(
                "blank.optional@ksh.test",
                "Blank Optional",
                Role.STUDENT,
                null,
                " ",
                " ",
                false,
                "TempPass@123"
        );

        when(userRepository.findFirstByEmailIgnoreCase("blank.optional@ksh.test"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("TempPass@123"))
                .thenReturn("encoded-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.create(form, 1L);

        assertThat(result.getPhone()).isNull();
        assertThat(result.getBio()).isNull();
    }

    @Test
    void create_withDuplicateEmail_throwsEmailAlreadyUsedException() {
        CreateUserForm form = new CreateUserForm(
                "existing.user@ksh.test",
                "Existing User",
                Role.STUDENT,
                null,
                null,
                null,
                false,
                "TempPass@123"
        );

        User existing = mock(User.class);
        when(userRepository.findFirstByEmailIgnoreCase("existing.user@ksh.test"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(form, 1L))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
        verify(auditWriter, never()).write(any(), any(), any(), any(), any());
    }
}