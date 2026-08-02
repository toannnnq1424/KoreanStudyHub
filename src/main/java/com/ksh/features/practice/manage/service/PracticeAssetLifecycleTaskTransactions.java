package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PracticeAssetLifecycleTaskTransactions {

    private static final int MAX_ATTEMPTS = 8;
    private static final java.time.Duration CLAIM_LEASE =
            java.time.Duration.ofMinutes(10);
    private static final java.time.Duration RETENTION_RECHECK_DELAY =
            java.time.Duration.ofHours(1);

    private final PracticeAssetLifecycleTaskRepository taskRepository;
    private final LecturerAssetRepository assetRepository;
    private final PracticeAssetReferenceGuard referenceGuard;

    public PracticeAssetLifecycleTaskTransactions(
            PracticeAssetLifecycleTaskRepository taskRepository,
            LecturerAssetRepository assetRepository,
            PracticeAssetReferenceGuard referenceGuard) {
        this.taskRepository = taskRepository;
        this.assetRepository = assetRepository;
        this.referenceGuard = referenceGuard;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedDelete claim(Long taskId) {
        PracticeAssetLifecycleTask task = taskRepository.findByIdForUpdate(taskId)
                .orElse(null);
        LocalDateTime now = LocalDateTime.now();
        boolean pendingDue = task != null
                && "PENDING".equals(task.getStatus())
                && (task.getNextAttemptAt() == null
                    || !task.getNextAttemptAt().isAfter(now));
        boolean staleRunning = task != null
                && "RUNNING".equals(task.getStatus())
                && task.getNextAttemptAt() != null
                && !task.getNextAttemptAt().isAfter(now);
        if (!pendingDue && !staleRunning) {
            return null;
        }
        if (PracticeAssetLifecycleTask.DELETE.equals(task.getOperation())
                && task.getAssetId() != null) {
            AssetDeleteDecision decision = lockAssetDeleteCandidate(
                    task.getAssetId(), task.getStorageProfileCode(),
                    task.getSourceStorageKey());
            if (decision.terminal()) {
                task.markCompleted();
                taskRepository.save(task);
                return null;
            }
            if (decision.blocked()) {
                deferRetainedStorageKey(task, now);
                return null;
            }
            String claimToken = java.util.UUID.randomUUID().toString();
            task.markRunning(claimToken, now.plus(CLAIM_LEASE));
            taskRepository.saveAndFlush(task);
            return new ClaimedDelete(
                    task.getId(),
                    task.getAssetId(),
                    task.getStorageProfileCode(),
                    task.getOperation(),
                    decision.asset().getStorageKey(),
                    claimToken);
        }
        if (task.getSourceStorageKey() == null
                || task.getSourceStorageKey().isBlank()
                || hasRetainedStorageKeyRow(
                        lockStorageRows(task.getStorageProfileCode(),
                                task.getSourceStorageKey()))) {
            task.markCompleted();
            taskRepository.save(task);
            return null;
        }
        String claimToken = java.util.UUID.randomUUID().toString();
        task.markRunning(claimToken, now.plus(CLAIM_LEASE));
        taskRepository.saveAndFlush(task);
        return new ClaimedDelete(
                task.getId(),
                task.getAssetId(),
                task.getStorageProfileCode(),
                task.getOperation(),
                task.getSourceStorageKey(),
                claimToken);
    }

    /**
     * Rechecks the durable claim immediately before storage I/O. Asset
     * registration takes the same task-key lock first, so a running cleanup
     * cannot race a new LecturerAsset row for the same physical key.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirmPhysicalDeleteAllowed(ClaimedDelete claim) {
        PracticeAssetLifecycleTask task = taskRepository.findByIdForUpdate(
                claim.taskId()).orElse(null);
        if (task == null
                || !"RUNNING".equals(task.getStatus())
                || !java.util.Objects.equals(
                        task.getClaimToken(), claim.claimToken())
                || !java.util.Objects.equals(
                        task.getSourceStorageKey(), claim.storageKey())) {
            return false;
        }
        if (PracticeAssetLifecycleTask.DELETE.equals(claim.operation())
                && claim.assetId() != null) {
            AssetDeleteDecision decision = lockAssetDeleteCandidate(
                    claim.assetId(), claim.storageProfileCode(),
                    claim.storageKey());
            if (decision.terminal()) {
                task.markCompleted();
                taskRepository.save(task);
                return false;
            }
            if (decision.blocked()) {
                deferRetainedStorageKey(task, LocalDateTime.now());
                return false;
            }
            return true;
        }
        if (claim.storageKey() == null
                || claim.storageKey().isBlank()
                || hasRetainedStorageKeyRow(
                        lockStorageRows(claim.storageProfileCode(),
                                claim.storageKey()))) {
            task.markCompleted();
            taskRepository.save(task);
            return false;
        }
        return true;
    }

    /**
     * Asset ids are logical identities, but storage deletion acts on one
     * physical key. Historical/content-addressed rows may share that key, so
     * both claim and final confirmation take the deterministic all-row key
     * lock and prove that every sibling has already entered an unreferenced
     * terminal deletion state. A retained or still-usable sibling makes the
     * decision fail closed without mutating any asset row.
     */
    private AssetDeleteDecision lockAssetDeleteCandidate(
            Long candidateId,
            String storageProfileCode,
            String storageKey) {
        if (candidateId == null
                || storageKey == null
                || storageKey.isBlank()) {
            return AssetDeleteDecision.invalid();
        }
        java.util.List<LecturerAsset> lockedAssets =
                lockStorageRows(storageProfileCode, storageKey);
        LecturerAsset candidate = lockedAssets.stream()
                .filter(asset -> java.util.Objects.equals(
                        candidateId, asset.getId()))
                .findFirst()
                .orElse(null);
        if (candidate == null
                || !"DELETION_PENDING".equalsIgnoreCase(
                        candidate.getStatus())
                || !java.util.Objects.equals(
                        candidate.getStorageKey(), storageKey)
                || !java.util.Objects.equals(
                        candidate.getStorageProfileCode(), storageProfileCode)) {
            return AssetDeleteDecision.invalid();
        }
        for (LecturerAsset asset : lockedAssets) {
            if (referenceGuard.isRetained(asset.getId())) {
                return AssetDeleteDecision.retained();
            }
            if (!java.util.Objects.equals(candidateId, asset.getId())
                    && storageRowStillNeedsBytes(asset)) {
                return AssetDeleteDecision.retained();
            }
        }
        return AssetDeleteDecision.allowed(candidate);
    }

    private java.util.List<LecturerAsset> lockStorageRows(
            String storageProfileCode, String storageKey) {
        if (!"PRACTICE_AUTHORING".equals(storageProfileCode)) {
            throw new IllegalStateException("Practice authoring storage profile is invalid.");
        }
        return assetRepository.findByStorageProfileCodeAndStorageKeyForUpdate(
                storageProfileCode, storageKey);
    }

    private void deferRetainedStorageKey(
            PracticeAssetLifecycleTask task,
            LocalDateTime now) {
        task.markDeferred(
                "Physical storage key is still retained by an asset row.",
                now.plus(RETENTION_RECHECK_DELAY));
        taskRepository.save(task);
    }

    private static boolean storageRowStillNeedsBytes(LecturerAsset asset) {
        return asset == null
                || (!"DELETION_PENDING".equalsIgnoreCase(asset.getStatus())
                    && !"DELETED".equalsIgnoreCase(asset.getStatus()));
    }

    private static boolean hasRetainedStorageKeyRow(
            java.util.List<LecturerAsset> assets) {
        return assets.stream().anyMatch(asset ->
                asset.getDeletedAt() == null
                        || !"DELETED".equalsIgnoreCase(asset.getStatus()));
    }

    private record AssetDeleteDecision(
            LecturerAsset asset,
            boolean blocked,
            boolean terminal) {

        private static AssetDeleteDecision allowed(LecturerAsset asset) {
            return new AssetDeleteDecision(asset, false, false);
        }

        private static AssetDeleteDecision retained() {
            return new AssetDeleteDecision(null, true, false);
        }

        private static AssetDeleteDecision invalid() {
            return new AssetDeleteDecision(null, false, true);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(ClaimedDelete claim) {
        PracticeAssetLifecycleTask task = taskRepository.findByIdForUpdate(
                claim.taskId()).orElse(null);
        if (task == null
                || !"RUNNING".equals(task.getStatus())
                || !java.util.Objects.equals(
                        task.getClaimToken(), claim.claimToken())) {
            return;
        }
        if (PracticeAssetLifecycleTask.DELETE.equals(claim.operation())
                && claim.assetId() != null) {
            LecturerAsset asset = assetRepository.findByIdForUpdate(
                    claim.assetId()).orElse(null);
            if (asset != null) {
                asset.setStatus("DELETED");
                asset.setDeletedAt(asset.getDeletedAt() == null
                        ? LocalDateTime.now() : asset.getDeletedAt());
                assetRepository.save(asset);
            }
        }
        task.markCompleted();
        taskRepository.save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(ClaimedDelete claim, Exception exception) {
        PracticeAssetLifecycleTask task = taskRepository.findByIdForUpdate(
                claim.taskId()).orElse(null);
        if (task == null
                || !"RUNNING".equals(task.getStatus())
                || !java.util.Objects.equals(
                        task.getClaimToken(), claim.claimToken())) {
            return;
        }
        long multiplier = Math.min(1L << Math.min(
                task.getAttemptCount() == null
                        ? 0
                        : task.getAttemptCount(), 10), 360L);
        task.markRetry(
                exception == null ? "storage cleanup failed" : exception.getMessage(),
                LocalDateTime.now().plusMinutes(multiplier),
                MAX_ATTEMPTS);
        taskRepository.save(task);
    }

    public record ClaimedDelete(
            Long taskId,
            Long assetId,
            String storageProfileCode,
            String operation,
            String storageKey,
            String claimToken) {}
}
