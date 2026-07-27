package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PracticeAssetOrphanReconciler {

    private final LecturerAssetRepository assetRepository;
    private final PracticeAssetLifecycleTaskRepository taskRepository;
    private final PracticeAssetReferenceGuard referenceGuard;

    public PracticeAssetOrphanReconciler(
            LecturerAssetRepository assetRepository,
            PracticeAssetLifecycleTaskRepository taskRepository,
            PracticeAssetReferenceGuard referenceGuard) {
        this.assetRepository = assetRepository;
        this.taskRepository = taskRepository;
        this.referenceGuard = referenceGuard;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int enqueueExpiredUnboundUploads(int requestedLimit) {
        int processed = 0;
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        for (Long assetId : assetRepository.findExpiredUnboundAssetIds(
                LocalDateTime.now(), PageRequest.of(0, limit))) {
            if (enqueueOne(assetId)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean enqueueOne(Long assetId) {
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
                .orElse(null);
        LocalDateTime now = LocalDateTime.now();
        /*
         * Only the two unbound producer states are retention candidates:
         * lecturer uploads and generated TTS staged before task completion.
         */
        if (asset == null
                || !"TEMPORARY".equalsIgnoreCase(asset.getStatus())
                || !"PRIVATE".equalsIgnoreCase(asset.getVisibility())
                || (!"MANUAL_UPLOAD".equalsIgnoreCase(asset.getSourceType())
                    && !"AI_TTS".equalsIgnoreCase(asset.getSourceType()))
                || asset.getRetentionUntil() == null
                || asset.getRetentionUntil().isAfter(now)
                || referenceGuard.isRetained(assetId)) {
            return false;
        }
        asset.setStatus("DELETION_PENDING");
        asset.setDeletedAt(now);
        asset.setUpdatedAt(now);
        assetRepository.save(asset);
        taskRepository.save(new PracticeAssetLifecycleTask(
                assetId,
                PracticeAssetLifecycleTask.DELETE,
                asset.getStorageKey(),
                null));
        return true;
    }
}
