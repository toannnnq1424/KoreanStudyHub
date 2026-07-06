package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticePublishedGraphMutationGuard {

    private final PracticeSetRepository setRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticeSubmissionRepository submissionRepository;

    public PracticePublishedGraphMutationGuard(PracticeSetRepository setRepository,
                                               PracticeAttemptRepository attemptRepository,
                                               PracticeSubmissionRepository submissionRepository) {
        this.setRepository = setRepository;
        this.attemptRepository = attemptRepository;
        this.submissionRepository = submissionRepository;
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
        if (attemptRepository.findFirstIdBySetIdForShare(setId).isPresent()
                || submissionRepository.existsBySetId(setId)) {
            throw restore
                    ? PublishedPracticeGraphMutationBlockedException.forRestore()
                    : PublishedPracticeGraphMutationBlockedException.forRepublish();
        }
        return set;
    }
}
