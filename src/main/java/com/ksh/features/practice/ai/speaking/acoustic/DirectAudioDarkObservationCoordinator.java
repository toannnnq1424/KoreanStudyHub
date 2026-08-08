package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.service.DirectAudioReviewerAccessAudit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/** Spring wiring for reviewer-only dark metadata inspection. */
@Service
public class DirectAudioDarkObservationCoordinator {
    private final DirectAudioDarkObservationService delegate;
    private final DirectAudioReviewerAccessAudit audit;

    @Autowired
    public DirectAudioDarkObservationCoordinator(
            DirectAudioDarkObservationJdbcStore store,
            ObjectMapper mapper,
            DirectAudioReviewerAccessAudit audit,
            ObjectProvider<Clock> clockProvider) {
        this(store, mapper, audit,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    DirectAudioDarkObservationCoordinator(
            DirectAudioDarkObservationJdbcStore store,
            ObjectMapper mapper,
            DirectAudioReviewerAccessAudit audit,
            Clock clock) {
        this.delegate = new DirectAudioDarkObservationService(store, mapper, clock);
        this.audit = audit;
    }

    public Optional<DirectAudioDarkObservationService.ReviewerView> inspect(
            Long reviewerId, Long attemptId) {
        return delegate.inspect(reviewerId, attemptId).map(view -> {
            audit.recordAuthorized(
                    DirectAudioReviewerAccessAudit.Action.INSPECTION_METADATA,
                    reviewerId, view.attemptId(), view.questionId(), view.mediaId(),
                    view.observationKey());
            return view;
        });
    }
}
