package com.ksh.features.mail.outbox;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.notifications.entity.Notification;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.repository.NotificationRepository;
import com.ksh.features.notifications.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL/Flyway contract for the V59 outbox table and its JPA mapping.
 *
 * <p>No transport bean is loaded, so this test cannot perform network I/O.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        MailOutboxService.class,
        MailOutboxTransactionService.class,
        NotificationService.class
})
@Transactional
class MailOutboxRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MailOutboxRepository outboxRepository;

    @Autowired
    private MailOutboxService outboxService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MailOutboxTransactionService transactionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void enqueue_persists_one_job_linked_to_notification() {
        User user = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn")
                .orElseThrow();
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                user.getId(),
                "Bài học mới",
                "Nội dung bài học",
                NotificationType.LESSON_PUBLISHED,
                NotificationType.REF_LESSON,
                123L));

        outboxService.enqueueNotification(
                notification.getId(),
                user.getEmail(),
                "[KSH] " + notification.getTitle(),
                notification.getContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(outboxRepository.existsByNotificationId(notification.getId())).isTrue();
        MailOutboxJob job = outboxRepository.findAll().stream()
                .filter(candidate -> notification.getId().equals(candidate.getNotificationId()))
                .findFirst()
                .orElseThrow();
        assertThat(job.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getRecipientEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void deleting_notification_nulls_link_but_preserves_delivery_snapshot() {
        User user = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn")
                .orElseThrow();
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                user.getId(),
                "Snapshot survives",
                "Notification can be removed independently",
                NotificationType.LESSON_PUBLISHED,
                NotificationType.REF_LESSON,
                124L));
        MailOutboxJob job = outboxRepository.saveAndFlush(MailOutboxJob.pending(
                notification.getId(),
                "snapshot-recipient@example.com",
                "[KSH] Immutable subject snapshot",
                "Immutable body snapshot",
                MailOutboxService.SOURCE_NOTIFICATION,
                LocalDateTime.now(ZoneOffset.UTC)));
        Long jobId = job.getId();

        notificationRepository.delete(notification);
        notificationRepository.flush();
        entityManager.clear();

        MailOutboxJob retained = outboxRepository.findById(jobId).orElseThrow();
        assertThat(retained.getNotificationId()).isNull();
        assertThat(retained.getRecipientEmail())
                .isEqualTo("snapshot-recipient@example.com");
        assertThat(retained.getSubject())
                .isEqualTo("[KSH] Immutable subject snapshot");
        assertThat(retained.getBody()).isEqualTo("Immutable body snapshot");
        assertThat(retained.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
    }

    @Test
    void terminal_retention_deletes_only_rows_strictly_older_than_each_cutoff() {
        User user = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn")
                .orElseThrow();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime sentCutoff = now.minusDays(30);
        LocalDateTime failedCutoff = now.minusDays(90);

        MailOutboxJob oldSent = retentionJob(user, 201L, now.minusDays(40));
        oldSent.markSent(sentCutoff.minusSeconds(1));
        MailOutboxJob boundarySent = retentionJob(user, 202L, now.minusDays(40));
        boundarySent.markSent(sentCutoff);
        MailOutboxJob oldFailed = retentionJob(user, 203L, now.minusDays(100));
        oldFailed.markFailed(failedCutoff.minusSeconds(1), "delivery_failed");
        MailOutboxJob pending = retentionJob(user, 204L, now.minusDays(120));
        MailOutboxJob retry = retentionJob(user, 205L, now.minusDays(120));
        retry.scheduleRetry(now.minusDays(100), now.minusDays(100), "delivery_failed");
        MailOutboxJob processing = retentionJob(user, 206L, now.minusDays(120));
        processing.claim("retention-contract", now.minusDays(100), Duration.ofMinutes(2));

        outboxRepository.saveAllAndFlush(List.of(
                oldSent,
                boundarySent,
                oldFailed,
                pending,
                retry,
                processing));
        Long oldSentId = oldSent.getId();
        Long boundarySentId = boundarySent.getId();
        Long oldFailedId = oldFailed.getId();
        Long pendingId = pending.getId();
        Long retryId = retry.getId();
        Long processingId = processing.getId();

        assertThat(outboxRepository.deleteSentBefore(sentCutoff, 100)).isEqualTo(1);
        assertThat(outboxRepository.deleteFailedBefore(failedCutoff, 100)).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        assertThat(outboxRepository.findById(oldSentId)).isEmpty();
        assertThat(outboxRepository.findById(oldFailedId)).isEmpty();
        assertThat(outboxRepository.findAllById(List.of(
                        boundarySentId,
                        pendingId,
                        retryId,
                        processingId)))
                .extracting(MailOutboxJob::getStatus)
                .containsExactlyInAnyOrder(
                        MailOutboxStatus.SENT,
                        MailOutboxStatus.PENDING,
                        MailOutboxStatus.RETRY,
                        MailOutboxStatus.PROCESSING);
    }

    @Test
    void notification_and_outbox_roll_back_together() {
        User user = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn")
                .orElseThrow();
        Notification notification = notificationService.create(
                user.getId(),
                "Rollback contract",
                "Không được tồn tại sau rollback",
                NotificationType.LESSON_PUBLISHED,
                NotificationType.REF_LESSON,
                987_654L);
        entityManager.flush();
        Long notificationId = notification.getId();

        assertThat(notificationRepository.findById(notificationId)).isPresent();
        assertThat(outboxRepository.existsByNotificationId(notificationId)).isTrue();

        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationRepository.findById(notificationId)).isEmpty();
        assertThat(outboxRepository.existsByNotificationId(notificationId)).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrent_workers_claim_a_due_job_exactly_once() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        long[] ids = transactions.execute(status -> {
            User user = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn")
                    .orElseThrow();
            Notification notification = notificationRepository.saveAndFlush(new Notification(
                    user.getId(),
                    "Concurrent claim contract",
                    "Outbox lease test",
                    NotificationType.LESSON_PUBLISHED,
                    NotificationType.REF_LESSON,
                    987_655L));
            MailOutboxJob job = outboxRepository.saveAndFlush(MailOutboxJob.pending(
                    notification.getId(),
                    user.getEmail(),
                    "[KSH] Concurrent claim contract",
                    "Outbox lease test",
                    MailOutboxService.SOURCE_NOTIFICATION,
                    LocalDateTime.now(ZoneOffset.UTC)));
            return new long[]{notification.getId(), job.getId()};
        });
        assertThat(ids).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<MailOutboxDelivery>> first = executor.submit(
                    () -> claimAfterBarrier(ids[1], "worker-a", ready, start));
            Future<Optional<MailOutboxDelivery>> second = executor.submit(
                    () -> claimAfterBarrier(ids[1], "worker-b", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Optional<MailOutboxDelivery>> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            transactions.executeWithoutResult(status -> {
                outboxRepository.deleteById(ids[1]);
                notificationRepository.deleteById(ids[0]);
            });
        }
    }

    private Optional<MailOutboxDelivery> claimAfterBarrier(
            long jobId,
            String workerId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent claim barrier timed out");
        }
        return transactionService.claim(jobId, workerId);
    }

    private MailOutboxJob retentionJob(User user, long referenceId, LocalDateTime createdAt) {
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                user.getId(),
                "Retention contract " + referenceId,
                "Terminal retention contract",
                NotificationType.LESSON_PUBLISHED,
                NotificationType.REF_LESSON,
                referenceId));
        return MailOutboxJob.pending(
                notification.getId(),
                "retention-contract@example.com",
                "[KSH] Retention contract",
                "Retention contract body",
                MailOutboxService.SOURCE_NOTIFICATION,
                createdAt);
    }
}
