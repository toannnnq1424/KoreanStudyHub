package com.ksh.features.practice.service;

import com.ksh.entities.CleanupProcessingSnapshot;
import com.ksh.entities.PracticeSpeakingMediaCleanupErrorCode;
import com.ksh.entities.PracticeSpeakingMediaCleanupReason;
import com.ksh.entities.PracticeSpeakingMediaCleanupStatus;
import com.ksh.entities.PracticeSpeakingMediaCleanupTask;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.repository.PracticeSpeakingMediaCleanupTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class PracticeSpeakingMediaCleanupTaskService {

    private final PracticeSpeakingMediaCleanupTaskRepository repository;
    private final Clock clock;

    @Autowired
    public PracticeSpeakingMediaCleanupTaskService(
            PracticeSpeakingMediaCleanupTaskRepository repository,
            ObjectProvider<Clock> clockProvider) {
        this(repository, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    PracticeSpeakingMediaCleanupTaskService(
            PracticeSpeakingMediaCleanupTaskRepository repository,
            Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueSupersededRetention(
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey) {
        return enqueue(
                PracticeSpeakingMediaCleanupReason.SUPERSEDED_RETENTION,
                storageProvider,
                storageKey,
                now().plusHours(24));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueLogicalDelete(
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey) {
        return enqueue(
                PracticeSpeakingMediaCleanupReason.LOGICAL_DELETE,
                storageProvider,
                storageKey,
                now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enqueueCompensationOrphan(
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey) {
        return enqueue(
                PracticeSpeakingMediaCleanupReason.ACTIVATION_COMPENSATION,
                storageProvider,
                storageKey,
                now());
    }

    @Transactional(readOnly = true)
    public Optional<CleanupProcessingSnapshot> processingSnapshot(Long taskId) {
        return repository.findById(taskId).map(PracticeSpeakingMediaCleanupTask::toProcessingSnapshot);
    }

    @Transactional(readOnly = true)
    public List<Long> findDueTaskIds(LocalDateTime now, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return repository.findDueTaskIds(now, PageRequest.of(0, limit));
    }

    @Transactional
    public PracticeSpeakingMediaCleanupStatus markCompleted(Long taskId, Long expectedLockVersion) {
        PracticeSpeakingMediaCleanupTask task = load(taskId);
        task.markCompleted(expectedLockVersion, now());
        return task.getStatus();
    }

    @Transactional
    public PracticeSpeakingMediaCleanupStatus markRetry(
            Long taskId,
            Long expectedLockVersion,
            Long attemptCount,
            PracticeSpeakingMediaCleanupErrorCode errorCode) {
        PracticeSpeakingMediaCleanupTask task = load(taskId);
        LocalDateTime nextAttemptAt = now().plus(backoff(attemptCount == null ? 0L : attemptCount));
        task.markRetry(expectedLockVersion, errorCode, nextAttemptAt);
        return task.getStatus();
    }

    @Transactional
    public PracticeSpeakingMediaCleanupStatus markTerminal(
            Long taskId,
            Long expectedLockVersion,
            PracticeSpeakingMediaCleanupErrorCode errorCode) {
        PracticeSpeakingMediaCleanupTask task = load(taskId);
        task.markTerminal(expectedLockVersion, errorCode, now());
        return task.getStatus();
    }

    private Long enqueue(PracticeSpeakingMediaCleanupReason reason,
                         PracticeSpeakingStorageProvider storageProvider,
                         String storageKey,
                         LocalDateTime dueAt) {
        PracticeSpeakingMediaCleanupTask candidate =
                PracticeSpeakingMediaCleanupTask.pending(reason, storageProvider, storageKey, dueAt);
        repository.insertOrKeepExisting(
                reason.name(),
                storageProvider.name(),
                candidate.getStorageKey(),
                dueAt);
        return repository.findByStorageProviderAndStorageKey(storageProvider, candidate.getStorageKey())
                .orElseThrow(() -> new IllegalStateException("Cleanup task was not persisted."))
                .getId();
    }

    private PracticeSpeakingMediaCleanupTask load(Long taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Cleanup task is unavailable."));
    }

    private LocalDateTime now() {
        Instant instant = Instant.now(clock);
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static java.time.Duration backoff(Long alreadyAttemptedCount) {
        if (alreadyAttemptedCount == null || alreadyAttemptedCount <= 0L) {
            return java.time.Duration.ofMinutes(5);
        }
        if (alreadyAttemptedCount == 1L) {
            return java.time.Duration.ofMinutes(30);
        }
        if (alreadyAttemptedCount == 2L) {
            return java.time.Duration.ofHours(2);
        }
        if (alreadyAttemptedCount == 3L) {
            return java.time.Duration.ofHours(6);
        }
        return java.time.Duration.ofHours(24);
    }
}
