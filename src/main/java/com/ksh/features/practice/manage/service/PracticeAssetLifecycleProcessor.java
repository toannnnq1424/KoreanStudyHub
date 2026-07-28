package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PracticeAssetLifecycleProcessor {

    private final PracticeAssetLifecycleTaskRepository taskRepository;
    private final PracticeAssetLifecycleTaskExecutor taskExecutor;
    private final PracticeAssetOrphanReconciler orphanReconciler;

    public PracticeAssetLifecycleProcessor(
            PracticeAssetLifecycleTaskRepository taskRepository,
            PracticeAssetLifecycleTaskExecutor taskExecutor,
            PracticeAssetOrphanReconciler orphanReconciler) {
        this.taskRepository = taskRepository;
        this.taskExecutor = taskExecutor;
        this.orphanReconciler = orphanReconciler;
    }

    public int processDue(int batchSize) {
        int processed = orphanReconciler.enqueueExpiredUnboundUploads(batchSize);
        LocalDateTime now = LocalDateTime.now();
        for (Long id : taskRepository.findDueIds(
                now,
                PageRequest.of(0, Math.max(1, Math.min(batchSize, 100))))) {
            taskExecutor.processOne(id);
            processed++;
        }
        return processed;
    }
}
