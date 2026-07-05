package com.ksh.features.practice.service;

import com.ksh.entities.CleanupProcessingSnapshot;
import com.ksh.entities.PracticeSpeakingMediaCleanupErrorCode;
import com.ksh.entities.PracticeSpeakingMediaCleanupStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class PracticeSpeakingMediaCleanupProcessor {

    private static final Logger log = LoggerFactory.getLogger(PracticeSpeakingMediaCleanupProcessor.class);
    private static final String CLEANUP_FAILED_EVENT = "Speaking media cleanup failed";
    private static final int MAX_BATCH_SIZE = 100;

    private final PracticeSpeakingMediaCleanupTaskService taskService;
    private final SpeakingAudioStorage storage;

    public PracticeSpeakingMediaCleanupProcessor(
            PracticeSpeakingMediaCleanupTaskService taskService,
            SpeakingAudioStorage storage) {
        this.taskService = taskService;
        this.storage = storage;
    }

    public CleanupTaskProcessingResult processTaskNow(Long taskId) {
        return taskService.processingSnapshot(taskId)
                .map(this::processSnapshot)
                .orElse(CleanupTaskProcessingResult.skipped());
    }

    public CleanupBatchResult processDueTasks(LocalDateTime now, int limit) {
        List<Long> taskIds = taskService.findDueTaskIds(now, Math.min(limit, MAX_BATCH_SIZE));
        int completed = 0;
        int retried = 0;
        int terminal = 0;
        int skipped = 0;
        int failed = 0;
        for (Long taskId : taskIds) {
            try {
                CleanupTaskProcessingResult result = processTaskNow(taskId);
                switch (result.outcome()) {
                    case COMPLETED -> completed++;
                    case RETRY -> retried++;
                    case TERMINAL -> terminal++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn(CLEANUP_FAILED_EVENT);
            }
        }
        return new CleanupBatchResult(taskIds.size(), completed, retried, terminal, skipped, failed);
    }

    private CleanupTaskProcessingResult processSnapshot(CleanupProcessingSnapshot snapshot) {
        if (snapshot.status() == PracticeSpeakingMediaCleanupStatus.COMPLETED
                || snapshot.status() == PracticeSpeakingMediaCleanupStatus.TERMINAL) {
            return CleanupTaskProcessingResult.skipped();
        }
        if (snapshot.storageProvider() != PracticeSpeakingStorageProvider.LOCAL) {
            taskService.markTerminal(
                    snapshot.taskId(),
                    snapshot.lockVersion(),
                    PracticeSpeakingMediaCleanupErrorCode.PROVIDER_UNSUPPORTED);
            return CleanupTaskProcessingResult.terminal();
        }
        if (!isValidLocalStorageKey(snapshot.storageKey())) {
            taskService.markTerminal(
                    snapshot.taskId(),
                    snapshot.lockVersion(),
                    PracticeSpeakingMediaCleanupErrorCode.INVALID_STORAGE_IDENTITY);
            return CleanupTaskProcessingResult.terminal();
        }
        try {
            storage.delete(snapshot.storageKey());
            taskService.markCompleted(snapshot.taskId(), snapshot.lockVersion());
            return CleanupTaskProcessingResult.completed();
        } catch (RuntimeException ex) {
            taskService.markRetry(
                    snapshot.taskId(),
                    snapshot.lockVersion(),
                    snapshot.attemptCount(),
                    PracticeSpeakingMediaCleanupErrorCode.DELETE_FAILED);
            log.warn(CLEANUP_FAILED_EVENT);
            return CleanupTaskProcessingResult.retry();
        }
    }

    private static boolean isValidLocalStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.length() > 512) {
            return false;
        }
        String key = storageKey.trim();
        if (!key.equals(storageKey) || !key.equals(key.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (key.startsWith("/") || key.startsWith("\\") || key.contains("\\") || key.contains("..")) {
            return false;
        }
        if (key.length() >= 2 && Character.isLetter(key.charAt(0)) && key.charAt(1) == ':') {
            return false;
        }
        if (key.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        for (String segment : key.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    public record CleanupBatchResult(
            int processed,
            int completed,
            int retried,
            int terminal,
            int skipped,
            int failed
    ) {}

    public record CleanupTaskProcessingResult(Outcome outcome) {
        static CleanupTaskProcessingResult completed() {
            return new CleanupTaskProcessingResult(Outcome.COMPLETED);
        }

        static CleanupTaskProcessingResult retry() {
            return new CleanupTaskProcessingResult(Outcome.RETRY);
        }

        static CleanupTaskProcessingResult terminal() {
            return new CleanupTaskProcessingResult(Outcome.TERMINAL);
        }

        static CleanupTaskProcessingResult skipped() {
            return new CleanupTaskProcessingResult(Outcome.SKIPPED);
        }

        public enum Outcome {
            COMPLETED,
            RETRY,
            TERMINAL,
            SKIPPED
        }
    }
}
