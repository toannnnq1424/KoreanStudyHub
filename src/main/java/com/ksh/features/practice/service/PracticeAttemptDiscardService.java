package com.ksh.features.practice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PracticeAttemptDiscardService {

    private static final Logger log = LoggerFactory.getLogger(PracticeAttemptDiscardService.class);
    private static final String CLEANUP_FAILURE_EVENT = "Speaking attempt discard cleanup failed";

    private final PracticeAttemptDiscardTransactionService transactionService;
    private final PracticeSpeakingMediaCleanupProcessor cleanupProcessor;

    public PracticeAttemptDiscardService(
            PracticeAttemptDiscardTransactionService transactionService,
            PracticeSpeakingMediaCleanupProcessor cleanupProcessor) {
        this.transactionService = transactionService;
        this.cleanupProcessor = cleanupProcessor;
    }

    public PracticeAttemptDiscardResult discardForOwner(Long attemptId, Long userId) {
        PracticeAttemptDiscardResult result = transactionService.discardForOwner(attemptId, userId);
        for (Long cleanupTaskId : result.immediateCleanupTaskIds()) {
            try {
                cleanupProcessor.processTaskNow(cleanupTaskId);
            } catch (RuntimeException ex) {
                log.warn(CLEANUP_FAILURE_EVENT);
            }
        }
        return result;
    }
}
