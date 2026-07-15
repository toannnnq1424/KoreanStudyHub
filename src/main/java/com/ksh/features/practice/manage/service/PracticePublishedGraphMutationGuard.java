package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticePublishedGraphMutationGuard {

    private final PracticeSetRepository setRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticeSpeakingMediaRepository speakingMediaRepository;

    public PracticePublishedGraphMutationGuard(PracticeSetRepository setRepository,
                                               PracticeAttemptRepository attemptRepository,
                                               PracticeSpeakingMediaRepository speakingMediaRepository) {
        this.setRepository = setRepository;
        this.attemptRepository = attemptRepository;
        this.speakingMediaRepository = speakingMediaRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PracticeSet lockAndAssertRestoreAllowed(Long setId) {
        return lockAndAssertNoLearnerHistory(setId, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PracticeSet lockAndAssertRepublishAllowed(Long setId) {
        return lockAndAssertNoLearnerHistory(setId, false);
    }

    private PracticeSet lockAndAssertNoLearnerHistory(Long setId, boolean restore) {
        PracticeSet set = setRepository.findByIdForUpdate(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu gốc không tồn tại."));
        boolean mutableAttemptHistoryExists =
                attemptRepository.findFirstUnversionedIdBySetIdForShare(setId).isPresent();
        boolean liveSpeakingMediaExists =
                speakingMediaRepository.findFirstIdByQuestionSetIdForShare(setId).isPresent();
        if (mutableAttemptHistoryExists || liveSpeakingMediaExists) {
            throw restore
                    ? PublishedPracticeGraphMutationBlockedException.forRestore()
                    : PublishedPracticeGraphMutationBlockedException.forRepublish();
        }
        return set;
    }
}
