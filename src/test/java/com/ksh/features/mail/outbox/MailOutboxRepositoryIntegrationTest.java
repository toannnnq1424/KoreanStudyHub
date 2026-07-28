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
}
