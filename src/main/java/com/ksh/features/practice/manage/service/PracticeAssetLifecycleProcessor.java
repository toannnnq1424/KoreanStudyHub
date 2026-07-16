package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PracticeAssetLifecycleProcessor {

    private final PracticeAssetLifecycleTaskRepository taskRepository;
    private final PracticeAssetLifecycleTaskExecutor taskExecutor;

    public PracticeAssetLifecycleProcessor(
            PracticeAssetLifecycleTaskRepository taskRepository,
            PracticeAssetLifecycleTaskExecutor taskExecutor) {
        this.taskRepository = taskRepository;
        this.taskExecutor = taskExecutor;
    }

    public int processDue(int batchSize) {
        int processed = 0;
        for (Long id : taskRepository.findDueIds(
                LocalDateTime.now(), PageRequest.of(0, Math.max(1, Math.min(batchSize, 100))))) {
            taskExecutor.processOne(id);
            processed++;
        }
        return processed;
    }
}
