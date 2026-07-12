package com.ksh.features.practice.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeCollaborationServiceTest {

    private final PracticeAuthoringCollaborationRepository repository =
            mock(PracticeAuthoringCollaborationRepository.class);
    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final PracticeGovernanceAuditService auditService =
            mock(PracticeGovernanceAuditService.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private PracticeCollaborationService service;

    @BeforeEach
    void setUp() {
        service = new PracticeCollaborationService(repository, authorizationService,
                auditService, userRepository, new ObjectMapper());
    }

    @Test
    void ownerCanGrantActionSpecificSetCollaborationAndAuditIt() {
        allowSetOwner(10L, 11L);
        User lecturer = activeUser(Role.LECTURER);
        when(userRepository.findById(22L)).thenReturn(Optional.of(lecturer));
        when(repository.findByTargetTypeAndTargetIdAndCollaboratorId("SET", 10L, 22L))
                .thenReturn(Optional.empty());
        when(repository.save(any(PracticeAuthoringCollaboration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeAuthoringCollaboration saved = service.shareSet(
                10L, 22L, new PracticeCollaborationService.Grants(true, false, true, false),
                11L, null);

        assertEquals(11L, saved.getOwnerId());
        assertEquals(22L, saved.getCollaboratorId());
        assertTrue(saved.isCanEdit());
        assertTrue(saved.isCanRestore());
        verify(auditService).record(eq("COLLABORATOR_GRANTED"), eq("SET"), eq(10L),
                eq(11L), eq(11L), isNull(), eq(false), isNull(), isNull(), anyString());
    }

    @Test
    void ownerCannotGrantCollaborationToSelfOrStudent() {
        allowSetOwner(10L, 11L);

        assertThrows(IllegalArgumentException.class,
                () -> service.shareSet(10L, 11L, null, 11L, null));

        User student = activeUser(Role.STUDENT);
        when(userRepository.findById(22L)).thenReturn(Optional.of(student));
        assertThrows(IllegalArgumentException.class,
                () -> service.shareSet(10L, 22L, null, 11L, null));
        verify(repository, never()).save(any());
    }

    @Test
    void shareByEmailNormalizesLookupAndRejectsUnknownAccount() {
        allowSetOwner(10L, 11L);
        when(userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn"))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.shareSetByEmail(10L, "  lecturer@ksh.edu.vn  ",
                        null, 11L, null));
        verify(userRepository).findByEmailIgnoreCase("lecturer@ksh.edu.vn");
    }

    @Test
    void revokeMarksGrantAndWritesBeforeAuditSnapshot() {
        PracticeAuthorizationService.Decision decision =
                new PracticeAuthorizationService.Decision(11L, true, false, false);
        when(authorizationService.requireSetOwnerOrOverride(
                10L, 33L, PracticeAction.EDIT, "Khắc phục quyền sai"))
                .thenReturn(decision);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                "SET", 10L, 11L, 22L, true, true, true, true, 11L);
        when(repository.findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                "SET", 10L, 22L)).thenReturn(Optional.of(grant));

        service.revokeSet(10L, 22L, 33L, "Khắc phục quyền sai");

        assertNotNull(grant.getRevokedAt());
        verify(repository).save(grant);
        verify(auditService).record(eq("COLLABORATOR_REVOKED"), eq("SET"), eq(10L),
                eq(11L), eq(33L), isNull(), eq(true), eq("Khắc phục quyền sai"),
                anyString(), isNull());
    }

    private void allowSetOwner(Long setId, Long ownerId) {
        when(authorizationService.requireSetOwnerOrOverride(
                setId, ownerId, PracticeAction.EDIT, null))
                .thenReturn(new PracticeAuthorizationService.Decision(
                        ownerId, false, false, true));
    }

    private static User activeUser(Role role) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return user;
    }
}
