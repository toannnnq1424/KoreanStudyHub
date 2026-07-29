package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PracticeAttemptDeadlineTransactions {

    private final PracticeAttemptRepository attemptRepository;

    public PracticeAttemptDeadlineTransactions(
            PracticeAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureDisposition recordFailure(
            Long attemptId,
            String errorCode,
            LocalDateTime now) {
        PracticeAttempt attempt = attemptRepository
                .findByIdForUpdate(attemptId)
                .orElse(null);
        if (attempt == null
                || !attempt.isDeadlineReconcileDue(now)) {
            return FailureDisposition.NO_LONGER_ELIGIBLE;
        }
        attempt.recordDeadlineReconcileFailure(errorCode, now);
        attemptRepository.save(attempt);
        return attempt.isDeadlineReconcileQuarantined()
                ? FailureDisposition.QUARANTINED
                : FailureDisposition.RETRY_SCHEDULED;
    }

    public enum FailureDisposition {
        RETRY_SCHEDULED,
        QUARANTINED,
        NO_LONGER_ELIGIBLE
    }
}
