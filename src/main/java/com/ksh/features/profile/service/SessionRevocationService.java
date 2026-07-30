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

    /** Expires every session for {@code username}, except the explicitly retained one. */
    public int revokeOtherSessions(String username, String keepSessionId) {
        if (username == null || username.isBlank()) return 0;

        int revoked = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!username.equals(usernameOf(principal))) continue;
            for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                if (session.getSessionId().equals(keepSessionId)) continue;
                session.expireNow();
                revoked++;
            }
        }
        if (revoked > 0) {
            log.info("Revoked {} session(s) for user {} after credential change", revoked, username);
        }
        return revoked;
    }

    private String usernameOf(Object principal) {
        if (principal instanceof KshUserDetails details) return details.getUsername();
        if (principal instanceof CustomOidcUserPrincipal oidc) return oidc.getUsername();
        return null;
    }
}
