package com.ksh.features.practice.governance;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeCollaborationServiceTest {

    private final PracticeAuthoringCollaborationRepository repository =
            mock(PracticeAuthoringCollaborationRepository.class);
    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private PracticeCollaborationService service;

    @BeforeEach
    void setUp() {
        service = new PracticeCollaborationService(
                repository, authorizationService, userRepository);
    }

    @Test
    void ownerCanGrantFixedSetCollaboration() {
        allowSetOwner(10L, 11L);
        User lecturer = activeUser(Role.LECTURER);
        when(userRepository.findById(22L)).thenReturn(Optional.of(lecturer));
        when(repository.findBySetIdAndCollaboratorId(10L, 22L))
                .thenReturn(Optional.empty());
        when(repository.save(any(PracticeAuthoringCollaboration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeAuthoringCollaboration saved = service.shareSet(
                10L, 22L, 11L);

        assertEquals(10L, saved.getSetId());
        assertEquals(22L, saved.getCollaboratorId());
    }

    @Test
    void ownerCannotGrantCollaborationToSelfOrStudent() {
        allowSetOwner(10L, 11L);

        assertThrows(IllegalArgumentException.class,
                () -> service.shareSet(10L, 11L, 11L));

        User student = activeUser(Role.STUDENT);
        when(userRepository.findById(22L)).thenReturn(Optional.of(student));
        assertThrows(IllegalArgumentException.class,
                () -> service.shareSet(10L, 22L, 11L));
        verify(repository, never()).save(any());
    }

    @Test
    void ownerCannotGrantCollaborationToHeadOrAdmin() {
        allowSetOwner(10L, 11L);

        User head = activeUser(Role.HEAD);
        when(userRepository.findById(22L)).thenReturn(Optional.of(head));
        assertThrows(IllegalArgumentException.class,
                () -> service.shareSet(10L, 22L, 11L));

        User admin = activeUser(Role.ADMIN);
        when(userRepository.findById(23L)).thenReturn(Optional.of(admin));
        assertThrows(IllegalArgumentException.class,
                () -> service.shareSet(10L, 23L, 11L));
        verify(repository, never()).save(any());
    }

    @Test
    void shareByEmailNormalizesLookupAndRejectsUnknownAccount() {
        allowSetOwner(10L, 11L);
        when(userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn"))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.shareSetByEmail(10L, "  lecturer@ksh.edu.vn  ",
                        11L));
        verify(userRepository).findByEmailIgnoreCase("lecturer@ksh.edu.vn");
    }

    @Test
    void ownerCanRevokeGrant() {
        when(authorizationService.requireSetOwner(
                10L, 11L, PracticeAction.EDIT))
                .thenReturn(new PracticeAuthorizationService.Decision(11L, false));
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                10L, 22L);
        when(repository.findBySetIdAndCollaboratorIdAndRevokedAtIsNull(
                10L, 22L)).thenReturn(Optional.of(grant));

        service.revokeSet(10L, 22L, 11L);

        assertNotNull(grant.getRevokedAt());
        verify(repository).save(grant);
    }

    private void allowSetOwner(Long setId, Long ownerId) {
        when(authorizationService.requireSetOwner(
                setId, ownerId, PracticeAction.EDIT))
                .thenReturn(new PracticeAuthorizationService.Decision(ownerId, false));
    }

    private static User activeUser(Role role) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return user;
    }
}
