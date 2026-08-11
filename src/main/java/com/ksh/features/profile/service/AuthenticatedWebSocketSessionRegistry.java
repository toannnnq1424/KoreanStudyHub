package com.ksh.features.profile.service;

import com.ksh.security.KshUserDetails;
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

    /** Registers one established transport when it has an authenticated local principal. */
    public void register(WebSocketSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        Identity identity = identityOf(session.getPrincipal());
        if (identity.userId() == null
                && (identity.username() == null || identity.username().isBlank())) {
            return;
        }
        Object httpSessionId = session.getAttributes().get(
                HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
        sessions.put(session.getId(), new SessionBinding(
                session,
                identity.userId(),
                identity.username(),
                httpSessionId instanceof String value ? value : null));
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
            if (!sessions.remove(entry.getKey(), binding)) {
                continue;
            }
            try {
                if (binding.session().isOpen()) {
                    binding.session().close(ACCESS_REVOKED);
                    closed++;
                }
            } catch (IOException ex) {
                log.debug("Could not close revoked WebSocket session {}",
                        entry.getKey(), ex);
            }
        }
        return closed;
    }

    private static Identity identityOf(Principal connectionPrincipal) {
        Object principal = connectionPrincipal;
        if (connectionPrincipal instanceof Authentication authentication) {
            principal = authentication.getPrincipal();
        }
        if (principal instanceof KshUserDetails details) {
            return new Identity(details.getId(), details.getUsername());
        }
        return new Identity(null,
                connectionPrincipal == null ? null : connectionPrincipal.getName());
    }

    private record Identity(Long userId, String username) {
    }

    private record SessionBinding(WebSocketSession session,
                                  Long userId,
                                  String username,
                                  String httpSessionId) {
    }
}
