package com.ksh.features.mail.outbox;

import com.ksh.features.notifications.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Short database transactions around claim and completion transitions.
 *
 * <p>SMTP calls deliberately happen outside these methods so a slow relay does
 * not retain a database lock or transaction.
 */
@Service
public class MailOutboxTransactionService {

    static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    static final Duration INITIAL_RETRY_DELAY = Duration.ofMinutes(1);
    static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    static final String ERROR_DELIVERY_FAILED = "delivery_failed";
    static final String ERROR_ATTEMPTS_EXHAUSTED = "attempts_exhausted";

    private static final List<MailOutboxStatus> READY_STATUSES =
            List.of(MailOutboxStatus.PENDING, MailOutboxStatus.RETRY);

    private final MailOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Autowired
    public MailOutboxTransactionService(
            MailOutboxRepository outboxRepository,
            NotificationRepository notificationRepository) {
        this(outboxRepository, notificationRepository, Clock.systemUTC());
    }

    MailOutboxTransactionService(
            MailOutboxRepository outboxRepository,
            NotificationRepository notificationRepository,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> findClaimableIds(int batchSize) {
        int boundedBatchSize = Math.max(1, Math.min(batchSize, 100));
        return outboxRepository.findClaimableIds(
                READY_STATUSES,
                MailOutboxStatus.PROCESSING,
                now(),
                PageRequest.of(0, boundedBatchSize));
    }

    @Transactional
    public Optional<MailOutboxDelivery> claim(Long jobId, String workerId) {
        LocalDateTime claimedAt = now();
        return outboxRepository.findByIdForUpdate(jobId).flatMap(job -> {
            if (!job.isClaimable(claimedAt)) {
                return Optional.empty();
            }
            if (job.attemptsExhausted()) {
                job.markFailed(claimedAt, ERROR_ATTEMPTS_EXHAUSTED);
                outboxRepository.save(job);
                return Optional.empty();
            }

            job.claim(workerId, claimedAt, LEASE_DURATION);
            outboxRepository.save(job);
            return Optional.of(new MailOutboxDelivery(
                    job.getId(),
                    job.getRecipientEmail(),
                    job.getSubject(),
                    job.getBody()));
        });
    }

    @Transactional
    public boolean recordSuccess(Long jobId, String workerId) {
        LocalDateTime completedAt = now();
        return outboxRepository.findByIdForUpdate(jobId).map(job -> {
            if (!job.isOwnedBy(workerId)) {
                return false;
            }
            job.markSent(completedAt);
            outboxRepository.save(job);

            if (job.getNotificationId() != null) {
                notificationRepository.findById(job.getNotificationId()).ifPresent(notification -> {
                    notification.setEmailSent(true);
                    notificationRepository.save(notification);
                });
            }
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean recordFailure(Long jobId, String workerId, String errorCode) {
        LocalDateTime failedAt = now();
        return outboxRepository.findByIdForUpdate(jobId).map(job -> {
            if (!job.isOwnedBy(workerId)) {
                return false;
            }
            if (job.attemptsExhausted()) {
                job.markFailed(failedAt, errorCode);
            } else {
                job.scheduleRetry(
                        failedAt,
                        failedAt.plus(retryDelay(job.getAttemptCount())),
                        errorCode);
            }
            outboxRepository.save(job);
            return true;
        }).orElse(false);
    }

    static Duration retryDelay(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 10));
        Duration candidate = INITIAL_RETRY_DELAY.multipliedBy(1L << exponent);
        return candidate.compareTo(MAX_RETRY_DELAY) > 0
                ? MAX_RETRY_DELAY
                : candidate;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
