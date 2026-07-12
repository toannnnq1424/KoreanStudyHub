package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeDraftRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeLifecycleServiceTest {

    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final PracticeGovernanceAuditService auditService =
            mock(PracticeGovernanceAuditService.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);

    private PracticeLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new PracticeLifecycleService(
                authorizationService, auditService, setRepository, draftRepository);
    }

    @Test
    void lockAndUnlockSetPersistStateAndAuditActor() {
        PracticeSet set = publishedSet(11L);
        allowSet(10L, 11L, PracticeAction.LOCK);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        service.lockSet(10L, 11L, null);
        assertTrue(set.isOwnerLocked());
        assertEquals(11L, set.getLockedBy());
        assertNotNull(set.getLockedAt());
        verify(auditService).record(eq("OWNER_LOCKED"), eq("SET"), eq(10L),
                eq(11L), eq(11L), isNull(), eq(false), isNull(), anyString(), anyString());

        service.unlockSet(10L, 11L, null);
        assertFalse(set.isOwnerLocked());
        assertNull(set.getLockedBy());
        assertNull(set.getLockedAt());
        verify(auditService).record(eq("OWNER_UNLOCKED"), eq("SET"), eq(10L),
                eq(11L), eq(11L), isNull(), eq(false), isNull(), anyString(), anyString());
    }

    @Test
    void draftLockUsesDraftBoundary() {
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "TOPIK_II", "GLOBAL", null, "DRAFT", 11L, "{}");
        allowDraft(20L, 11L, PracticeAction.LOCK);
        when(draftRepository.findById(20L)).thenReturn(Optional.of(draft));

        service.lockDraft(20L, 11L, null);

        assertTrue(draft.isOwnerLocked());
        verify(draftRepository).save(draft);
        verify(auditService).record(eq("OWNER_LOCKED"), eq("DRAFT"), eq(20L),
                eq(11L), eq(11L), isNull(), eq(false), isNull(), anyString(), anyString());
    }

    @Test
    void archiveAndUnarchivePreserveExplicitLifecycle() {
        PracticeSet set = publishedSet(11L);
        allowSet(10L, 11L, PracticeAction.ARCHIVE);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        service.archiveSet(10L, 11L, null);
        assertEquals(PracticeSet.STATUS_ARCHIVED, set.getStatus());
        assertNotNull(set.getArchivedAt());

        service.unarchiveSet(10L, 11L, null);
        assertEquals(PracticeSet.STATUS_PUBLISHED, set.getStatus());
        assertNull(set.getArchivedAt());
        verify(auditService).record(eq("SET_ARCHIVED"), eq("SET"), eq(10L),
                eq(11L), eq(11L), isNull(), eq(false), isNull(), anyString(), anyString());
        verify(auditService).record(eq("SET_UNARCHIVED"), eq("SET"), eq(10L),
                eq(11L), eq(11L), isNull(), eq(false), isNull(), anyString(), anyString());
    }

    @Test
    void unarchiveRejectsSetThatWasNeverArchived() {
        PracticeSet set = publishedSet(11L);
        allowSet(10L, 11L, PracticeAction.ARCHIVE);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        assertThrows(IllegalStateException.class,
                () -> service.unarchiveSet(10L, 11L, null));
    }

    private void allowSet(Long setId, Long actorId, PracticeAction action) {
        when(authorizationService.requireSetOwnerOrOverride(setId, actorId, action, null))
                .thenReturn(new PracticeAuthorizationService.Decision(actorId, false, false, true));
    }

    private void allowDraft(Long draftId, Long actorId, PracticeAction action) {
        when(authorizationService.requireDraftOwnerOrOverride(draftId, actorId, action, null))
                .thenReturn(new PracticeAuthorizationService.Decision(actorId, false, false, true));
    }

    private static PracticeSet publishedSet(Long ownerId) {
        return new PracticeSet("Set", "", "READING", "TOPIK_II", "GLOBAL",
                null, null, "{}", PracticeSet.STATUS_PUBLISHED, ownerId);
    }
}
