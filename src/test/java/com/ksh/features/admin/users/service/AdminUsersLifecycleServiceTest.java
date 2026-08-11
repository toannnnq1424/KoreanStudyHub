package com.ksh.features.admin.users.service;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.auth.service.CredentialRotationService;
import com.ksh.features.profile.service.SessionRevocationService;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsersLifecycleServiceTest {

    private UserRepository userRepository;
    private CredentialRotationService credentialRotationService;
    private AdminUsersGuard guard;
    private AdminUsersLifecycleService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        credentialRotationService = mock(CredentialRotationService.class);
        guard = mock(AdminUsersGuard.class);
        service = new AdminUsersLifecycleService(
                userRepository,
                credentialRotationService,
                guard,
                mock(AdminUsersAuditWriter.class),
                mock(SessionRevocationService.class));
    }

    @Test
    void deactivate_acquires_shared_admin_mutex_before_target_lock() {
        User target = arrangeLockedTarget(12L);

        service.deactivate(target.getId(), 99L);

        assertMutexBeforeTarget(target.getId());
    }

    @Test
    void lock_acquires_shared_admin_mutex_before_target_lock() {
        User target = arrangeLockedTarget(13L);

        service.lock(target.getId(), "security review", 99L);

        assertMutexBeforeTarget(target.getId());
    }

    @Test
    void softDelete_acquires_shared_admin_mutex_before_target_lock() {
        User target = arrangeLockedTarget(14L);

        service.softDelete(target.getId(), 99L);

        assertMutexBeforeTarget(target.getId());
    }

    @Test
    void mutation_fails_closed_when_no_durable_admin_mutex_row_exists() {
        when(userRepository.findAdminLifecycleMutexForUpdate("ADMIN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(12L, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quản trị");
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void adminPasswordResetRotatesPasswordAndEveryOutstandingRecoveryLink() {
        User target = admin(21L, "target-admin@ksh.test");
        when(userRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(target));
        when(credentialRotationService.replacePassword(target, "new-password"))
                .thenReturn(target);

        service.resetPassword(21L, "new-password", 99L);

        verify(credentialRotationService).replacePassword(target, "new-password");
        verify(userRepository, never()).save(target);
    }

    private User arrangeLockedTarget(Long targetId) {
        User mutexOwner = admin(1L, "mutex-admin@ksh.test");
        User target = admin(targetId, "target-admin-" + targetId + "@ksh.test");
        when(userRepository.findAdminLifecycleMutexForUpdate("ADMIN"))
                .thenReturn(Optional.of(mutexOwner));
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        return target;
    }

    private void assertMutexBeforeTarget(Long targetId) {
        InOrder order = inOrder(userRepository);
        order.verify(userRepository).findAdminLifecycleMutexForUpdate("ADMIN");
        order.verify(userRepository).findByIdForUpdate(targetId);
    }

    private static User admin(Long id, String email) {
        User user = UserFactory.newAdminCreated(
                email, "encoded-password", "Admin", Role.ADMIN,
                true, null, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
