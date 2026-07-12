package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAuthorizationServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeAuthoringCollaborationRepository collaborationRepository =
            mock(PracticeAuthoringCollaborationRepository.class);

    private PracticeAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new PracticeAuthorizationService(
                jdbcTemplate, draftRepository, setRepository, collaborationRepository);
    }

    @Test
    void ownerCanEditOwnLockedSet() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        allow(11L, PracticeAction.EDIT);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        PracticeAuthorizationService.Decision decision =
                service.requireSet(10L, 11L, PracticeAction.EDIT, null);

        assertTrue(decision.ownerLocked());
        assertTrue(decision.ownerOrCollaborator());
        assertFalse(decision.overrideUsed());
    }

    @Test
    void collaboratorGrantIsActionSpecific() throws Exception {
        PracticeSet set = set(10L, 11L);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                "SET", 10L, 11L, 22L, true, false, true, true, 11L);
        allow(22L, PracticeAction.EDIT);
        allow(22L, PracticeAction.PUBLISH);
        deny(22L, PracticeAction.EMERGENCY_OVERRIDE);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                        "SET", 10L, 22L)).thenReturn(Optional.of(grant));

        assertTrue(service.requireSet(10L, 22L, PracticeAction.EDIT, null)
                .ownerOrCollaborator());
        assertThrows(AccessDeniedException.class,
                () -> service.requireSet(10L, 22L, PracticeAction.PUBLISH, null));
    }

    @Test
    void ownerLockBlocksCollaboratorMutation() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                "SET", 10L, 11L, 22L, true, true, true, true, 11L);
        allow(22L, PracticeAction.EDIT);
        deny(22L, PracticeAction.EMERGENCY_OVERRIDE);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                        "SET", 10L, 22L)).thenReturn(Optional.of(grant));

        assertThrows(AccessDeniedException.class,
                () -> service.requireSet(10L, 22L, PracticeAction.EDIT, null));
    }

    @Test
    void emergencyOverrideRequiresReasonAndIsReported() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        allow(33L, PracticeAction.EDIT);
        allow(33L, PracticeAction.EMERGENCY_OVERRIDE);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                        "SET", 10L, 33L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.requireSet(10L, 33L, PracticeAction.EDIT, " "));

        PracticeAuthorizationService.Decision decision =
                service.requireSet(10L, 33L, PracticeAction.EDIT, "Sửa lỗi bảo mật");
        assertTrue(decision.overrideUsed());
        assertFalse(decision.ownerOrCollaborator());
    }

    @Test
    void explicitPermissionDenialStopsEvenOwner() throws Exception {
        PracticeSet set = set(10L, 11L);
        deny(11L, PracticeAction.EDIT);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        assertThrows(AccessDeniedException.class,
                () -> service.requireSet(10L, 11L, PracticeAction.EDIT, null));
    }

    @Test
    void linkedSetLockAlsoBlocksDraftCollaborator() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "TOPIK_II", "GLOBAL", null,
                "DRAFT", 11L, "{}");
        setId(draft, 20L);
        draft.setPublishedSetId(10L);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                "DRAFT", 20L, 11L, 22L, true, true, true, true, 11L);
        allow(22L, PracticeAction.EDIT);
        deny(22L, PracticeAction.EMERGENCY_OVERRIDE);
        when(draftRepository.findById(20L)).thenReturn(Optional.of(draft));
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                        "DRAFT", 20L, 22L)).thenReturn(Optional.of(grant));

        assertThrows(AccessDeniedException.class,
                () -> service.requireDraft(20L, 22L, PracticeAction.EDIT, null));
    }

    private void allow(Long actorId, PracticeAction action) {
        permission(actorId, action, 1);
    }

    private void deny(Long actorId, PracticeAction action) {
        permission(actorId, action, 0);
    }

    private void permission(Long actorId, PracticeAction action, int count) {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq(actorId), eq(action.permissionKey())))
                .thenReturn(count);
    }

    private static PracticeSet set(Long id, Long ownerId) throws Exception {
        PracticeSet set = new PracticeSet(
                "Set", "", "READING", "TOPIK_II", "GLOBAL",
                null, null, "{}", "PUBLISHED", ownerId);
        setId(set, id);
        return set;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
