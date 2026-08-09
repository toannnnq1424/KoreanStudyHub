package com.ksh.features.classes;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.JoinClassService;
import com.ksh.features.notifications.entity.Notification;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.repository.NotificationRepository;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration boundary: class catalog + enrollment lifecycle + notification
 * persistence. This deliberately uses the real Spring services and MySQL
 * repositories rather than mocking either downstream module.
 */
@SpringBootTest
@Transactional
class ClassJoinApprovalNotificationIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private JoinClassService joinClassService;

    private User owner;
    private User student;
    private ClassEntity activeClass;

    @BeforeEach
    void setUp() {
        owner = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();

        activeClass = new ClassEntity(
                "IT-CJNA-" + System.nanoTime(), owner.getId(), owner.getId(),
                "Transactional integration fixture", null, null, 20);
        activeClass.setSubjectId(owner.getSubjectId());
        activeClass.approve(owner.getId(), LocalDateTime.now());
        activeClass = classRepository.saveAndFlush(activeClass);
    }

    @Test
    void request_then_owner_approval_persists_enrollment_and_both_notification_boundaries() {
        long ownerUnreadBefore = notificationService.unreadCount(owner.getId());
        long studentUnreadBefore = notificationService.unreadCount(student.getId());

        JoinClassService.JoinResult requested = joinClassService.requestJoin(
                activeClass.getId(), student.getId());

        assertThat(requested).isInstanceOf(JoinClassService.PendingRequested.class);
        Enrollment pending = enrollmentRepository.findByUserIdAndClassId(student.getId(), activeClass.getId())
                .orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(Enrollment.STATUS_PENDING);
        assertThat(notificationService.unreadCount(owner.getId())).isEqualTo(ownerUnreadBefore + 1);
        assertThat(notificationsFor(owner.getId())).anySatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.JOIN_REQUEST);
            assertThat(notification.getReferenceType()).isEqualTo(NotificationType.REF_CLASS);
            assertThat(notification.getReferenceId()).isEqualTo(activeClass.getId());
        });

        joinClassService.approve(activeClass.getId(), student.getId(), owner.getId(), Role.LECTURER);

        Enrollment active = enrollmentRepository.findByUserIdAndClassId(student.getId(), activeClass.getId())
                .orElseThrow();
        assertThat(active.getStatus()).isEqualTo(Enrollment.STATUS_ACTIVE);
        assertThat(notificationService.unreadCount(student.getId())).isEqualTo(studentUnreadBefore + 2);
        assertThat(notificationsFor(student.getId()).stream().map(Notification::getType))
                .contains(NotificationType.JOIN_APPROVED, NotificationType.CLASS_ENROLLED);
    }

    private List<Notification> notificationsFor(Long recipientId) {
        return notificationRepository.findAll().stream()
                .filter(notification -> recipientId.equals(notification.getUserId()))
                .filter(notification -> activeClass.getId().equals(notification.getReferenceId()))
                .toList();
    }
}
