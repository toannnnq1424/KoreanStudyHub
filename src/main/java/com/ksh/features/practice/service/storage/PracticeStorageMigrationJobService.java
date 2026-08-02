package com.ksh.features.practice.service.storage;

import com.ksh.entities.PracticeStorageMigrationJob;
import com.ksh.entities.PracticeStorageMigrationLogicalType;
import com.ksh.entities.PracticeStorageMigrationStatus;
import com.ksh.features.practice.repository.PracticeStorageMigrationJobRepository;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PracticeStorageMigrationJobService {
    private final PracticeStorageMigrationJobRepository repository;
    private final Clock clock;
    private final Duration lease;
    private final Duration sourceDeleteDelay;

    @Autowired
    public PracticeStorageMigrationJobService(
            PracticeStorageMigrationJobRepository repository,
            ObjectProvider<Clock> clockProvider,
            @Value("${app.practice.storage-migration.lease:PT5M}") Duration lease,
            @Value("${app.practice.storage-migration.source-delete-delay:PT24H}")
            Duration sourceDeleteDelay) {
        this(repository, clockProvider.getIfAvailable(Clock::systemUTC), lease, sourceDeleteDelay);
    }

    PracticeStorageMigrationJobService(PracticeStorageMigrationJobRepository repository,
                                       Clock clock,
                                       Duration lease,
                                       Duration sourceDeleteDelay) {
        this.repository = repository;
        this.clock = clock;
        this.lease = bounded(lease, Duration.ofSeconds(30), Duration.ofMinutes(30));
        this.sourceDeleteDelay = bounded(sourceDeleteDelay,
                Duration.ofMinutes(1), Duration.ofDays(30));
    }

    @Transactional
    public Long plan(PracticeStorageMigrationLogicalType logicalType,
                     Long logicalId,
                     StorageProfileCode sourceProfileCode,
                     String sourceStorageKey,
                     StorageProfileCode targetProfileCode,
                     String targetStorageKey,
                     long expectedSize,
                     String expectedSha256) {
        return repository.save(PracticeStorageMigrationJob.planned(
                logicalType, logicalId, sourceProfileCode, sourceStorageKey,
                targetProfileCode, targetStorageKey, expectedSize, expectedSha256, now())).getId();
    }

    @Transactional(readOnly = true)
    public List<Long> findDueCopyIds(int limit) {
        if (limit < 1) return List.of();
        return repository.findDueIds(List.of(
                        PracticeStorageMigrationStatus.PLANNED,
                        PracticeStorageMigrationStatus.COPYING),
                now(), PageRequest.of(0, Math.min(limit, 100)));
    }

    @Transactional(readOnly = true)
    public List<Long> findDueCleanupIds(int limit) {
        if (limit < 1) return List.of();
        return repository.findDueIds(List.of(
                        PracticeStorageMigrationStatus.CLEANUP_PENDING,
                        PracticeStorageMigrationStatus.DELETING_SOURCE),
                now(), PageRequest.of(0, Math.min(limit, 100)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PracticeStorageMigrationClaim> claimCopy(Long jobId) {
        PracticeStorageMigrationJob job = repository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return Optional.empty();
        LocalDateTime now = now();
        String token = token();
        if (!job.claimCopy(now, now.plus(lease), token)) return Optional.empty();
        repository.saveAndFlush(job);
        return Optional.of(snapshot(job));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCopiedVerified(PracticeStorageMigrationClaim claim,
                                   StorageBackend targetProvider) {
        PracticeStorageMigrationJob job = loadOwned(claim, PracticeStorageMigrationStatus.COPYING);
        job.markCopiedVerified(claim.claimToken(), targetProvider, now());
        repository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryCopy(PracticeStorageMigrationClaim claim, String errorCode) {
        PracticeStorageMigrationJob job = loadOwned(claim, PracticeStorageMigrationStatus.COPYING);
        job.retryCopy(claim.claimToken(), errorCode,
                now().plus(backoff(job.getCopyAttemptCount())));
        repository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PracticeStorageMigrationClaim> claimCleanup(Long jobId) {
        PracticeStorageMigrationJob job = repository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return Optional.empty();
        LocalDateTime now = now();
        String token = token();
        if (!job.claimCleanup(now, now.plus(lease), token)) return Optional.empty();
        repository.saveAndFlush(job);
        return Optional.of(snapshot(job));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeCleanup(PracticeStorageMigrationClaim claim) {
        PracticeStorageMigrationJob job = loadOwned(
                claim, PracticeStorageMigrationStatus.DELETING_SOURCE);
        job.complete(claim.claimToken(), now());
        repository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryCleanup(PracticeStorageMigrationClaim claim, String errorCode) {
        PracticeStorageMigrationJob job = loadOwned(
                claim, PracticeStorageMigrationStatus.DELETING_SOURCE);
        job.retryCleanup(claim.claimToken(), errorCode,
                now().plus(backoff(job.getCleanupAttemptCount())));
        repository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markLogicalIdentityUpdated(Long jobId) {
        PracticeStorageMigrationJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException("STORAGE_MIGRATION_JOB_NOT_FOUND"));
        job.markLogicalUpdatedAndCleanupPending(now(), now().plus(sourceDeleteDelay));
        repository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<PracticeStorageMigrationStatus> status(Long jobId) {
        return repository.findById(jobId).map(PracticeStorageMigrationJob::getStatus);
    }

    private PracticeStorageMigrationJob loadOwned(
            PracticeStorageMigrationClaim claim,
            PracticeStorageMigrationStatus status) {
        PracticeStorageMigrationJob job = repository.findByIdForUpdate(claim.jobId())
                .orElseThrow(() -> new IllegalArgumentException("STORAGE_MIGRATION_JOB_NOT_FOUND"));
        if (job.getStatus() != status || !claim.claimToken().equals(job.getClaimToken())) {
            throw new IllegalStateException("STORAGE_MIGRATION_CLAIM_CONFLICT");
        }
        return job;
    }

    private static PracticeStorageMigrationClaim snapshot(PracticeStorageMigrationJob job) {
        return new PracticeStorageMigrationClaim(
                job.getId(), job.getClaimToken(), job.getLogicalType(), job.getLogicalId(),
                job.getSourceProfileCode(), job.getSourceStorageKey(),
                job.getTargetProfileCode(), job.getTargetStorageKey(),
                job.getExpectedSize(), job.getExpectedSha256());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static Duration backoff(int attempts) {
        return Duration.ofMinutes(Math.min(60L, 1L << Math.min(6, Math.max(0, attempts - 1))));
    }

    private static Duration bounded(Duration value, Duration minimum, Duration maximum) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("STORAGE_MIGRATION_DURATION_INVALID");
        }
        return value;
    }
}
