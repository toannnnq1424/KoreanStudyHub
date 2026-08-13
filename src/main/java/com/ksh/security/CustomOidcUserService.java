package com.ksh.security;

import com.ksh.entities.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Custom OIDC user service — delegate to the default service, then enforce
 * KSH's registration rules. Only pre-registered, active, non-locked users may
 * sign in via Google OAuth. Existing provider-subject bindings are resolved
 * before the mutable provider email. On first sign-in we link
 * {@code users.google_id} and create one {@code user_oauth_providers} row.
 *
 * <p>This bean is always registered. When Google sign-in is not configured
 * (no client id in {@code system_settings}), Spring Security simply never
 * dispatches an OIDC request here — see {@code DbClientRegistrationRepository}.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private final OidcAccountLinkService accountLinkService;
    private final LoginPermissionResolver loginPermissionResolver;

    /**
     * Constructs the service with the required repositories.
     *
     * @param accountLinkService      transactional local-account binding service
     * @param loginPermissionResolver fail-soft login permission enrichment
     */
    public CustomOidcUserService(OidcAccountLinkService accountLinkService,
                                 LoginPermissionResolver loginPermissionResolver) {
        this.accountLinkService = accountLinkService;
        this.loginPermissionResolver = loginPermissionResolver;
    }

    /**
     * Loads and validates the OIDC user after a successful Google sign-in.
     *
     * <p>Delegates to the default {@link OidcUserService} to fetch the user info,
     * then applies the following business rules:
     * <ul>
     *   <li>Rejects any account whose email is missing or blank.</li>
     *   <li>Resolves an existing provider-subject binding before consulting email.</li>
     *   <li>For a first-time provider subject, rejects emails that are not
     *       pre-registered in the {@code users} table.</li>
     *   <li>Rejects accounts that are inactive or locked.
     *       Soft-deleted accounts are already excluded by the {@code @SQLRestriction} on {@link User}.</li>
     *   <li>On first sign-in, stores the Google subject ID in {@code users.google_id}.</li>
     *   <li>Upserts a {@code user_oauth_providers} row (provider = {@code "google"})
     *       to avoid duplicates on subsequent logins.</li>
     * </ul>
     *
     * @param userRequest the OIDC user request containing the access token and client registration
     * @return a {@link CustomOidcUserPrincipal} wrapping the OIDC user and the local {@link User}
     * @throws OAuth2AuthenticationException with error code {@code "oauth_unregistered"} if the
     *         verified identity is absent, a first-time email is not registered, the account is
     *         inactive/locked, or the stored provider ownership is inconsistent
     */
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()
                || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuth2AuthenticationException("oauth_unregistered");
        }

        String googleSub = oidcUser.getSubject();
        if (googleSub == null || googleSub.isBlank()) {
            throw new OAuth2AuthenticationException("oauth_unregistered");
        }

        // Identity linking owns its transaction. LoginPermissionResolver suspends any
        // ambient caller transaction before RBAC enrichment, so an RBAC rollback cannot
        // poison either the link transaction or an authentication caller transaction.
        User user = accountLinkService.resolveAndLink(email, googleSub);

        // Match form-login authority semantics: role plus effective RBAC permissions.
        LoginPermissionResolver.PermissionSnapshot permissions =
                loginPermissionResolver.resolveSnapshotSafely(user.getId());
        return new CustomOidcUserPrincipal(
                oidcUser, user, permissions.featureKeys(), permissions.validUntil());
    }
}
