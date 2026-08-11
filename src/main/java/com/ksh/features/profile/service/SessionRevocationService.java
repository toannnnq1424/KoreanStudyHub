package com.ksh.features.profile.service;

import com.ksh.security.CustomOidcUserPrincipal;
import com.ksh.security.KshUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/** Revokes authenticated sessions that were established with an old credential. */
@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);
    private final SessionRegistry sessionRegistry;

    public SessionRevocationService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /** Expires every session for {@code username}. */
    public int revokeAllSessions(String username) {
        return revokeSessions(null, username, null);
    }

    /** Expires every session whose local principal belongs to {@code userId}. */
    public int revokeAllSessions(Long userId) {
        if (userId == null) return 0;
        return revokeSessions(userId, null, null);
    }

    /** Expires every session for {@code username}, except the explicitly retained one. */
    public int revokeOtherSessions(String username, String keepSessionId) {
        return revokeSessions(null, username, keepSessionId);
    }

    private int revokeSessions(Long userId, String username, String keepSessionId) {
        if (userId == null && (username == null || username.isBlank())) return 0;

        int revoked = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!matches(principal, userId, username)) continue;
            for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                if (keepSessionId != null && session.getSessionId().equals(keepSessionId)) continue;
                session.expireNow();
                revoked++;
            }
        }
        if (revoked > 0) {
            log.info("Revoked {} session(s) for user {} after access change",
                    revoked, userId != null ? userId : username);
        }
        return revoked;
    }

    private String usernameOf(Object principal) {
        if (principal instanceof KshUserDetails details) return details.getUsername();
        if (principal instanceof CustomOidcUserPrincipal oidc) return oidc.getUsername();
        return null;
    }

    private boolean matches(Object principal, Long userId, String username) {
        if (userId != null) {
            if (principal instanceof KshUserDetails details) return userId.equals(details.getId());
            if (principal instanceof CustomOidcUserPrincipal oidc) return userId.equals(oidc.getId());
            return false;
        }
        return username.equals(usernameOf(principal));
    }
}
