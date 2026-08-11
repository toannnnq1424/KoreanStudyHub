package com.ksh.security;

import com.ksh.entities.User;
import com.ksh.entities.UserOAuthProvider;
import com.ksh.features.auth.repository.UserOAuthProviderRepository;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Custom OIDC user service — delegate to the default service, then enforce
 * ksh's registration rules. Only pre-registered, active, non-locked users may
 * sign in via Google OAuth. On first sign-in we link {@code users.google_id}
 * and upsert one {@code user_oauth_providers} row.
 *
 * <p>This bean is always registered. When Google sign-in is not configured
 * (no client id in {@code system_settings}), Spring Security simply never
 * dispatches an OIDC request here — see {@code DbClientRegistrationRepository}.
 *
 * <p><b>Why {@code is_active} is enforced here and not by the framework.</b>
 * {@link CustomOidcUserPrincipal} implements {@link OidcUser}, not
 * {@code UserDetails}, so Spring Security never calls an {@code isEnabled()}
 * on it the way it does for form login via {@code kshUserDetails}. Without the
 * explicit check in {@link #loadUser(OidcUserRequest)}, a deactivated account
 * could still sign in with Google. This method is the only place that gate
 * exists for the OAuth flow.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final UserOAuthProviderRepository oauthProviderRepo;

    /**
     * Constructs the service with the required repositories.
     *
     * @param userRepository    repository for looking up and persisting {@link User} records
     * @param oauthProviderRepo repository for upserting {@link UserOAuthProvider} link rows
     */
    public CustomOidcUserService(UserRepository userRepository,
                                 UserOAuthProviderRepository oauthProviderRepo) {
        this.userRepository = userRepository;
        this.oauthProviderRepo = oauthProviderRepo;
    }

    /**
     * Loads and validates the OIDC user after a successful Google sign-in.
     *
     * <p>Delegates to the default {@link OidcUserService} to fetch the user info,
     * then applies the following business rules:
     * <ul>
     *   <li>Rejects any account whose email is missing or blank.</li>
     *   <li>Rejects emails that are not pre-registered in the {@code users} table.</li>
     *   <li>Rejects accounts that are locked ({@code is_locked = true}).
     *       Soft-deleted accounts are already excluded by the {@code @SQLRestriction} on {@link User}.</li>
     *   <li>On first sign-in, stores the Google subject ID in {@code users.google_id}
     *       and activates the account if its owner had not activated it yet.</li>
     *   <li>Rejects accounts that are deactivated ({@code is_active = false}).</li>
     *   <li>Upserts a {@code user_oauth_providers} row (provider = {@code "google"})
     *       to avoid duplicates on subsequent logins.</li>
     * </ul>
     *
     * <p><b>Ordering is load-bearing — do not move the {@code is_active} check.</b>
     * It must run <i>after</i> the {@code google_id} linking block, never before.
     * An account that has never linked a Google identity may legitimately be
     * inactive: that is the first-sign-in activation path for accounts created
     * by the admin import, which start at {@code is_active = 0}. An account
     * whose {@code google_id} was already linked and which is now inactive was
     * deactivated by an administrator and must be rejected. An empty
     * {@code google_id} is the only thing distinguishing the two cases, so the
     * link must happen first. Moving this check earlier rejects every imported
     * account; moving it later lets deactivated accounts through.
     *
     * @param userRequest the OIDC user request containing the access token and client registration
     * @return a {@link CustomOidcUserPrincipal} wrapping the OIDC user and the local {@link User}
     * @throws OAuth2AuthenticationException with error code {@code "oauth_unregistered"} if the
     *         email is absent, not found in the database, or the account is locked or deactivated
     */
    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("oauth_unregistered");
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            throw new OAuth2AuthenticationException("oauth_unregistered");
        }

        User user = userOpt.get();
        if (user.isLocked()) {
            throw new OAuth2AuthenticationException("oauth_unregistered");
        }
        // is_deleted already filtered by @SQLRestriction

        // Link google_id if not yet set. A blank google_id means this is the
        // first-ever Google sign-in, which is itself an activation path — so
        // this block runs before the is_active gate below.
        String googleSub = oidcUser.getSubject();
        boolean firstGoogleLink = user.getGoogleId() == null || user.getGoogleId().isBlank();
        if (firstGoogleLink) {
            user.setGoogleId(googleSub);
            // First-ever Google sign-in is itself an activation path. markActivated
            // keeps an already-set activated_at, so an account that activated by
            // emailed link earlier never has its recorded moment moved forward.
            user.markActivated(LocalDateTime.now());
            userRepository.save(user);
        }

        // Reject a deactivated account, unless it just linked Google for the
        // first time. See the method Javadoc — this must stay after the block
        // above, otherwise every not-yet-activated account is rejected.
        if (!firstGoogleLink && !user.isActive()) {
            throw new OAuth2AuthenticationException("oauth_unregistered");
        }

        // Upsert oauth provider row (no duplicate)
        Optional<UserOAuthProvider> existing = oauthProviderRepo
                .findByProviderAndProviderUserId("google", googleSub);
        if (existing.isEmpty()) {
            oauthProviderRepo.save(new UserOAuthProvider(user, "google", googleSub));
        }

        // Build OidcUser with correct authorities from the user's role
        return new CustomOidcUserPrincipal(oidcUser, user);
    }
}
