package com.ksh.security;

import com.ksh.features.admin.permissions.service.PermissionResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginPermissionFreshnessTest {

    private final PermissionResolver permissionResolver = mock(PermissionResolver.class);
    private final LoginPermissionResolver loginResolver =
            new LoginPermissionResolver(permissionResolver);

    @Test
    void elapsedOverrideEvictsStaleCacheBeforeResolvingLoginAuthorities() {
        when(permissionResolver.permissionTimeline(org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenReturn(new PermissionResolver.PermissionTimeline(true, null));
        when(permissionResolver.resolvePermissions(9L)).thenReturn(Set.of("library.view"));

        LoginPermissionResolver.PermissionSnapshot result =
                loginResolver.resolveSnapshotSafely(9L);

        assertThat(result.featureKeys()).containsExactly("library.view");
        var order = inOrder(permissionResolver);
        order.verify(permissionResolver).permissionTimeline(
                org.mockito.ArgumentMatchers.eq(9L), any(LocalDateTime.class));
        order.verify(permissionResolver).evictUser(9L);
        order.verify(permissionResolver).resolvePermissions(9L);
    }

    @Test
    void nearestFutureExpiryIsCopiedIntoTheLoginSnapshot() {
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);
        when(permissionResolver.permissionTimeline(org.mockito.ArgumentMatchers.eq(10L), any()))
                .thenReturn(new PermissionResolver.PermissionTimeline(false, expiry));
        when(permissionResolver.resolvePermissions(10L)).thenReturn(Set.of("system.settings"));

        LoginPermissionResolver.PermissionSnapshot result =
                loginResolver.resolveSnapshotSafely(10L);

        assertThat(result.validUntil()).isEqualTo(expiry);
        verify(permissionResolver, never()).evictUser(10L);
    }
}
