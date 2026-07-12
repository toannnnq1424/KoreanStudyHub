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
public class PracticeAssetLifecycleTaskExecutor {

    private static final int MAX_ATTEMPTS = 8;

    private final PracticeAssetLifecycleTaskRepository taskRepository;
    private final LecturerAssetRepository assetRepository;
    private final PracticeMaterialReferenceService referenceService;
    private final AssetStorageService storageService;

    public PracticeAssetLifecycleTaskExecutor(
            PracticeAssetLifecycleTaskRepository taskRepository,
            LecturerAssetRepository assetRepository,
            PracticeMaterialReferenceService referenceService,
            AssetStorageService storageService) {
        this.taskRepository = taskRepository;
        this.assetRepository = assetRepository;
        this.referenceService = referenceService;
        this.storageService = storageService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(Long taskId) {
        PracticeAssetLifecycleTask task = taskRepository.findByIdForUpdate(taskId)
                .orElse(null);
        if (task == null || !"PENDING".equals(task.getStatus())) return;
        task.markRunning();
        taskRepository.saveAndFlush(task);
        try {
            if (PracticeAssetLifecycleTask.DELETE.equals(task.getOperation())
                    && task.getAssetId() != null
                    && referenceService.hasAnyReference(task.getAssetId())) {
                LecturerAsset referenced = assetRepository.findById(task.getAssetId()).orElse(null);
                if (referenced != null
                        && "DELETION_PENDING".equalsIgnoreCase(referenced.getStatus())) {
                    referenced.setStatus("ARCHIVED");
                    assetRepository.save(referenced);
                }
                task.markCompleted();
                taskRepository.save(task);
                return;
            }
            if (task.getSourceStorageKey() != null
                    && !task.getSourceStorageKey().isBlank()) {
                storageService.delete(task.getSourceStorageKey());
            }
            if (PracticeAssetLifecycleTask.DELETE.equals(task.getOperation())
                    && task.getAssetId() != null) {
                LecturerAsset asset = assetRepository.findById(task.getAssetId()).orElse(null);
                if (asset != null) {
                    asset.setStatus("DELETED");
                    asset.setDeletedAt(asset.getDeletedAt() == null
                            ? LocalDateTime.now() : asset.getDeletedAt());
                    assetRepository.save(asset);
                }
            }
            task.markCompleted();
        } catch (Exception exception) {
            long multiplier = Math.min(1L << Math.min(
                    task.getAttemptCount() == null ? 0 : task.getAttemptCount(), 10), 360L);
            task.markRetry(exception.getMessage(),
                    LocalDateTime.now().plusMinutes(multiplier), MAX_ATTEMPTS);
        }
        taskRepository.save(task);
    }
}
