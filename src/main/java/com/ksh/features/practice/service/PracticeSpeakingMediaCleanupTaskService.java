package com.ksh.features.practice.service;

import com.ksh.entities.CleanupProcessingSnapshot;
import com.ksh.entities.PracticeSpeakingMediaCleanupErrorCode;
import com.ksh.entities.PracticeSpeakingMediaCleanupReason;
import com.ksh.entities.PracticeSpeakingMediaCleanupStatus;
import com.ksh.entities.PracticeSpeakingMediaCleanupTask;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.repository.PracticeSpeakingMediaCleanupTaskRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.UUID;

@Service
public class PracticeSpeakingMediaCleanupTaskService {

    private final PracticeSpeakingMediaCleanupTaskRepository repository;
    private final Clock clock;
    private final java.time.Duration leaseDuration;
    private final PracticeSpeakingMediaRepository mediaRepository;

    @Autowired
    public PracticeSpeakingMediaCleanupTaskService(
            PracticeSpeakingMediaCleanupTaskRepository repository,
            PracticeSpeakingMediaRepository mediaRepository,
            ObjectProvider<Clock> clockProvider,
            @Value("${app.practice.speaking-media.cleanup-lease-duration:PT5M}")
            java.time.Duration leaseDuration) {
        this(repository, mediaRepository,
                clockProvider.getIfAvailable(Clock::systemUTC), leaseDuration);
    }

    PracticeSpeakingMediaCleanupTaskService(
            PracticeSpeakingMediaCleanupTaskRepository repository,
            Clock clock) {
        this(repository, clock, java.time.Duration.ofMinutes(5));
    }

    PracticeSpeakingMediaCleanupTaskService(
            PracticeSpeakingMediaCleanupTaskRepository repository,
            Clock clock,
            java.time.Duration leaseDuration) {
        this(repository, null, clock, leaseDuration);
    }

    PracticeSpeakingMediaCleanupTaskService(
            PracticeSpeakingMediaCleanupTaskRepository repository,
            PracticeSpeakingMediaRepository mediaRepository,
            Clock clock,
            java.time.Duration leaseDuration) {
        this.repository = repository;
        this.mediaRepository = mediaRepository;
        this.clock = clock;
        this.leaseDuration = boundedLease(leaseDuration);
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
    public Long enqueueSupersededRetention(
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey) {
        return enqueueExact(PracticeSpeakingMediaCleanupReason.SUPERSEDED_RETENTION,
                mediaId, storageProvider, storageProfileCode, storageKey,
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

    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueLogicalDelete(
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey) {
        return enqueueExact(PracticeSpeakingMediaCleanupReason.LOGICAL_DELETE,
                mediaId, storageProvider, storageProfileCode, storageKey, now());
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enqueueCompensationOrphan(
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey) {
        return enqueueExact(PracticeSpeakingMediaCleanupReason.ACTIVATION_COMPENSATION,
                null, storageProvider, storageProfileCode, storageKey, now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueTemporaryExpiry(
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey) {
        return enqueueExact(PracticeSpeakingMediaCleanupReason.TEMPORARY_EXPIRY,
                mediaId, storageProvider, storageProfileCode, storageKey,
                now().plusHours(24));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueuePromotedTemporaryCleanup(
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey) {
        return enqueueExact(PracticeSpeakingMediaCleanupReason.ACTIVATION_COMPENSATION,
                null, storageProvider, storageProfileCode, storageKey, now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueDiscardAttempt(
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            LocalDateTime discardedAt) {
        if (discardedAt == null) {
            throw new IllegalArgumentException("discardedAt is required.");
        }
        return enqueue(
                PracticeSpeakingMediaCleanupReason.DISCARD_ATTEMPT,
                storageProvider,
                storageKey,
                discardedAt.plusHours(24));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueDiscardAttempt(
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey,
            LocalDateTime discardedAt) {
        if (discardedAt == null) throw new IllegalArgumentException("discardedAt is required.");
        return enqueueExact(PracticeSpeakingMediaCleanupReason.DISCARD_ATTEMPT,
                mediaId, storageProvider, storageProfileCode, storageKey,
                discardedAt.plusHours(24));
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CleanupProcessingSnapshot> claimForProcessing(Long taskId) {
        PracticeSpeakingMediaCleanupTask task = repository.findByIdForUpdate(taskId)
                .orElse(null);
        LocalDateTime claimedAt = now();
        if (task == null || !task.isClaimable(claimedAt)) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        task.claim(task.getLockVersion(), token, claimedAt.plus(leaseDuration));
        if (task.getMediaId() != null && mediaRepository != null) {
            mediaRepository.findByIdForUpdate(task.getMediaId())
                    .ifPresent(media -> {
                        if (!java.util.Objects.equals(media.getStorageProfileCode(),
                                task.getStorageProfileCode())
                                || !java.util.Objects.equals(media.getStorageKey(),
                                task.getStorageKey())) {
                            throw new IllegalStateException("Cleanup media identity changed.");
                        }
                        media.markDeletionPending();
                        mediaRepository.save(media);
                    });
        }
        repository.saveAndFlush(task);
        return Optional.of(task.toProcessingSnapshot());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeSpeakingMediaCleanupStatus markCompleted(
            CleanupProcessingSnapshot claim) {
        PracticeSpeakingMediaCleanupTask task = loadForClaim(claim);
        task.markCompleted(
                claim.lockVersion(), claim.claimToken(), now());
        return task.getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeSpeakingMediaCleanupStatus confirmPhysicalDeletion(
            CleanupProcessingSnapshot claim) {
        PracticeSpeakingMediaCleanupTask task = loadForClaim(claim);
        if (task.getMediaId() != null) {
            if (mediaRepository == null) {
                throw new IllegalStateException("Speaking media repository is unavailable.");
            }
            var media = mediaRepository.findByIdForUpdate(task.getMediaId())
                    .orElseThrow(() -> new IllegalStateException("Speaking media is unavailable."));
            if (!java.util.Objects.equals(media.getStorageProfileCode(), task.getStorageProfileCode())
                    || !java.util.Objects.equals(media.getStorageKey(), task.getStorageKey())) {
                throw new IllegalStateException("Cleanup media identity changed.");
            }
            media.markDeleted(now());
            mediaRepository.save(media);
        }
        task.markCompleted(claim.lockVersion(), claim.claimToken(), now());
        return task.getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeSpeakingMediaCleanupStatus markRetry(
            CleanupProcessingSnapshot claim,
            PracticeSpeakingMediaCleanupErrorCode errorCode) {
        PracticeSpeakingMediaCleanupTask task = loadForClaim(claim);
        LocalDateTime nextAttemptAt = now().plus(
                backoff(claim.attemptCount() == null ? 0L : claim.attemptCount()));
        if (claim.attemptCount() != null && claim.attemptCount() >= 7L) {
            task.markTerminal(claim.lockVersion(), claim.claimToken(), errorCode, now());
        } else {
            task.markRetry(claim.lockVersion(), claim.claimToken(), errorCode, nextAttemptAt);
        }
        return task.getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeSpeakingMediaCleanupStatus markTerminal(
            CleanupProcessingSnapshot claim,
            PracticeSpeakingMediaCleanupErrorCode errorCode) {
        PracticeSpeakingMediaCleanupTask task = loadForClaim(claim);
        task.markTerminal(
                claim.lockVersion(), claim.claimToken(), errorCode, now());
        return task.getStatus();
    }

    private Long enqueue(PracticeSpeakingMediaCleanupReason reason,
                         PracticeSpeakingStorageProvider storageProvider,
                         String storageKey,
                         LocalDateTime dueAt) {
        return enqueue(reason, storageProvider, storageKey, dueAt, dueAt);
    }

    private Long enqueueExact(
            PracticeSpeakingMediaCleanupReason reason,
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode,
            String storageKey,
            LocalDateTime dueAt) {
        PracticeSpeakingMediaCleanupTask.pendingExact(reason, mediaId,
                storageProvider, storageProfileCode, storageKey, dueAt, dueAt);
        repository.insertOrKeepExistingExact(reason.name(), mediaId,
                storageProvider.name(), storageProfileCode, storageKey, dueAt, dueAt);
        return repository.findByStorageProfileCodeAndStorageKey(
                        storageProfileCode, storageKey)
                .orElseThrow(() -> new IllegalStateException("Cleanup task was not persisted."))
                .getId();
    }

    private Long enqueue(PracticeSpeakingMediaCleanupReason reason,
                         PracticeSpeakingStorageProvider storageProvider,
                         String storageKey,
                         LocalDateTime dueAt,
                         LocalDateTime nextAttemptAt) {
        PracticeSpeakingMediaCleanupTask candidate =
                PracticeSpeakingMediaCleanupTask.pending(
                        reason, storageProvider, storageKey, dueAt, nextAttemptAt);
        repository.insertOrKeepExisting(
                reason.name(),
                storageProvider.name(),
                candidate.getStorageKey(),
                dueAt,
                nextAttemptAt);
        return repository.findByStorageProviderAndStorageKey(storageProvider, candidate.getStorageKey())
                .orElseThrow(() -> new IllegalStateException("Cleanup task was not persisted."))
                .getId();
    }

    private PracticeSpeakingMediaCleanupTask loadForClaim(
            CleanupProcessingSnapshot claim) {
        if (claim == null || claim.taskId() == null) {
            throw new IllegalArgumentException("Cleanup claim is required.");
        }
        return repository.findByIdForUpdate(claim.taskId())
                .orElseThrow(() ->
                        new IllegalStateException("Cleanup task is unavailable."));
    }

    private LocalDateTime now() {
        Instant instant = Instant.now(clock);
        // V31 persists cleanup scheduling columns as DATETIME(0). Keep enqueue
        // and claim comparisons on that same precision so an immediate task
        // cannot be rounded into the next second by MySQL.
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).withNano(0);
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

    private static java.time.Duration boundedLease(java.time.Duration configured) {
        java.time.Duration candidate = configured == null
                ? java.time.Duration.ofMinutes(5)
                : configured;
        if (candidate.compareTo(java.time.Duration.ofSeconds(30)) < 0) {
            return java.time.Duration.ofSeconds(30);
        }
        if (candidate.compareTo(java.time.Duration.ofMinutes(30)) > 0) {
            return java.time.Duration.ofMinutes(30);
        }
        return candidate;
    }
}
