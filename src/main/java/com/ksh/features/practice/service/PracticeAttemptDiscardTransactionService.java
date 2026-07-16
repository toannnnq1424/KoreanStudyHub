package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class PracticeAttemptDiscardTransactionService {

    private final PracticeAttemptRepository attemptRepository;
    private final PracticeSpeakingMediaRepository mediaRepository;
    private final PracticeSpeakingMediaCleanupTaskService cleanupTaskService;
    private final Clock clock;

    public PracticeAttemptDiscardTransactionService(
            PracticeAttemptRepository attemptRepository,
            PracticeSpeakingMediaRepository mediaRepository,
            PracticeSpeakingMediaCleanupTaskService cleanupTaskService,
            ObjectProvider<Clock> clockProvider) {
        this.attemptRepository = attemptRepository;
        this.mediaRepository = mediaRepository;
        this.cleanupTaskService = cleanupTaskService;
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Transactional
    public PracticeAttemptDiscardResult discardForOwner(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserIdForUpdate(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Practice attempt was not found."));
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())
                && !PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())) {
            throw new PracticeAttemptConflictException(
                    "Attempt cannot be discarded in its current state.");
        }

        LocalDateTime discardedAt = attempt.getDiscardedAt();
        if (discardedAt == null) {
            discardedAt = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC).withNano(0);
            attempt.discard(discardedAt);
        }

        List<PracticeSpeakingMedia> mediaRows =
                mediaRepository.findByAttemptIdForUpdateOrderByIdAsc(attemptId);
        for (PracticeSpeakingMedia media : mediaRows) {
            media.markDeleted(discardedAt);
            cleanupTaskService.enqueueDiscardAttempt(
                    media.getStorageProvider(),
                    media.getStorageKey(),
                    discardedAt);
        }

        attemptRepository.flush();
        mediaRepository.flush();
        return new PracticeAttemptDiscardResult(
                attempt.getId(),
                attempt.getStatus(),
                discardedAt,
                mediaRows.size(),
                List.of());
    }
}
