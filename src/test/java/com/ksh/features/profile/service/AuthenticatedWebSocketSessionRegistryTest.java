package com.ksh.features.profile.service;

import com.ksh.security.KshUserDetails;
import com.ksh.security.AuthenticatedAccessVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticatedWebSocketSessionRegistryTest {

    private final AuthenticatedAccessVersionService accessVersions =
            mock(AuthenticatedAccessVersionService.class);
    private final AuthenticatedWebSocketSessionRegistry registry =
            new AuthenticatedWebSocketSessionRegistry(accessVersions);

    @Test
    void closesEveryTransportForStableUserId() throws Exception {
        WebSocketSession first = socket("ws-1", 42L, "member@example.test", "http-1");
        WebSocketSession second = socket("ws-2", 42L, "member@example.test", "http-2");
        when(accessVersions.isCurrent(42L, 7L)).thenReturn(true);
        registry.register(first);
        registry.register(second);

        assertThat(registry.closeAll(42L)).isEqualTo(2);

        verify(first).close(revokedCloseStatus());
        verify(second).close(revokedCloseStatus());
        assertThat(registry.closeAll(42L)).isZero();
    }

    @Test
    void retainingCurrentHttpSessionClosesOnlyOtherBrowserSockets() throws Exception {
        WebSocketSession current = socket("ws-current", 42L,
                "member@example.test", "http-current");
        WebSocketSession compromised = socket("ws-old", 42L,
                "member@example.test", "http-old");
        when(accessVersions.isCurrent(42L, 7L)).thenReturn(true);
        registry.register(current);
        registry.register(compromised);

        assertThat(registry.closeOther(
                "member@example.test", "http-current")).isOne();

        verify(current, never()).close(org.mockito.ArgumentMatchers.any());
        verify(compromised).close(revokedCloseStatus());
    }

    @Test
    void unregisterMakesNormalClosureIdempotent() throws Exception {
        WebSocketSession socket = socket("ws-1", 42L,
                "member@example.test", "http-1");
        when(accessVersions.isCurrent(42L, 7L)).thenReturn(true);
        registry.register(socket);
        registry.unregister("ws-1");

        assertThat(registry.closeAll(42L)).isZero();
        verify(socket, never()).close(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stalePrincipalIsClosedEvenWhenItRegistersAfterRevocationSweep() throws Exception {
        WebSocketSession stale = socket("ws-late", 42L,
                "member@example.test", "http-late");
        when(accessVersions.isCurrent(42L, 7L)).thenReturn(false);

        assertThat(registry.register(stale)).isFalse();

        verify(stale).close(revokedCloseStatus());
        assertThat(registry.closeAll(42L)).isZero();
    }

    @Test
    void destroyedHttpSessionRejectsTransportThatRegistersAfterLogoutSweep() throws Exception {
        HttpSessionDestroyedEvent logout = mock(HttpSessionDestroyedEvent.class);
        when(logout.getId()).thenReturn("http-late");
        registry.onHttpSessionDestroyed(logout);

        WebSocketSession late = socket("ws-late", 42L,
                "member@example.test", "http-late");
        when(accessVersions.isCurrent(42L, 7L)).thenReturn(true);

        assertThat(registry.register(late)).isFalse();

        verify(late).close(revokedCloseStatus());
        verify(accessVersions, never()).isCurrent(42L, 7L);
        assertThat(registry.closeAll(42L)).isZero();
    }

    @Test
    void missingHttpSessionIdentityIsRejectedFailClosed() throws Exception {
        WebSocketSession uncorrelated = socket("ws-uncorrelated", 42L,
                "member@example.test", null);

        assertThat(registry.register(uncorrelated)).isFalse();

        verify(uncorrelated).close(revokedCloseStatus());
        verify(accessVersions, never()).isCurrent(42L, 7L);
        assertThat(registry.closeAll(42L)).isZero();
    }

    @Test
    void logoutDuringRegistrationClosesTransportAndRegistrationFails() throws Exception {
        WebSocketSession racing = socket("ws-racing", 42L,
                "member@example.test", "http-racing");
        CountDownLatch versionCheckStarted = new CountDownLatch(1);
        CountDownLatch releaseVersionCheck = new CountDownLatch(1);
        when(accessVersions.isCurrent(42L, 7L)).thenAnswer(ignored -> {
            versionCheckStarted.countDown();
            assertThat(releaseVersionCheck.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();

        try {
            Future<Boolean> registration = worker.submit(() -> registry.register(racing));
            assertThat(versionCheckStarted.await(5, TimeUnit.SECONDS)).isTrue();

            HttpSessionDestroyedEvent logout = mock(HttpSessionDestroyedEvent.class);
            when(logout.getId()).thenReturn("http-racing");
            registry.onHttpSessionDestroyed(logout);
            releaseVersionCheck.countDown();

            assertThat(registration.get(5, TimeUnit.SECONDS)).isFalse();
            verify(racing).close(revokedCloseStatus());
            assertThat(registry.closeAll(42L)).isZero();
        } finally {
            releaseVersionCheck.countDown();
            worker.shutdownNow();
            worker.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static WebSocketSession socket(String socketId, Long userId,
                                           String username, String httpSessionId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn(username);
        when(principal.getSecurityVersion()).thenReturn(7L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME,
                httpSessionId);
        when(socket.getId()).thenReturn(socketId);
        when(socket.getPrincipal()).thenReturn(authentication);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

    private static CloseStatus revokedCloseStatus() {
        return argThat(status -> status != null
                && status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
                && "Authentication revoked".equals(status.getReason()));
    }
}
