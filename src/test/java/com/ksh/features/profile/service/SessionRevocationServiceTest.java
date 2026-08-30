package com.ksh.features.profile.service;

import com.ksh.security.CustomOidcUserPrincipal;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SessionRevocationServiceTest {
    private static final String EMAIL = "member@ksh.edu.vn";
    private final SessionRegistry registry = mock(SessionRegistry.class);
    private final AuthenticatedWebSocketSessionRegistry webSockets =
            mock(AuthenticatedWebSocketSessionRegistry.class);
    private final SessionRevocationService service =
            new SessionRevocationService(registry, webSockets);

    private static SessionInformation session(Object principal, String id) {
        return new SessionInformation(principal, id, new Date());
    }

    @Test
    void revokesOtherFormAndOidcSessionsButKeepsCurrentSession() {
        KshUserDetails form = mock(KshUserDetails.class);
        CustomOidcUserPrincipal oidc = mock(CustomOidcUserPrincipal.class);
        when(form.getUsername()).thenReturn(EMAIL);
        when(oidc.getUsername()).thenReturn(EMAIL);
        SessionInformation current = session(form, "current");
        SessionInformation laptop = session(form, "laptop");
        SessionInformation google = session(oidc, "google");
        when(registry.getAllPrincipals()).thenReturn(List.of(form, oidc));
        when(registry.getAllSessions(form, false)).thenReturn(List.of(current, laptop));
        when(registry.getAllSessions(oidc, false)).thenReturn(List.of(google));

        assertThat(service.revokeOtherSessions(EMAIL, "current")).isEqualTo(2);
        assertThat(current.isExpired()).isFalse();
        assertThat(laptop.isExpired()).isTrue();
        assertThat(google.isExpired()).isTrue();
        verify(webSockets).closeOther(EMAIL, "current");
    }

    @Test
    void revokesEveryFormAndOidcSessionWhenAdminChangesAccountAccess() {
        KshUserDetails form = mock(KshUserDetails.class);
        CustomOidcUserPrincipal oidc = mock(CustomOidcUserPrincipal.class);
        when(form.getUsername()).thenReturn(EMAIL);
        when(oidc.getUsername()).thenReturn(EMAIL);
        SessionInformation browser = session(form, "browser");
        SessionInformation google = session(oidc, "google");
        when(registry.getAllPrincipals()).thenReturn(List.of(form, oidc));
        when(registry.getAllSessions(form, false)).thenReturn(List.of(browser));
        when(registry.getAllSessions(oidc, false)).thenReturn(List.of(google));

        assertThat(service.revokeAllSessions(EMAIL)).isEqualTo(2);
        assertThat(browser.isExpired()).isTrue();
        assertThat(google.isExpired()).isTrue();
        verify(webSockets).closeOther(EMAIL, null);
    }

    @Test
    void revokesSessionsByStableUserIdWhenRolePermissionChanges() {
        KshUserDetails form = mock(KshUserDetails.class);
        CustomOidcUserPrincipal oidc = mock(CustomOidcUserPrincipal.class);
        when(form.getId()).thenReturn(42L);
        when(oidc.getId()).thenReturn(42L);
        SessionInformation browser = session(form, "browser");
        SessionInformation google = session(oidc, "google");
        when(registry.getAllPrincipals()).thenReturn(List.of(form, oidc));
        when(registry.getAllSessions(form, false)).thenReturn(List.of(browser));
        when(registry.getAllSessions(oidc, false)).thenReturn(List.of(google));

        assertThat(service.revokeAllSessions(42L)).isEqualTo(2);
        assertThat(browser.isExpired()).isTrue();
        assertThat(google.isExpired()).isTrue();
        verify(webSockets).closeAll(42L);
    }

    @Test
    void blankUsernameDoesNotTouchRegistry() {
        assertThat(service.revokeOtherSessions(" ", "current")).isZero();
        verifyNoInteractions(registry, webSockets);
    }
}
