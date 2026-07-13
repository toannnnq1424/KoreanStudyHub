package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePublishedGraphMutationGuardTest {

    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeAttemptRepository attemptRepository = mock(PracticeAttemptRepository.class);
    private PracticePublishedGraphMutationGuard guard;
    private PracticeSet set;

    @BeforeEach
    void setUp() {
        guard = new PracticePublishedGraphMutationGuard(setRepository, attemptRepository);
        set = new PracticeSet("Set", "Description", "READING",
                "GLOBAL", null, null, "{}", PracticeSet.STATUS_PUBLISHED, 99L);
        when(setRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(set));
        when(attemptRepository.findFirstUnversionedIdBySetIdForShare(10L))
                .thenReturn(Optional.empty());
    }

    @Test
    void restoreAllowedLocksSetAndPassesWhenNoLearnerHistoryExists() {
        PracticeSet locked = guard.lockAndAssertRestoreAllowed(10L);

        assertSame(set, locked);
        verify(setRepository).findByIdForUpdate(10L);
    }

    @Test
    void restoreBlockedWhenUnversionedPracticeAttemptExists() {
        when(attemptRepository.findFirstUnversionedIdBySetIdForShare(10L))
                .thenReturn(Optional.of(77L));

        PublishedPracticeGraphMutationBlockedException exception = assertThrows(
                PublishedPracticeGraphMutationBlockedException.class,
                () -> guard.lockAndAssertRestoreAllowed(10L)
        );

        assertEquals(PublishedPracticeGraphMutationBlockedException.RESTORE_MESSAGE, exception.getMessage());
    }

    @Test
    void restoreAllowedWhenEveryAttemptIsLockedToImmutableVersion() {
        when(attemptRepository.findFirstIdBySetIdForShare(10L))
                .thenReturn(Optional.of(77L));
        when(attemptRepository.findFirstUnversionedIdBySetIdForShare(10L))
                .thenReturn(Optional.empty());

        assertSame(set, guard.lockAndAssertRestoreAllowed(10L));

    }

    @Test
    void missingSetIsBoundedNotFound() {
        when(setRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> guard.lockAndAssertRepublishAllowed(404L));
        verify(attemptRepository, never()).findFirstUnversionedIdBySetIdForShare(404L);
    }
}
