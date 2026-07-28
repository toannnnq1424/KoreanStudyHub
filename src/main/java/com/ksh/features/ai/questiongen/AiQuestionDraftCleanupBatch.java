package com.ksh.features.ai.questiongen;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Deletes one bounded batch in its own transaction so a retention sweep never
 * holds a single transaction open across the whole backlog.
 */
@Service
class AiQuestionDraftCleanupBatch {

    private final AiQuestionDraftSessionRepository repository;

    AiQuestionDraftCleanupBatch(AiQuestionDraftSessionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public int deleteExpired(LocalDateTime cutoff, int batchSize) {
        return repository.deleteExpiredBatch(cutoff, batchSize);
    }
}
