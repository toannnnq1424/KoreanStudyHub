package com.ksh.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;

/**
 * Fail-closed policy for frames received from browser STOMP clients.
 *
 * <p>KSH has no {@code @MessageMapping} write API. Messages are persisted by
 * authenticated HTTP endpoints and only the server publishes broker frames.
 * A browser therefore has no legitimate reason to SEND a frame. Subscriptions
 * are likewise limited to the unresolved current-user queue so a client cannot
 * name another user or listen to a shared broker destination.
 */
final class StompInboundAuthorizationInterceptor implements ChannelInterceptor {

    static final String USER_MESSAGE_QUEUE = "/user/queue/messages";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        if (StompCommand.SEND.equals(command) || StompCommand.MESSAGE.equals(command)) {
            throw new AccessDeniedException("Client STOMP message frames are not allowed");
        }
        if (StompCommand.SUBSCRIBE.equals(command)
                && !USER_MESSAGE_QUEUE.equals(accessor.getDestination())) {
            throw new AccessDeniedException("STOMP subscription destination is not allowed");
        }
        return message;
    }
}
