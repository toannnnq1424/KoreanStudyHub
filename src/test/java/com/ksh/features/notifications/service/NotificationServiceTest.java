package com.ksh.features.notifications.service;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.mail.outbox.MailOutboxService;
import com.ksh.features.notifications.entity.Notification;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.repository.NotificationRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository =
            mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MailOutboxService mailOutboxService = mock(MailOutboxService.class);

    private final NotificationService service = new NotificationService(
            notificationRepository,
            userRepository,
            mailOutboxService
    );

    @Test
    void create_withEmailWhitelistedType_savesNotificationAndEnqueuesMail() {
        User user = user(10L, "student@ksh.test");
        Notification saved = notification(
                501L,
                10L,
                "New lesson",
                "Lesson 1 is published",
                NotificationType.LESSON_PUBLISHED
        );

        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenReturn(saved);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        Notification result = service.create(
                10L,
                "New lesson",
                "Lesson 1 is published",
                NotificationType.LESSON_PUBLISHED,
                NotificationType.REF_LESSON,
                77L
        );

        assertThat(result).isSameAs(saved);
        verify(mailOutboxService).enqueueNotification(
                501L,
                "student@ksh.test",
                "[KSH] New lesson",
                "Lesson 1 is published"
        );
    }

    @Test
    void create_withInAppOnlyType_savesNotificationWithoutMail() {
        Notification saved = notification(
                502L,
                10L,
                "Assignment graded",
                "Your score is ready",
                NotificationType.ASSIGNMENT_GRADED
        );

        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenReturn(saved);

        Notification result = service.create(
                10L,
                "Assignment graded",
                "Your score is ready",
                NotificationType.ASSIGNMENT_GRADED,
                NotificationType.REF_ASSIGNMENT,
                88L
        );

        assertThat(result).isSameAs(saved);
        verify(mailOutboxService, never()).enqueueNotification(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static User user(Long id, String email) {
        User user = UserFactory.newAdminCreated(
                email,
                "encoded-password",
                "Student User",
                Role.STUDENT,
                true,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Notification notification(
            Long id,
            Long userId,
            String title,
            String content,
            String type
    ) {
        Notification notification = new Notification(
                userId,
                title,
                content,
                type,
                NotificationType.REF_LESSON,
                77L
        );
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }
}
