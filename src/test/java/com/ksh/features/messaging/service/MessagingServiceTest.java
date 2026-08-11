package com.ksh.features.messaging.service;

import com.ksh.entities.Conversation;
import com.ksh.entities.Message;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.messaging.dto.MessagingDtos.SendResult;
import com.ksh.features.messaging.repository.ConversationRepository;
import com.ksh.features.messaging.repository.MessageRepository;
import com.ksh.features.messaging.support.MessagingAccess;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingServiceTest {

    private final ConversationRepository conversationRepository =
            mock(ConversationRepository.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MessagingAccess access = mock(MessagingAccess.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ClassRepository classRepository = mock(ClassRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final DepartmentRepository subjectRepository = mock(DepartmentRepository.class);

    private final MessagingService service = new MessagingService(
            conversationRepository,
            messageRepository,
            userRepository,
            access,
            messagingTemplate,
            classRepository,
            enrollmentRepository,
            subjectRepository
    );

    @Test
    void getOrCreateConversation_withEligibleRecipient_returnsExistingConversationId() {
        Conversation conversation = conversation(100L, 10L, 20L);

        when(access.canStartConversation(20L, Role.STUDENT, 10L)).thenReturn(true);
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user(10L, "a@ksh.test")));
        when(conversationRepository.findByUserLoIdAndUserHiId(10L, 20L))
                .thenReturn(Optional.of(conversation));

        Long result = service.getOrCreateConversation(20L, Role.STUDENT, 10L);

        assertThat(result).isEqualTo(100L);
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void getOrCreateConversation_withIneligibleRecipient_throwsNotFound() {
        when(access.canStartConversation(20L, Role.STUDENT, 99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getOrCreateConversation(20L, Role.STUDENT, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void send_withValidInput_returnsSavedMessageResultAndPushesToPeer() {
        Conversation conversation = conversation(100L, 10L, 20L);
        Message saved = message(500L, 100L, 10L, "Hello lecturer");
        User peer = user(20L, "lecturer@ksh.test");
        User sender = user(10L, "student@ksh.test");

        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        when(messageRepository.saveAndFlush(any(Message.class))).thenReturn(saved);
        when(messageRepository.countUnreadForUser(20L)).thenReturn(3L);
        when(userRepository.findById(20L)).thenReturn(Optional.of(peer));
        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));

        SendResult result = service.send(10L, 100L, "  Hello lecturer  ");

        assertThat(result.messageId()).isEqualTo(500L);
        assertThat(result.convId()).isEqualTo(100L);
        assertThat(result.body()).isEqualTo("Hello lecturer");
        assertThat(result.peerUnread()).isEqualTo(3L);
        verify(conversationRepository).touchLastMessageAt(eq(100L), any());
        verify(messagingTemplate).convertAndSendToUser(
                eq("lecturer@ksh.test"),
                eq("/queue/messages"),
                any()
        );
    }

    @Test
    void send_withBlankBody_throwsBadRequestAndDoesNotSave() {
        Conversation conversation = conversation(100L, 10L, 20L);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.send(10L, 100L, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(messageRepository, never()).saveAndFlush(any(Message.class));
    }

    @Test
    void markConversationRead_withParticipant_returnsUnreadCount() {
        Conversation conversation = conversation(100L, 10L, 20L);

        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        when(messageRepository.countUnreadForUser(10L)).thenReturn(0L);

        long result = service.markConversationRead(10L, 100L);

        assertThat(result).isZero();
        verify(messageRepository).markReadBulk(eq(100L), eq(10L), any());
    }

    private static Conversation conversation(Long id, Long userA, Long userB) {
        Conversation conversation = Conversation.between(userA, userB);
        ReflectionTestUtils.setField(conversation, "id", id);
        return conversation;
    }

    private static Message message(Long id, Long conversationId, Long senderId, String body) {
        Message message = new Message(conversationId, senderId, body);
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());
        return message;
    }

    private static User user(Long id, String email) {
        User user = UserFactory.newAdminCreated(
                email,
                "encoded-password",
                "Test User",
                Role.STUDENT,
                true,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
