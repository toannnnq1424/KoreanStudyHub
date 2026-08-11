package com.ksh.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompInboundAuthorizationInterceptorTest {

    private final StompInboundAuthorizationInterceptor interceptor =
            new StompInboundAuthorizationInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void websocket_configuration_installs_the_inbound_policy() {
        ChannelRegistration registration = mock(ChannelRegistration.class);
        ArgumentCaptor<ChannelInterceptor[]> captor =
                ArgumentCaptor.forClass(ChannelInterceptor[].class);

        new WebSocketConfig().configureClientInboundChannel(registration);

        verify(registration).interceptors(captor.capture());
        assertThat(captor.getValue())
                .singleElement()
                .isInstanceOf(StompInboundAuthorizationInterceptor.class);
    }

    @Test
    void rejects_client_send_to_another_users_queue() {
        Message<byte[]> frame = frame(
                StompCommand.SEND, "/user/victim@example.com/queue/messages");

        assertThatThrownBy(() -> interceptor.preSend(frame, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("message frames");
    }

    @Test
    void rejects_inbound_message_command_even_when_destination_looks_valid() {
        Message<byte[]> frame = frame(
                StompCommand.MESSAGE, StompInboundAuthorizationInterceptor.USER_MESSAGE_QUEUE);

        assertThatThrownBy(() -> interceptor.preSend(frame, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void permits_only_the_current_user_message_subscription() {
        Message<byte[]> allowed = frame(
                StompCommand.SUBSCRIBE, StompInboundAuthorizationInterceptor.USER_MESSAGE_QUEUE);

        assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/user/victim@example.com/queue/messages"), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/messages"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void leaves_connection_lifecycle_frames_untouched() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, null);
        Message<byte[]> disconnect = frame(StompCommand.DISCONNECT, null);

        assertThat(interceptor.preSend(connect, channel)).isSameAs(connect);
        assertThat(interceptor.preSend(disconnect, channel)).isSameAs(disconnect);
    }

    private static Message<byte[]> frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
