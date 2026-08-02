package com.ksh.features.practice.service;

import com.ksh.entities.CleanupProcessingSnapshot;
import com.ksh.entities.PracticeSpeakingMediaCleanupErrorCode;
import com.ksh.entities.PracticeSpeakingMediaCleanupReason;
import com.ksh.entities.PracticeSpeakingMediaCleanupStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.repository.PracticeSpeakingMediaCleanupTaskRepository;
import com.ksh.features.practice.service.PracticeSpeakingMediaCleanupProcessor.CleanupTaskProcessingResult.Outcome;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.StoredSpeakingAudioObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PracticeSpeakingMediaCleanupTaskServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-05T00:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    private static final String SECRET_KEY = "learner-speaking/ready/learner_audio_cleanup_key_secret_b3a1";
    private static final String OTHER_SECRET_KEY = "learner-speaking/ready/learner_audio_cleanup_key_secret_b3a1_other";

    @Autowired
    private PracticeSpeakingMediaCleanupTaskService taskService;

    @Autowired
    private PracticeSpeakingMediaCleanupTaskRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM practice_speaking_media_cleanup_tasks WHERE storage_key LIKE 'learner-speaking/%'");
    }

    @Test
    void migrationCreatesCleanupTableWithoutSensitiveIdentityColumns() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_speaking_media_cleanup_tasks'
                """, String.class);

        assertThat(columns).contains(
                "id",
                "cleanup_reason",
                "storage_provider",
                "storage_key",
                "due_at",
                "next_attempt_at",
                "status",
                "claim_token",
                "lease_expires_at",
                "attempt_count",
                "last_error_code",
                "completed_at",
                "lock_version",
                "created_at",
                "updated_at");
        assertThat(columns).doesNotContain(
                "user_id",
                "attempt_id",
                "question_id",
                "content_hash",
                "filename",
                "absolute_path");

        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_speaking_media_cleanup_tasks'
                """, String.class);
        assertThat(indexes).contains(
                "PRIMARY",
                "uk_psm_cleanup_storage",
                "idx_psm_cleanup_status_next_attempt",
                "idx_psm_cleanup_due_at",
                "idx_psm_cleanup_status_lease");

        String statusCheck = jdbcTemplate.queryForObject("""
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_psm_cleanup_status'
                """, String.class);
        assertThat(statusCheck).contains("PROCESSING");
    }

    @Test
    void mandatoryEnqueueRequiresExistingTransactionAndUsesExactPolicyTimes() {
        assertThatThrownBy(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY))
                .isInstanceOf(IllegalTransactionStateException.class);

        Long supersededTaskId = inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        var superseded = repository.findById(supersededTaskId).orElseThrow();
        assertThat(superseded.getCleanupReason()).isEqualTo(PracticeSpeakingMediaCleanupReason.SUPERSEDED_RETENTION);
        assertThat(superseded.getDueAt()).isEqualTo(NOW.plusHours(24));
        assertThat(superseded.getNextAttemptAt()).isEqualTo(NOW.plusHours(24));

        Long deleteTaskId = inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, OTHER_SECRET_KEY));
        var deleted = repository.findById(deleteTaskId).orElseThrow();
        assertThat(deleted.getCleanupReason()).isEqualTo(PracticeSpeakingMediaCleanupReason.LOGICAL_DELETE);
        assertThat(deleted.getDueAt()).isEqualTo(NOW);
        assertThat(deleted.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void subsecondClockNormalizesImmediateLogicalDeleteBeforeClaim() {
        Instant fractionalInstant = Instant.parse("2026-07-05T00:00:00.900Z");
        LocalDateTime persistedSecond =
                LocalDateTime.ofInstant(fractionalInstant, ZoneOffset.UTC).withNano(0);
        PracticeSpeakingMediaCleanupTaskService fractionalClockService =
                new PracticeSpeakingMediaCleanupTaskService(
                        repository,
                        Clock.fixed(fractionalInstant, ZoneOffset.UTC));
        String storageKey = "learner-speaking/ready/fractional-immediate-delete";

        Long taskId = inTransaction(() -> fractionalClockService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, storageKey));

        var persisted = repository.findById(taskId).orElseThrow();
        assertThat(persisted.getDueAt()).isEqualTo(persistedSecond);
        assertThat(persisted.getNextAttemptAt()).isEqualTo(persistedSecond);

        CleanupProcessingSnapshot claim = inTransaction(() ->
                fractionalClockService.claimForProcessing(taskId).orElseThrow());
        assertThat(claim.status()).isEqualTo(PracticeSpeakingMediaCleanupStatus.PROCESSING);
        assertThat(claim.claimToken()).isNotBlank();
    }

    @Test
    void idempotentEnqueueKeepsOneTaskAndDoesNotReactivateCompletedOrTerminal() {
        Long taskId = inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        Long repeatedId = inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        assertThat(repeatedId).isEqualTo(taskId);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(taskId).orElseThrow().getDueAt()).isEqualTo(NOW);

        var retrySnapshot = taskService.claimForProcessing(taskId).orElseThrow();
        taskService.markRetry(
                retrySnapshot,
                PracticeSpeakingMediaCleanupErrorCode.DELETE_FAILED);
        Long retryId = inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        assertThat(retryId).isEqualTo(taskId);
        assertThat(repository.findById(taskId).orElseThrow().getAttemptCount()).isEqualTo(1L);

        String completedKey =
                "learner-speaking/ready/completed-secret-b3a1";
        Long completedTaskId = taskService.enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, completedKey);
        var completedSnapshot =
                taskService.claimForProcessing(completedTaskId).orElseThrow();
        taskService.markCompleted(completedSnapshot);
        Long completedId = inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, completedKey));
        assertThat(completedId).isEqualTo(completedTaskId);
        assertThat(repository.findById(completedTaskId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.COMPLETED);

        Long terminalTaskId = inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, "learner-speaking/ready/terminal-secret-b3a1"));
        var terminalSnapshot = taskService.claimForProcessing(terminalTaskId).orElseThrow();
        taskService.markTerminal(
                terminalSnapshot,
                PracticeSpeakingMediaCleanupErrorCode.INVALID_STORAGE_IDENTITY);
        inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, "learner-speaking/ready/terminal-secret-b3a1"));
        assertThat(repository.findById(terminalTaskId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.TERMINAL);
    }

    @Test
    void reenqueueEscalatesImmediateReasonsAndNeverPostponesExistingDueTime() {
        Long taskId = inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        var superseded = repository.findById(taskId).orElseThrow();
        assertThat(superseded.getCleanupReason()).isEqualTo(PracticeSpeakingMediaCleanupReason.SUPERSEDED_RETENTION);
        assertThat(superseded.getDueAt()).isEqualTo(NOW.plusHours(24));

        Long logicalDeleteId = inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        var logicalDelete = repository.findById(logicalDeleteId).orElseThrow();
        assertThat(logicalDeleteId).isEqualTo(taskId);
        assertThat(logicalDelete.getCleanupReason()).isEqualTo(PracticeSpeakingMediaCleanupReason.LOGICAL_DELETE);
        assertThat(logicalDelete.getDueAt()).isEqualTo(NOW);
        assertThat(logicalDelete.getNextAttemptAt()).isEqualTo(NOW);

        inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        var stillImmediate = repository.findById(taskId).orElseThrow();
        assertThat(stillImmediate.getCleanupReason()).isEqualTo(PracticeSpeakingMediaCleanupReason.LOGICAL_DELETE);
        assertThat(stillImmediate.getDueAt()).isEqualTo(NOW);

        taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        var compensation = repository.findById(taskId).orElseThrow();
        assertThat(compensation.getCleanupReason()).isEqualTo(PracticeSpeakingMediaCleanupReason.ACTIVATION_COMPENSATION);
        assertThat(compensation.getDueAt()).isEqualTo(NOW);

        inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        assertThat(repository.findById(taskId).orElseThrow().getCleanupReason())
                .isEqualTo(PracticeSpeakingMediaCleanupReason.ACTIVATION_COMPENSATION);
    }

    @Test
    void discardEnqueueEscalatesRetentionAndPreservesRetryBackoff() {
        Long retentionTaskId = inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY));
        Long discardTaskId = inTransaction(() -> taskService.enqueueDiscardAttempt(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, NOW));
        assertThat(discardTaskId).isEqualTo(retentionTaskId);
        var discardTask = repository.findById(discardTaskId).orElseThrow();
        assertThat(discardTask.getCleanupReason())
                .isEqualTo(PracticeSpeakingMediaCleanupReason.DISCARD_ATTEMPT);
        assertThat(discardTask.getDueAt()).isEqualTo(NOW.plusHours(24));
        assertThat(discardTask.getNextAttemptAt()).isEqualTo(NOW.plusHours(24));
        assertThat(taskService.findDueTaskIds(NOW, 10)).doesNotContain(discardTaskId);

        jdbcTemplate.update("""
                UPDATE practice_speaking_media_cleanup_tasks
                SET due_at = ?, next_attempt_at = ?
                WHERE id = ?
                """, NOW, NOW, discardTaskId);
        var discardSnapshot = taskService.claimForProcessing(discardTaskId).orElseThrow();
        taskService.markRetry(
                discardSnapshot,
                PracticeSpeakingMediaCleanupErrorCode.DELETE_FAILED);
        var retryBefore = repository.findById(discardTaskId).orElseThrow();
        inTransaction(() -> taskService.enqueueDiscardAttempt(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, NOW));
        var retryAfter = repository.findById(discardTaskId).orElseThrow();
        assertThat(retryAfter.getAttemptCount()).isEqualTo(retryBefore.getAttemptCount());
        assertThat(retryAfter.getNextAttemptAt()).isEqualTo(retryBefore.getNextAttemptAt());
        assertThat(retryAfter.getLastErrorCode()).isEqualTo(retryBefore.getLastErrorCode());

        Long logicalId = inTransaction(() -> taskService.enqueueLogicalDelete(
                PracticeSpeakingStorageProvider.LOCAL, OTHER_SECRET_KEY));
        var logicalSnapshot = taskService.claimForProcessing(logicalId).orElseThrow();
        taskService.markRetry(
                logicalSnapshot,
                PracticeSpeakingMediaCleanupErrorCode.DELETE_FAILED);
        var logicalBefore = repository.findById(logicalId).orElseThrow();
        inTransaction(() -> taskService.enqueueDiscardAttempt(
                PracticeSpeakingStorageProvider.LOCAL, OTHER_SECRET_KEY, NOW));
        var logicalAfter = repository.findById(logicalId).orElseThrow();
        assertThat(logicalAfter.getCleanupReason())
                .isEqualTo(PracticeSpeakingMediaCleanupReason.LOGICAL_DELETE);
        assertThat(logicalAfter.getAttemptCount()).isEqualTo(logicalBefore.getAttemptCount());
        assertThat(logicalAfter.getNextAttemptAt()).isEqualTo(logicalBefore.getNextAttemptAt());
    }

    @Test
    void requiresNewOrphanEnqueueSurvivesOuterTransactionRollback() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> template.execute(status -> {
            taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
            throw new IllegalStateException("rollback outer transaction");
        })).isInstanceOf(IllegalStateException.class);

        var task = repository.findByStorageProviderAndStorageKey(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY).orElseThrow();
        assertThat(task.getCleanupReason()).isEqualTo(PracticeSpeakingMediaCleanupReason.ACTIVATION_COMPENSATION);
        assertThat(task.getDueAt()).isEqualTo(NOW);
    }

    @Test
    void processingSnapshotAndEntityToStringDoNotExposeStorageKey() {
        Long taskId = taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        var task = repository.findById(taskId).orElseThrow();
        CleanupProcessingSnapshot snapshot = taskService.processingSnapshot(taskId).orElseThrow();

        assertThat(task.toString()).doesNotContain(SECRET_KEY);
        assertThat(snapshot.toString()).doesNotContain(SECRET_KEY);
    }

    @Test
    void staleOutcomeVersionIsRejected() {
        Long taskId = taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        CleanupProcessingSnapshot claim = taskService.claimForProcessing(taskId).orElseThrow();
        CleanupProcessingSnapshot wrongVersion = new CleanupProcessingSnapshot(
                claim.taskId(),
                999L,
                claim.storageProvider(),
                claim.storageKey(),
                claim.status(),
                claim.claimToken(),
                claim.leaseExpiresAt(),
                claim.attemptCount(),
                claim.nextAttemptAt());

        assertThatThrownBy(() -> taskService.markCompleted(wrongVersion))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cleanup task version mismatch.");
    }

    @Test
    void staleRetryCannotOverwriteCompletedTaskAndFinalTaskCannotBeReclaimed() {
        Long taskId = taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        CleanupProcessingSnapshot stale = taskService.claimForProcessing(taskId).orElseThrow();
        taskService.markCompleted(stale);

        assertThatThrownBy(() -> taskService.markRetry(
                stale,
                PracticeSpeakingMediaCleanupErrorCode.DELETE_FAILED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cleanup task version mismatch.");
        var completed = repository.findById(taskId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PracticeSpeakingMediaCleanupStatus.COMPLETED);
        assertThat(completed.getLastErrorCode()).isNull();

        assertThat(taskService.claimForProcessing(taskId)).isEmpty();
    }

    @Test
    void onlyOneWorkerOwnsLiveClaimAndExpiredClaimCanBeReclaimed() {
        Long taskId = taskService.enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);

        CleanupProcessingSnapshot first =
                taskService.claimForProcessing(taskId).orElseThrow();
        assertThat(first.status())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.PROCESSING);
        assertThat(first.claimToken()).isNotBlank();
        assertThat(first.leaseExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(taskService.claimForProcessing(taskId)).isEmpty();

        jdbcTemplate.update("""
                UPDATE practice_speaking_media_cleanup_tasks
                SET lease_expires_at = ?
                WHERE id = ?
                """, NOW.minusSeconds(1), taskId);

        CleanupProcessingSnapshot reclaimed =
                taskService.claimForProcessing(taskId).orElseThrow();
        assertThat(reclaimed.claimToken()).isNotEqualTo(first.claimToken());
        assertThat(reclaimed.leaseExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThatThrownBy(() -> taskService.markCompleted(first))
                .isInstanceOf(IllegalStateException.class);

        taskService.markCompleted(reclaimed);
        assertThat(repository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.COMPLETED);
    }

    @Test
    void concurrentWorkersProduceExactlyOneLiveClaim() throws Exception {
        Long taskId = taskService.enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return taskService.claimForProcessing(taskId);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return taskService.claimForProcessing(taskId);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CleanupProcessingSnapshot> claims = java.util.stream.Stream
                    .of(first.get(10, TimeUnit.SECONDS),
                            second.get(10, TimeUnit.SECONDS))
                    .flatMap(java.util.Optional::stream)
                    .toList();
            assertThat(claims).hasSize(1);
            taskService.markCompleted(claims.get(0));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void processorCompletesLocalDeletesOutsideTransactionAndSkipsCompletedOrTerminal() {
        Long taskId = taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        TrackingStorage storage = new TrackingStorage();
        PracticeSpeakingMediaCleanupProcessor processor =
                new PracticeSpeakingMediaCleanupProcessor(taskService, storage);

        assertThat(processor.processTaskNow(taskId).outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(storage.deletedKeys).containsExactly(SECRET_KEY);
        assertThat(storage.transactionActiveDuringDelete).containsExactly(false);
        assertThat(repository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.COMPLETED);

        assertThat(processor.processTaskNow(taskId).outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(storage.deletedKeys).containsExactly(SECRET_KEY);

        Long terminalTaskId = taskService.enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.OBJECT_STORAGE, OTHER_SECRET_KEY);
        assertThat(processor.processTaskNow(terminalTaskId).outcome()).isEqualTo(Outcome.TERMINAL);
        assertThat(repository.findById(terminalTaskId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.TERMINAL);
        assertThat(storage.deletedKeys).containsExactly(SECRET_KEY);
    }

    @Test
    void processorMarksInvalidKeyTerminalWithoutCallingStorage() {
        Long taskId = taskService.enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, "learner-speaking/../bad-secret-b3a1");
        TrackingStorage storage = new TrackingStorage();
        PracticeSpeakingMediaCleanupProcessor processor =
                new PracticeSpeakingMediaCleanupProcessor(taskService, storage);

        assertThat(processor.processTaskNow(taskId).outcome()).isEqualTo(Outcome.TERMINAL);
        var task = repository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(PracticeSpeakingMediaCleanupStatus.TERMINAL);
        assertThat(task.getLastErrorCode()).isEqualTo(PracticeSpeakingMediaCleanupErrorCode.INVALID_STORAGE_IDENTITY);
        assertThat(storage.deletedKeys).isEmpty();
    }

    @Test
    void processorRetryBackoffIsDeterministicAndBounded() {
        Long taskId = taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        TrackingStorage storage = new TrackingStorage();
        storage.failDeletes = true;
        PracticeSpeakingMediaCleanupProcessor processor =
                new PracticeSpeakingMediaCleanupProcessor(taskService, storage);

        assertBackoff(processor, taskId, 1L, NOW.plusMinutes(5));
        assertBackoff(processor, taskId, 2L, NOW.plusMinutes(30));
        assertBackoff(processor, taskId, 3L, NOW.plusHours(2));
        assertBackoff(processor, taskId, 4L, NOW.plusHours(6));
        assertBackoff(processor, taskId, 5L, NOW.plusHours(24));
        assertBackoff(processor, taskId, 6L, NOW.plusHours(24));
        assertBackoff(processor, taskId, 7L, NOW.plusHours(24));
        jdbcTemplate.update(
                "UPDATE practice_speaking_media_cleanup_tasks SET next_attempt_at = ? WHERE id = ?",
                NOW,
                taskId);
        assertThat(processor.processTaskNow(taskId).outcome()).isEqualTo(Outcome.TERMINAL);
        var terminal = repository.findById(taskId).orElseThrow();
        assertThat(terminal.getAttemptCount()).isEqualTo(8L);
        assertThat(terminal.getStatus()).isEqualTo(PracticeSpeakingMediaCleanupStatus.TERMINAL);
    }

    @Test
    void processorSaturatesAttemptCountInsteadOfOverflowing() {
        Long taskId = taskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
        jdbcTemplate.update(
                "UPDATE practice_speaking_media_cleanup_tasks SET attempt_count = ? WHERE id = ?",
                Long.MAX_VALUE,
                taskId);
        TrackingStorage storage = new TrackingStorage();
        storage.failDeletes = true;
        PracticeSpeakingMediaCleanupProcessor processor =
                new PracticeSpeakingMediaCleanupProcessor(taskService, storage);

        assertThat(processor.processTaskNow(taskId).outcome()).isEqualTo(Outcome.TERMINAL);
        var task = repository.findById(taskId).orElseThrow();
        assertThat(task.getAttemptCount()).isEqualTo(Long.MAX_VALUE);
        assertThat(task.getStatus()).isEqualTo(PracticeSpeakingMediaCleanupStatus.TERMINAL);
        assertThat(task.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void dueBatchHonorsLimitAndContinuesAfterTaskFailure() {
        Long failingId = taskService.enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, "learner-speaking/ready/fail-secret-b3a1");
        Long succeedingId = taskService.enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, "learner-speaking/ready/success-secret-b3a1");
        Long laterId = inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL, "learner-speaking/ready/later-secret-b3a1"));
        TrackingStorage storage = new TrackingStorage();
        storage.failKeyFragment = "fail-secret";
        PracticeSpeakingMediaCleanupProcessor processor =
                new PracticeSpeakingMediaCleanupProcessor(taskService, storage);

        var limited = processor.processDueTasks(NOW, 1);
        assertThat(limited.processed()).isEqualTo(1);
        assertThat(repository.findById(failingId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.RETRY);
        assertThat(repository.findById(succeedingId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.PENDING);
        assertThat(repository.findById(laterId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.PENDING);

        var remaining = processor.processDueTasks(NOW, 10);
        assertThat(remaining.completed()).isEqualTo(1);
        assertThat(repository.findById(succeedingId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.COMPLETED);
        assertThat(repository.findById(laterId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.PENDING);
    }

    @Test
    void dueBatchUsesInclusiveBoundaryDeterministicOrderAndSafeMaximumLimit() {
        for (int i = 0; i < 105; i++) {
            taskService.enqueueCompensationOrphan(
                    PracticeSpeakingStorageProvider.LOCAL,
                    "learner-speaking/ready/batch-secret-b3a1-" + i);
        }
        Long laterId = inTransaction(() -> taskService.enqueueSupersededRetention(
                PracticeSpeakingStorageProvider.LOCAL,
                "learner-speaking/ready/batch-later-secret-b3a1"));
        TrackingStorage storage = new TrackingStorage();
        PracticeSpeakingMediaCleanupProcessor processor =
                new PracticeSpeakingMediaCleanupProcessor(taskService, storage);

        var beforeBoundary = processor.processDueTasks(NOW.minusNanos(1_000), 200);
        assertThat(beforeBoundary.processed()).isZero();

        var atBoundary = processor.processDueTasks(NOW, 200);
        assertThat(atBoundary.processed()).isEqualTo(100);
        assertThat(atBoundary.completed()).isEqualTo(100);
        assertThat(storage.deletedKeys).containsExactlyElementsOf(
                IntStream.range(0, 100)
                        .mapToObj(i -> "learner-speaking/ready/batch-secret-b3a1-" + i)
                        .toList());
        assertThat(repository.findById(laterId).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaCleanupStatus.PENDING);
    }

    private void assertBackoff(PracticeSpeakingMediaCleanupProcessor processor,
                               Long taskId,
                               Long attemptCount,
                               LocalDateTime nextAttemptAt) {
        jdbcTemplate.update("""
                UPDATE practice_speaking_media_cleanup_tasks
                SET next_attempt_at = ?
                WHERE id = ?
                  AND status = 'RETRY'
                """, NOW, taskId);
        assertThat(processor.processTaskNow(taskId).outcome()).isEqualTo(Outcome.RETRY);
        var task = repository.findById(taskId).orElseThrow();
        assertThat(task.getAttemptCount()).isEqualTo(attemptCount);
        assertThat(task.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(task.getLastErrorCode()).isEqualTo(PracticeSpeakingMediaCleanupErrorCode.DELETE_FAILED);
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> callback) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return callback.call();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        Clock cleanupClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
        }
    }

    private static final class TrackingStorage implements SpeakingAudioStorage {
        private final List<String> deletedKeys = new ArrayList<>();
        private final List<Boolean> transactionActiveDuringDelete = new ArrayList<>();
        private boolean failDeletes;
        private String failKeyFragment;

        @Override
        public StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredContentLength) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String promoteTemporary(String temporaryKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String storageKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String storageKey) {
            return false;
        }

        @Override
        public void delete(String storageKey) {
            transactionActiveDuringDelete.add(TransactionSynchronizationManager.isActualTransactionActive());
            deletedKeys.add(storageKey);
            if (failDeletes || (failKeyFragment != null && storageKey.contains(failKeyFragment))) {
                throw new IllegalStateException("LEARNER_AUDIO_ERROR_SECRET_B3A1");
            }
        }
    }
}
