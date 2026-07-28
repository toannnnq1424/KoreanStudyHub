package com.ksh.features.notifications.service;

import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.mail.outbox.MailOutboxService;
import com.ksh.features.notifications.dto.NotificationDtos.NotificationRow;
import com.ksh.features.notifications.entity.Notification;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Application service for in-app notifications (Sprint 5, #63/#64).
 *
 * <p>Owns creation (with durable email enqueue for whitelisted types), listing,
 * unread-count, and owner-scoped mark-read. Entities never leak past this layer.
 *
 * <p>The notification and its email outbox job are persisted atomically.
 * SMTP delivery happens asynchronously; {@code is_email_sent} is set only
 * after the outbox worker records a successful delivery.
 */
@Service
public class NotificationService {

    /** Page size for the notifications list. */
    static final int NOTIFICATION_PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailOutboxService mailOutboxService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               MailOutboxService mailOutboxService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.mailOutboxService = mailOutboxService;
    }

    // ── Creation ────────────────────────────────────────────────────────

    /**
     * Persists a new notification and, when the type is email-whitelisted
     * ({@link NotificationType#EMAIL_TYPES}), atomically enqueues a durable
     * email job for the recipient.
     *
     * <p>SMTP availability is not consulted in this request transaction.
     *
     * @param userId        the recipient's user id
     * @param title         short notification title (Vietnamese UI text)
     * @param content       longer notification body (Vietnamese UI text)
     * @param type          notification type constant from {@link NotificationType}
     * @param referenceType optional reference domain type (e.g. "CLASS", "LESSON")
     * @param referenceId   optional reference domain id
     * @return the persisted notification row
     */
    @Transactional
    public Notification create(Long userId, String title, String content,
                               String type, String referenceType, Long referenceId) {
        Notification notification = new Notification(userId, title, content,
                type, referenceType, referenceId);
        Notification saved = notificationRepository.save(notification);

        // Preserve the existing whitelist. Delivery is handled asynchronously.
        if (NotificationType.EMAIL_TYPES.contains(type)) {
            userRepository.findById(userId).ifPresent(user -> {
                String recipient = user.getEmail();
                if (recipient != null && !recipient.isBlank()) {
                    mailOutboxService.enqueueNotification(
                            saved.getId(),
                            recipient,
                            "[KSH] " + title,
                            content);
                }
            });
        }

        return saved;
    }

    // ── Listing ──────────────────────────────────────────────────────────

    /**
     * Returns a page of the caller's notifications, newest first.
     *
     * @param userId the caller's user id
     * @param page   zero-based page index
     * @return a page of {@link NotificationRow}s
     */
    @Transactional(readOnly = true)
    public Page<NotificationRow> listForUser(Long userId, int page) {
        Pageable pageable = PageRequest.of(Math.max(0, page), NOTIFICATION_PAGE_SIZE);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toRow);
    }

    // ── Unread count ─────────────────────────────────────────────────────

    /**
     * The caller's total unread notification count for the header badge.
     *
     * @param userId the caller's user id
     * @return total unread notifications
     */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // ── Mark read ────────────────────────────────────────────────────────

    /**
     * Marks a notification as read (owner-scoped, no-leak). If the id does not
     * exist or belongs to a different user, this is a silent no-op — no
     * modification, no error, no existence leak.
     *
     * @param userId   the caller's user id
     * @param notifId  the notification id to mark read
     */
    @Transactional
    public void markRead(Long userId, Long notifId) {
        notificationRepository.findByIdAndUserId(notifId, userId).ifPresent(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        });
    }

    /**
     * Returns the notification if it belongs to the caller, or empty otherwise
     * (used by the controller to build the redirect target after marking read).
     *
     * @param userId  the caller's user id
     * @param notifId the notification id
     * @return the row DTO, or empty if absent or foreign
     */
    @Transactional(readOnly = true)
    public java.util.Optional<NotificationRow> findOwned(Long userId, Long notifId) {
        return notificationRepository.findByIdAndUserId(notifId, userId).map(this::toRow);
    }

    // ── Helper ───────────────────────────────────────────────────────────

    /** Maps a {@link Notification} entity to a list-row DTO. */
    private NotificationRow toRow(Notification n) {
        return new NotificationRow(
                n.getId(), n.getTitle(), n.getContent(), n.getType(),
                n.getReferenceType(), n.getReferenceId(),
                n.isRead(), n.getReadAt(), n.getCreatedAt());
    }
}
