package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/** Spring wiring for reviewer-only dark metadata inspection. */
@Service
public class DirectAudioDarkObservationCoordinator {
    private final DirectAudioDarkObservationService delegate;

    @Autowired
    public DirectAudioDarkObservationCoordinator(
            DirectAudioDarkObservationJdbcStore store,
            ObjectMapper mapper,
            ObjectProvider<Clock> clockProvider) {
        this(store, mapper, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    DirectAudioDarkObservationCoordinator(
            DirectAudioDarkObservationJdbcStore store,
            ObjectMapper mapper,
            Clock clock) {
        this.delegate = new DirectAudioDarkObservationService(store, mapper, clock);
    }

    public Optional<DirectAudioDarkObservationService.ReviewerView> inspect(
            Long reviewerId, Long attemptId) {
        return delegate.inspect(reviewerId, attemptId);
    }
}
