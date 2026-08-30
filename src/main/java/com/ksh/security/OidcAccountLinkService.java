package com.ksh.security;

import com.ksh.entities.User;
import com.ksh.entities.UserOAuthProvider;
import com.ksh.features.auth.repository.UserOAuthProviderRepository;
import com.ksh.features.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Resolves and, when needed, creates the durable local binding for an OIDC subject. */
@Service
public class OidcAccountLinkService {

    private static final String GOOGLE_PROVIDER = "google";

    private final UserRepository userRepository;
    private final UserOAuthProviderRepository oauthProviderRepository;

    public OidcAccountLinkService(UserRepository userRepository,
                                  UserOAuthProviderRepository oauthProviderRepository) {
        this.userRepository = userRepository;
        this.oauthProviderRepository = oauthProviderRepository;
    }

    /**
     * Resolves an existing stable subject before the mutable provider email and
     * performs first-link writes atomically.
     */
    @Transactional
    public User resolveAndLink(String email, String providerSubject) {
        Optional<UserOAuthProvider> existing = findBinding(providerSubject);

        User user;
        if (existing.isPresent()) {
            user = userRepository.findByIdForUpdate(linkedUserId(existing.get()))
                    .orElseThrow(OidcAccountLinkService::unregistered);
        } else {
            user = userRepository.findByEmailIgnoreCaseForUpdate(email)
                    .orElseThrow(OidcAccountLinkService::unregistered);

            // The locked email row serializes two first callbacks for the same
            // local account. Re-read the provider key after acquiring that lock
            // so the follower observes the winner's binding instead of inserting
            // a duplicate unique key.
            existing = findBinding(providerSubject);
            if (existing.isPresent()
                    && !linkedUserId(existing.get()).equals(user.getId())) {
                throw unregistered();
            }
        }

        if (!user.isActive() || user.isLocked()) {
            throw unregistered();
        }
        // Soft-deleted users are excluded by User's @SQLRestriction on both lookups.

        if (user.getGoogleId() != null && !user.getGoogleId().isBlank()
                && !user.getGoogleId().equals(providerSubject)) {
            throw unregistered();
        }

        if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
            user.setGoogleId(providerSubject);
            userRepository.save(user);
        }

        if (existing.isEmpty()) {
            oauthProviderRepository.save(
                    new UserOAuthProvider(user, GOOGLE_PROVIDER, providerSubject));
        }
        return user;
    }

    private Optional<UserOAuthProvider> findBinding(String providerSubject) {
        try {
            return oauthProviderRepository.findByProviderAndProviderUserIdForUpdate(
                    GOOGLE_PROVIDER, providerSubject);
        } catch (EntityNotFoundException ex) {
            // A stale provider row pointing at a filtered soft-deleted user must
            // fail closed as an authentication rejection, never as a 500 response.
            throw unregistered();
        }
    }

    private static Long linkedUserId(UserOAuthProvider binding) {
        try {
            if (binding.getUser() == null || binding.getUser().getId() == null) {
                throw unregistered();
            }
            return binding.getUser().getId();
        } catch (EntityNotFoundException ex) {
            throw unregistered();
        }
    }

    private static OAuth2AuthenticationException unregistered() {
        return new OAuth2AuthenticationException("oauth_unregistered");
    }
}
