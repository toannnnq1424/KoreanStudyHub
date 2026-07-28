package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAttemptDeadlineTransactionsTest {

    @Test
    void staleSecondSelectionCannotAdvanceBackoffCounter() {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 29, 10, 0);
        PracticeAttempt attempt = new PracticeAttempt(
                7L, 8L, 9L, "WRITING", 10L);
        attempt.setDeadlineAt(now.minusMinutes(1));
        PracticeAttemptRepository repository =
                mock(PracticeAttemptRepository.class);
        when(repository.findByIdForUpdate(17L))
                .thenReturn(Optional.of(attempt));
        PracticeAttemptDeadlineTransactions transactions =
                new PracticeAttemptDeadlineTransactions(repository);

        assertThat(transactions.recordFailure(
                17L, "MalformedSnapshotException", now))
                .isEqualTo(
                        PracticeAttemptDeadlineTransactions
                                .FailureDisposition.RETRY_SCHEDULED);
        assertThat(transactions.recordFailure(
                17L, "MalformedSnapshotException", now))
                .isEqualTo(
                        PracticeAttemptDeadlineTransactions
                                .FailureDisposition.NO_LONGER_ELIGIBLE);

        assertThat(attempt.getDeadlineReconcileAttempts())
                .isEqualTo(1);
        verify(repository, times(1)).save(attempt);
    }
}
