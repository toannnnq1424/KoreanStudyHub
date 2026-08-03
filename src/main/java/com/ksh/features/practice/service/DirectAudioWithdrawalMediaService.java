package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Enqueues exact private-media deletion inside the consent withdrawal transaction. */
@Service
public class DirectAudioWithdrawalMediaService {
    private final PracticeAttemptRepository attempts;
    private final PracticeSpeakingMediaRepository media;
    private final PracticeSpeakingMediaCleanupTaskService cleanup;

    public DirectAudioWithdrawalMediaService(
            PracticeAttemptRepository attempts,
            PracticeSpeakingMediaRepository media,
            PracticeSpeakingMediaCleanupTaskService cleanup) {
        this.attempts = attempts;
        this.media = media;
        this.cleanup = cleanup;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int enqueueForWithdrawal(
            Long learnerId, Long attemptId, String authorizationEvidenceId) {
        PracticeAttempt attempt = attempts.findByIdAndUserIdForUpdate(attemptId, learnerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Practice attempt was not found."));
        if (!"SPEAKING".equals(attempt.getSkill())) {
            throw new SecurityException("Direct-audio withdrawal requires an owned Speaking attempt.");
        }
        List<PracticeSpeakingMedia> rows = media.findByAttemptIdForUpdateOrderByIdAsc(attemptId);
        int enqueued = 0;
        for (PracticeSpeakingMedia item : rows) {
            if (item.getStatus() == PracticeSpeakingMediaStatus.DELETED) {
                continue;
            }
            item.markDeletionPending();
            cleanup.enqueueConsentWithdrawal(
                    item.getId(), item.getStorageProvider(), item.getStorageProfileCode(),
                    item.getStorageKey(), authorizationEvidenceId);
            enqueued++;
        }
        media.flush();
        return enqueued;
    }
}
