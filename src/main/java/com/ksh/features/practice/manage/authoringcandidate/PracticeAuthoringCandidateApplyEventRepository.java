package com.ksh.features.practice.manage.authoringcandidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PracticeAuthoringCandidateApplyEventRepository
        extends JpaRepository<PracticeAuthoringCandidateApplyEvent, Long> {

    Optional<PracticeAuthoringCandidateApplyEvent>
            findByCandidateIdAndApplyRequestId(
                    String candidateId, String applyRequestId);
}
