package com.ksh.config;

import com.ksh.features.profile.service.AuthenticatedWebSocketSessionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * STOMP-over-WebSocket configuration for real-time messaging (Epic #13, KSH-8.3).
 *
 * <p>The handshake at {@code /ws} rides the existing form-login HTTP session, so
 * the Spring Security principal is already present — no separate WS token is
 * needed (design decision D4). {@code SecurityConfig} requires authentication on
 * {@code /ws/**}. Messages are pushed to a specific user via
 * {@code SimpMessagingTemplate.convertAndSendToUser(email, "/queue/messages", ...)},
 * which resolves through the {@code /user} destination prefix registered here.
 * Clients may only subscribe to their own unresolved user destination; all
 * client SEND/MESSAGE frames are rejected because application writes use the
 * authenticated HTTP messaging endpoints.
 *
 * <p>Uses the in-memory simple broker ({@code /topic}, {@code /queue}). This is
 * single-instance only; a full broker relay is a later scaling step (out of scope).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthenticatedWebSocketSessionRegistry webSocketSessions;

    public WebSocketConfig(AuthenticatedWebSocketSessionRegistry webSocketSessions) {
        this.webSocketSessions = webSocketSessions;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        HttpSessionHandshakeInterceptor httpSessionIdentity =
                new HttpSessionHandshakeInterceptor();
        httpSessionIdentity.setCopyAllAttributes(false);
        httpSessionIdentity.setCopyHttpSessionId(true);
        httpSessionIdentity.setCreateSession(false);
        // SockJS fallback for browsers without native WebSocket support.
        registry.addEndpoint("/ws")
                .addInterceptors(httpSessionIdentity)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        // convertAndSendToUser targets /user/{username}/queue/messages under this prefix.
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompInboundAuthorizationInterceptor());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(delegate -> new WebSocketHandlerDecorator(delegate) {
            @Override
            public void afterConnectionEstablished(
                    org.springframework.web.socket.WebSocketSession session) throws Exception {
                webSocketSessions.register(session);
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception ex) {
                    webSocketSessions.unregister(session.getId());
                    throw ex;
                }
            }

            @Override
            public void afterConnectionClosed(
                    org.springframework.web.socket.WebSocketSession session,
                    org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    webSocketSessions.unregister(session.getId());
                }
            }
        });
    }
}
