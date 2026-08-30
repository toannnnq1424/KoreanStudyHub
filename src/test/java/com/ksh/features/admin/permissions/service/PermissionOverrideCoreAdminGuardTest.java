package com.ksh.features.admin.permissions.service;

import com.ksh.entities.Permission;
import com.ksh.entities.User;
import com.ksh.entities.UserPermissionOverride;
import com.ksh.features.admin.permissions.dto.PermissionDtos.OverrideForm;
import com.ksh.features.admin.permissions.repository.PermissionRepository;
import com.ksh.features.admin.permissions.repository.UserPermissionOverrideRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.profile.service.SessionRevocationService;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionOverrideCoreAdminGuardTest {

    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionAuditWriter auditWriter;
    @Mock private AdminPermissionsGuard permissionsGuard;
    @Mock private PermissionResolver permissionResolver;
    @Mock private SessionRevocationService sessionRevocationService;

    @InjectMocks private PermissionOverrideService service;

    @Test
    void revoke_override_for_admin_core_permission_is_rejected_before_write() {
        Long userId = 7L;
        Permission permission = org.mockito.Mockito.mock(Permission.class);
        User admin = org.mockito.Mockito.mock(User.class);
        OverrideForm form = new OverrideForm(userId, "system.permissions",
                UserPermissionOverride.TYPE_REVOKE, "test lockout", null);
        when(permissionRepository.findByFeatureKey("system.permissions"))
                .thenReturn(Optional.of(permission));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(admin));
        when(admin.getRole()).thenReturn(Role.ADMIN);
        org.mockito.Mockito.doThrow(new AccessDeniedException("core"))
                .when(permissionsGuard).checkDetachAllowed(Role.ADMIN.name(), permission);

        assertThatThrownBy(() -> service.createOrReplace(form, 1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionsGuard).checkDetachAllowed(Role.ADMIN.name(), permission);
        verify(overrideRepository, never()).findByUserIdAndPermissionId(userId, null);
        verifyNoInteractions(auditWriter, permissionResolver, sessionRevocationService);
    }
}
