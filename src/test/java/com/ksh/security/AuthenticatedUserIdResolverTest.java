package com.ksh.security;

import com.ksh.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedUserIdResolverTest {

    private final AuthenticatedUserIdResolver resolver = new AuthenticatedUserIdResolver();

    @Test
    void resolvesFormLoginLocalUserId() {
        KshUserDetails principal = new KshUserDetails(user(41L));
        Authentication authentication = new TestingAuthenticationToken(principal, "credentials");
        authentication.setAuthenticated(true);

        assertThat(resolver.resolve(authentication)).isEqualTo(41L);
    }

    @Test
    void resolvesOidcLocalUserId() {
        CustomOidcUserPrincipal principal = new CustomOidcUserPrincipal(mock(OidcUser.class), user(42L));
        Authentication authentication = new TestingAuthenticationToken(principal, "credentials");
        authentication.setAuthenticated(true);

        assertThat(resolver.resolve(authentication)).isEqualTo(42L);
    }

    @Test
    void rejectsUnsupportedPrincipalWithoutEmailFallback() {
        Authentication authentication = new TestingAuthenticationToken("student@ksh.edu.vn", "credentials");
        authentication.setAuthenticated(true);

        assertThatThrownBy(() -> resolver.resolve(authentication))
                .isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(InsufficientAuthenticationException.class);
    }

    private static User user(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(Role.STUDENT);
        when(user.getEmail()).thenReturn("student-" + id + "@ksh.edu.vn");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn("Student " + id);
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return user;
    }
}
