package com.ksh.features.mail.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Transactional enqueue facade used by notification creation.
 */
@Service
public class MailOutboxService {

    static final String SOURCE_NOTIFICATION = "NOTIFICATION";

    private final MailOutboxRepository repository;
    private final Clock clock;

    @Autowired
    public MailOutboxService(MailOutboxRepository repository) {
        this(repository, Clock.systemUTC());
    }

    MailOutboxService(MailOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Adds one durable email request for a persisted notification.
     *
     * <p>{@link Propagation#MANDATORY} guarantees callers cannot accidentally
     * commit a notification without its matching outbox row.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueNotification(
            Long notificationId,
            String recipientEmail,
            String subject,
            String body) {
        if (repository.existsByNotificationId(notificationId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        repository.save(MailOutboxJob.pending(
                notificationId,
                recipientEmail,
                subject,
                body,
                SOURCE_NOTIFICATION,
                now));
    }
}
