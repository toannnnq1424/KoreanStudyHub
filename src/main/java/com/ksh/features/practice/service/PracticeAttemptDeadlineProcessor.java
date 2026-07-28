package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(
        name = "app.practice.attempt-deadline.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PracticeAttemptDeadlineProcessor {

    private static final Logger log = LoggerFactory.getLogger(
            PracticeAttemptDeadlineProcessor.class);

    private final PracticeAttemptRepository attemptRepository;
    private final PracticeService practiceService;
    private final PracticeAttemptDiscardService discardService;
    private final PracticeAttemptDeadlineTransactions transactions;

    public PracticeAttemptDeadlineProcessor(
            PracticeAttemptRepository attemptRepository,
            PracticeService practiceService,
            PracticeAttemptDiscardService discardService,
            PracticeAttemptDeadlineTransactions transactions) {
        this.attemptRepository = attemptRepository;
        this.practiceService = practiceService;
        this.discardService = discardService;
        this.transactions = transactions;
    }

    @Scheduled(
            initialDelayString =
                    "${app.practice.attempt-deadline.initial-delay:PT5S}",
            fixedDelayString =
                    "${app.practice.attempt-deadline.fixed-delay:PT5S}")
    public void runScheduledBatch() {
        processExpired(50);
    }

    public int processExpired(int limit) {
        if (limit <= 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = attemptRepository
                .findExpiredInProgressAttemptIds(
                        now,
                        PageRequest.of(0, Math.min(limit, 100)));
        int finalized = 0;
        for (Long id : ids) {
            PracticeAttempt attempt =
                    attemptRepository.findById(id).orElse(null);
            if (attempt == null
                    || !attempt.isDeadlineReconcileDue(now)) {
                continue;
            }
            try {
                if ("SPEAKING".equals(attempt.getSkill())) {
                    discardService.discardForOwner(
                            attempt.getId(), attempt.getUserId());
                } else {
                    practiceService.submitAttempt(
                            attempt.getId(),
                            attempt.getUserId(),
                            Map.of(),
                            attempt.getLockVersion());
                }
                finalized++;
            } catch (RuntimeException exception) {
                String category =
                        exception.getClass().getSimpleName();
                PracticeAttemptDeadlineTransactions.FailureDisposition
                        disposition;
                try {
                    disposition = transactions.recordFailure(
                            id, category, LocalDateTime.now());
                } catch (RuntimeException persistenceFailure) {
                    log.warn(
                            "[PracticeDeadline] Failed to persist reconciliation failure attemptId={} category={} persistenceCategory={}",
                            id,
                            category,
                            persistenceFailure.getClass().getSimpleName());
                    continue;
                }
                if (PracticeAttemptDeadlineTransactions.FailureDisposition
                        .QUARANTINED.equals(disposition)) {
                    log.error(
                            "[PracticeDeadline] Quarantined expired attempt after bounded reconciliation failures attemptId={} category={}",
                            id,
                            category);
                } else {
                    log.info(
                            "[PracticeDeadline] Finalization raced or scheduled retry attemptId={} category={} disposition={}",
                            id,
                            category,
                            disposition);
                }
            }
        }
        return finalized;
    }
}
