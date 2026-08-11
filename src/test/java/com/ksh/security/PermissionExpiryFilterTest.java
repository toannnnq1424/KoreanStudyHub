package com.ksh.security;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.permissions.service.PermissionResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionExpiryFilterTest {

    private final PermissionResolver permissionResolver = mock(PermissionResolver.class);
    private final PermissionExpiryFilter filter = new PermissionExpiryFilter(permissionResolver);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiredPermissionSnapshotInvalidatesSameSessionAndClearsAuthentication()
            throws Exception {
        KshUserDetails principal = principal(LocalDateTime.now().minusSeconds(1));
        authenticate(principal);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(permissionResolver).evictUser(77L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void futurePermissionSnapshotKeepsSessionAuthenticated() throws Exception {
        KshUserDetails principal = principal(LocalDateTime.now().plusMinutes(5));
        authenticate(principal);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(permissionResolver, never()).evictUser(77L);
    }

    private static KshUserDetails principal(LocalDateTime validUntil) {
        User user = UserFactory.newAdminCreated(
                "expiry@example.test", "unused", "Expiry User",
                Role.LECTURER, true, null, null);
        ReflectionTestUtils.setField(user, "id", 77L);
        return new KshUserDetails(user, Set.of("library.view"), validUntil);
    }

    private static void authenticate(KshUserDetails principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }
}
