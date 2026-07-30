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
    private final SessionRevocationService service = new SessionRevocationService(registry);

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
    }

    @Test
    void blankUsernameDoesNotTouchRegistry() {
        assertThat(service.revokeOtherSessions(" ", "current")).isZero();
        verifyNoInteractions(registry);
    }
}
