package com.ksh.features.admin.users.service;

import com.ksh.entities.Permission;
import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.entities.UserFactory;
import com.ksh.entities.UserPermissionOverride;
import com.ksh.features.admin.permissions.repository.EffectivePermissionRepository;
import com.ksh.features.admin.permissions.repository.PermissionRepository;
import com.ksh.features.admin.permissions.repository.RolePermissionRow;
import com.ksh.features.admin.permissions.repository.UserPermissionOverrideRepository;
import com.ksh.features.admin.permissions.service.AdminPermissionsGuard;
import com.ksh.features.admin.permissions.service.PermissionResolver;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPermissionToggleServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final UserPermissionOverrideRepository overrideRepository =
            mock(UserPermissionOverrideRepository.class);
    private final EffectivePermissionRepository effectivePermissionRepository =
            mock(EffectivePermissionRepository.class);
    private final AdminPermissionsGuard guard = mock(AdminPermissionsGuard.class);
    private final AdminUsersAuditWriter auditWriter = mock(AdminUsersAuditWriter.class);
    private final PermissionResolver permissionResolver = mock(PermissionResolver.class);

    private final UserPermissionToggleService service =
            new UserPermissionToggleService(
                    userRepository,
                    permissionRepository,
                    overrideRepository,
                    effectivePermissionRepository,
                    guard,
                    auditWriter,
                    permissionResolver
            );

    @Test
    void toggle_grantPermissionNotProvidedByRole_savesGrantOverrideAndReturnsPermissionName() {
        User user = user(10L, Role.STUDENT);
        Permission permission = permission(100L, "lesson.create", "Create lesson", "LESSON");

        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(permissionRepository.findByFeatureKey("lesson.create"))
                .thenReturn(Optional.of(permission));
        when(guard.isCoreAdminPermission("STUDENT", "LESSON", "lesson.create"))
                .thenReturn(false);
        when(effectivePermissionRepository.findRoleDerivedPermissions(10L))
                .thenReturn(List.of());
        when(overrideRepository.findByUserIdAndPermissionId(10L, 100L))
                .thenReturn(Optional.empty());

        String result = service.toggle(10L, "lesson.create", true, 1L);

        assertThat(result).isEqualTo("Create lesson");
        verify(overrideRepository).save(any(UserPermissionOverride.class));
        verify(auditWriter).write(
                eq(10L),
                eq(UserActivity.TYPE_PERMISSION_CHANGED),
                org.mockito.ArgumentMatchers.contains("Create lesson"),
                any(),
                eq(1L)
        );
    }

    @Test
    void toggle_revokePermissionProvidedByRole_savesRevokeOverrideAndReturnsPermissionName() {
        User user = user(10L, Role.LECTURER);
        Permission permission = permission(100L, "lesson.create", "Create lesson", "LESSON");
        RolePermissionRow rolePermission = mock(RolePermissionRow.class);

        when(rolePermission.getPermissionId()).thenReturn(100L);
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(permissionRepository.findByFeatureKey("lesson.create"))
                .thenReturn(Optional.of(permission));
        when(guard.isCoreAdminPermission("LECTURER", "LESSON", "lesson.create"))
                .thenReturn(false);
        when(effectivePermissionRepository.findRoleDerivedPermissions(10L))
                .thenReturn(List.of(rolePermission));
        when(overrideRepository.findByUserIdAndPermissionId(10L, 100L))
                .thenReturn(Optional.empty());

        String result = service.toggle(10L, "lesson.create", false, 1L);

        assertThat(result).isEqualTo("Create lesson");
        verify(overrideRepository).save(any(UserPermissionOverride.class));
        verify(auditWriter).write(
                eq(10L),
                eq(UserActivity.TYPE_PERMISSION_CHANGED),
                org.mockito.ArgumentMatchers.contains("Create lesson"),
                any(),
                eq(1L)
        );
    }

    @Test
    void toggle_withUnknownUser_throwsNoSuchElementException() {
        when(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggle(999L, "lesson.create", true, 1L))
                .isInstanceOf(NoSuchElementException.class);

        verify(permissionRepository, never()).findByFeatureKey(any());
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void toggle_withUnknownPermission_throwsNoSuchElementException() {
        User user = user(10L, Role.STUDENT);

        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(permissionRepository.findByFeatureKey("unknown.permission"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggle(10L, "unknown.permission", true, 1L))
                .isInstanceOf(NoSuchElementException.class);

        verify(overrideRepository, never()).save(any());
        verify(auditWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void toggle_withCoreAdminPermission_throwsAccessDeniedException() {
        User user = user(10L, Role.ADMIN);
        Permission permission = permission(200L, "admin.core", "Admin core", "ADMIN");

        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(permissionRepository.findByFeatureKey("admin.core"))
                .thenReturn(Optional.of(permission));
        when(guard.isCoreAdminPermission("ADMIN", "ADMIN", "admin.core"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.toggle(10L, "admin.core", false, 1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(overrideRepository, never()).save(any());
        verify(auditWriter, never()).write(any(), any(), any(), any(), any());
    }

    private static User user(Long id, Role role) {
        User user = UserFactory.newAdminCreated(
                "user-" + id + "@example.com",
                "encoded-password",
                "Test User " + id,
                role,
                true,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Permission permission(
            Long id,
            String featureKey,
            String name,
            String permissionGroup
    ) {
        Permission permission = mock(Permission.class);
        when(permission.getId()).thenReturn(id);
        when(permission.getFeatureKey()).thenReturn(featureKey);
        when(permission.getName()).thenReturn(name);
        when(permission.getPermissionGroup()).thenReturn(permissionGroup);
        return permission;
    }
}