package com.ksh.security;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.entities.UserOAuthProvider;
import com.ksh.features.admin.permissions.service.PermissionResolver;
import com.ksh.features.auth.repository.UserOAuthProviderRepository;
import com.ksh.features.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Focused security contracts for local-account resolution after Google OIDC login. */
@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    private static final Long USER_ID = 71L;
    private static final String LOCAL_EMAIL = "linked@ksh.edu.vn";
    private static final String GOOGLE_SUB = "google-sub-linked";

    @Mock private UserRepository userRepository;
    @Mock private UserOAuthProviderRepository oauthProviderRepository;
    @Mock private PermissionResolver permissionResolver;

    private CustomOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOidcUserService(
                new OidcAccountLinkService(userRepository, oauthProviderRepository),
                new LoginPermissionResolver(permissionResolver));
    }

    @Test
    void permissionResolverFailure_degradesToRoleOnlyAuthorities() {
        User user = linkedUser();
        stubExistingBinding(user);
        when(permissionResolver.resolvePermissions(USER_ID))
                .thenThrow(new IllegalStateException("RBAC view unavailable"));

        OidcUser principal = service.loadUser(request(LOCAL_EMAIL, GOOGLE_SUB, true));

        assertThat(authorityNames(principal)).containsExactly(Role.STUDENT.authority());
        assertThat(principal.getName()).isEqualTo(LOCAL_EMAIL);
    }

    @Test
    void nullPermissionResult_degradesToRoleOnlyAuthorities() {
        User user = linkedUser();
        stubExistingBinding(user);
        when(permissionResolver.resolvePermissions(USER_ID)).thenReturn(null);

        OidcUser principal = service.loadUser(request(LOCAL_EMAIL, GOOGLE_SUB, true));

        assertThat(authorityNames(principal)).containsExactly(Role.STUDENT.authority());
    }

    @Test
    void existingProviderSubjectWinsOverChangedProviderEmail() {
        User user = linkedUser();
        stubExistingBinding(user);
        String changedProviderEmail = "renamed-at-google@example.com";

        OidcUser principal = service.loadUser(
                request(changedProviderEmail, GOOGLE_SUB, true));

        assertThat(principal.getName()).isEqualTo(LOCAL_EMAIL);
        assertThat(((CustomOidcUserPrincipal) principal).getId()).isEqualTo(USER_ID);
        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(userRepository, never()).findByEmailIgnoreCaseForUpdate(anyString());
    }

    @Test
    void existingProviderBindingStillRejectsInactiveLocalAccount() {
        User user = linkedUser();
        user.setActive(false);
        stubExistingBinding(user);

        assertOauthUnregistered(() -> service.loadUser(
                request("changed@example.com", GOOGLE_SUB, true)));

        verifyNoInteractions(permissionResolver);
    }

    @Test
    void existingProviderBindingStillRejectsLockedLocalAccount() {
        User user = linkedUser();
        user.lock("security hold");
        stubExistingBinding(user);

        assertOauthUnregistered(() -> service.loadUser(
                request("changed@example.com", GOOGLE_SUB, true)));

        verifyNoInteractions(permissionResolver);
    }

    @Test
    void existingProviderBindingStillRequiresVerifiedEmailClaim() {
        assertOauthUnregistered(() -> service.loadUser(
                request("changed@example.com", GOOGLE_SUB, false)));

        verifyNoInteractions(userRepository, oauthProviderRepository, permissionResolver);
    }

    @Test
    void inconsistentProviderOwnershipMirrorIsRejectedInsteadOfRebound() {
        User user = linkedUser();
        user.setGoogleId("a-different-google-sub");
        stubExistingBinding(user);

        assertOauthUnregistered(() -> service.loadUser(
                request(LOCAL_EMAIL, GOOGLE_SUB, true)));

        verify(userRepository, never()).save(user);
        verify(permissionResolver, never()).resolvePermissions(anyLong());
    }

    @Test
    void providerBindingWhoseLocalUserIsFilteredOutIsRejected() {
        User user = linkedUser();
        UserOAuthProvider binding = new UserOAuthProvider(user, "google", GOOGLE_SUB);
        when(oauthProviderRepository.findByProviderAndProviderUserIdForUpdate(
                "google", GOOGLE_SUB)).thenReturn(Optional.of(binding));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertOauthUnregistered(() -> service.loadUser(
                request(LOCAL_EMAIL, GOOGLE_SUB, true)));

        verifyNoInteractions(permissionResolver);
    }

    @Test
    void firstLinkRechecksProviderBindingAfterLockingEmailUser() {
        User user = linkedUser();
        UserOAuthProvider binding = new UserOAuthProvider(user, "google", GOOGLE_SUB);
        when(oauthProviderRepository.findByProviderAndProviderUserIdForUpdate(
                "google", GOOGLE_SUB))
                .thenReturn(Optional.empty(), Optional.of(binding));
        when(userRepository.findByEmailIgnoreCaseForUpdate(LOCAL_EMAIL))
                .thenReturn(Optional.of(user));

        OidcUser principal = service.loadUser(request(LOCAL_EMAIL, GOOGLE_SUB, true));

        assertThat(((CustomOidcUserPrincipal) principal).getId()).isEqualTo(USER_ID);
        verify(oauthProviderRepository, times(2))
                .findByProviderAndProviderUserIdForUpdate("google", GOOGLE_SUB);
        verify(oauthProviderRepository, never()).save(binding);
    }

    private User linkedUser() {
        User user = UserFactory.newAdminCreated(
                LOCAL_EMAIL,
                "{bcrypt}test",
                "Linked OIDC User",
                Role.STUDENT,
                true,
                null,
                null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setGoogleId(GOOGLE_SUB);
        return user;
    }

    private void stubExistingBinding(User user) {
        UserOAuthProvider binding = new UserOAuthProvider(user, "google", GOOGLE_SUB);
        when(oauthProviderRepository.findByProviderAndProviderUserIdForUpdate(
                "google", GOOGLE_SUB)).thenReturn(Optional.of(binding));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    }

    private static List<String> authorityNames(OidcUser principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static void assertOauthUnregistered(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(error -> assertThat(
                        ((OAuth2AuthenticationException) error).getError().getErrorCode())
                        .isEqualTo("oauth_unregistered"));
    }

    private static OidcUserRequest request(String email, String subject,
                                           boolean emailVerified) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .scope("openid", "email", "profile")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .build();

        Instant now = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-access-token",
                now,
                now.plusSeconds(3600),
                Set.of("openid", "email", "profile"));

        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, subject);
        claims.put(IdTokenClaimNames.ISS, "https://accounts.google.com");
        claims.put(IdTokenClaimNames.AUD, List.of("test-client-id"));
        claims.put(IdTokenClaimNames.IAT, now);
        claims.put(IdTokenClaimNames.EXP, now.plusSeconds(3600));
        claims.put("email", email);
        claims.put("email_verified", emailVerified);
        claims.put("name", "OIDC Test User");

        OidcIdToken idToken = new OidcIdToken(
                "fake-id-token", now, now.plusSeconds(3600), claims);
        return new OidcUserRequest(registration, accessToken, idToken);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke();
    }
}
