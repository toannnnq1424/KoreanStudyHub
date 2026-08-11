package com.ksh.features.profile.service;

import com.ksh.security.KshUserDetails;
import com.ksh.security.AuthenticatedAccessVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Tracks authenticated WebSocket transports so access revocation can close them. */
@Component
public class AuthenticatedWebSocketSessionRegistry {

    private static final Logger log =
            LoggerFactory.getLogger(AuthenticatedWebSocketSessionRegistry.class);
    private static final CloseStatus ACCESS_REVOKED =
            CloseStatus.POLICY_VIOLATION.withReason("Authentication revoked");

    private final Map<String, SessionBinding> sessions = new ConcurrentHashMap<>();
    private final AuthenticatedAccessVersionService accessVersions;

    public AuthenticatedWebSocketSessionRegistry(
            AuthenticatedAccessVersionService accessVersions) {
        this.accessVersions = accessVersions;
    }

    /** Registers one established transport when it has an authenticated local principal. */
    public boolean register(WebSocketSession session) {
        if (session == null || session.getId() == null) {
            return false;
        }
        Identity identity = identityOf(session.getPrincipal());
        Object httpSessionId = session.getAttributes().get(
                HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
        SessionBinding binding = new SessionBinding(
                session,
                identity.userId(),
                identity.username(),
                httpSessionId instanceof String value ? value : null);
        sessions.put(session.getId(), binding);
        try {
            if (identity.userId() == null
                    || !accessVersions.isCurrent(
                    identity.userId(), identity.securityVersion())) {
                closeBinding(session.getId(), binding);
                return false;
            }
        } catch (RuntimeException ex) {
            closeBinding(session.getId(), binding);
            throw ex;
        }
        return true;
    }

    /** Removes a transport after normal or exceptional connection closure. */
    public void unregister(String webSocketSessionId) {
        if (webSocketSessionId != null) {
            sessions.remove(webSocketSessionId);
        }
    }

    /** Closes every socket belonging to a stable local user id. */
    public int closeAll(Long userId) {
        if (userId == null) {
            return 0;
        }
        return closeMatching(binding -> userId.equals(binding.userId()), null);
    }

    /** Closes every socket belonging to a username/email. */
    public int closeAll(String username) {
        return closeOther(username, null);
    }

    /** Closes username sockets except those established from one retained HTTP session. */
    public int closeOther(String username, String keepHttpSessionId) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        return closeMatching(
                binding -> username.equalsIgnoreCase(binding.username()),
                keepHttpSessionId);
    }

    /** A destroyed/logout HTTP session must not leave its STOMP transport alive. */
    @EventListener
    public void onHttpSessionDestroyed(HttpSessionDestroyedEvent event) {
        if (event != null) {
            closeMatching(binding -> event.getId().equals(binding.httpSessionId()), null);
        }
    }

    private int closeMatching(Predicate<SessionBinding> predicate,
                              String keepHttpSessionId) {
        int closed = 0;
        for (Map.Entry<String, SessionBinding> entry : sessions.entrySet()) {
            SessionBinding binding = entry.getValue();
            if (!predicate.test(binding)
                    || (keepHttpSessionId != null
                    && keepHttpSessionId.equals(binding.httpSessionId()))) {
                continue;
            }
            closed += closeBinding(entry.getKey(), binding);
        }
        return closed;
    }

    private int closeBinding(String sessionId, SessionBinding binding) {
        if (!sessions.remove(sessionId, binding)) {
            return 0;
        }
        try {
            if (binding.session().isOpen()) {
                binding.session().close(ACCESS_REVOKED);
                return 1;
            }
        } catch (IOException ex) {
            log.debug("Could not close revoked WebSocket session {}", sessionId, ex);
        }
        return 0;
    }

    private static Identity identityOf(Principal connectionPrincipal) {
        Object principal = connectionPrincipal;
        if (connectionPrincipal instanceof Authentication authentication) {
            principal = authentication.getPrincipal();
        }
        if (principal instanceof KshUserDetails details) {
            return new Identity(
                    details.getId(), details.getUsername(), details.getSecurityVersion());
        }
        return new Identity(null,
                connectionPrincipal == null ? null : connectionPrincipal.getName(), 0L);
    }

    private record Identity(Long userId, String username, long securityVersion) {
    }

    private record SessionBinding(WebSocketSession session,
                                  Long userId,
                                  String username,
                                  String httpSessionId) {
    }
}
