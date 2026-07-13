package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeLifecycleServiceTest {

    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);

    private PracticeLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new PracticeLifecycleService(authorizationService, setRepository);
    }

    @Test
    void lockAndUnlockSetPersistOwnerState() {
        PracticeSet set = publishedSet(11L);
        allowSet(10L, 11L, PracticeAction.LOCK);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        service.lockSet(10L, 11L);
        assertTrue(set.isOwnerLocked());
        assertEquals(11L, set.getLockedBy());
        assertNotNull(set.getLockedAt());

        service.unlockSet(10L, 11L);
        assertFalse(set.isOwnerLocked());
        assertNull(set.getLockedBy());
        assertNull(set.getLockedAt());
    }

    @Test
    void archiveAndUnarchivePreserveExplicitLifecycle() {
        PracticeSet set = publishedSet(11L);
        allowSet(10L, 11L, PracticeAction.ARCHIVE);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        service.archiveSet(10L, 11L);
        assertEquals(PracticeSet.STATUS_ARCHIVED, set.getStatus());
        assertNotNull(set.getArchivedAt());

        service.unarchiveSet(10L, 11L);
        assertEquals(PracticeSet.STATUS_PUBLISHED, set.getStatus());
        assertNull(set.getArchivedAt());
    }

    @Test
    void unarchiveRejectsSetThatWasNeverArchived() {
        PracticeSet set = publishedSet(11L);
        allowSet(10L, 11L, PracticeAction.ARCHIVE);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        assertThrows(IllegalStateException.class,
                () -> service.unarchiveSet(10L, 11L));
    }

    private void allowSet(Long setId, Long actorId, PracticeAction action) {
        when(authorizationService.requireSetOwner(setId, actorId, action))
                .thenReturn(new PracticeAuthorizationService.Decision(actorId, false));
    }

    private static PracticeSet publishedSet(Long ownerId) {
        return new PracticeSet("Set", "", "READING",  "GLOBAL",
                null, null, "{}", PracticeSet.STATUS_PUBLISHED, ownerId);
    }
}
