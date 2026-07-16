package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.security.Role;
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
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeAuthoringCollaborationRepository collaborationRepository =
            mock(PracticeAuthoringCollaborationRepository.class);
    private PracticeAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new PracticeAuthorizationService(
                jdbcTemplate, userRepository, draftRepository, setRepository,
                collaborationRepository);
    }

    @Test
    void ownerCanEditOwnLockedSet() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        allow(11L, PracticeAction.EDIT);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        PracticeAuthorizationService.Decision decision =
                service.requireSet(10L, 11L, PracticeAction.EDIT);

        assertTrue(decision.ownerLocked());
    }

    @Test
    void collaboratorGrantAllowsCanonicalContentActions() throws Exception {
        PracticeSet set = set(10L, 11L);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                10L, 22L);
        allow(22L, PracticeAction.EDIT);
        allow(22L, PracticeAction.PUBLISH);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findBySetIdAndCollaboratorIdAndRevokedAtIsNull(
                        10L, 22L)).thenReturn(Optional.of(grant));

        assertFalse(service.requireSet(10L, 22L, PracticeAction.EDIT)
                .ownerLocked());
        assertFalse(service.requireSet(10L, 22L, PracticeAction.PUBLISH)
                .ownerLocked());
    }

    @Test
    void ownerLockBlocksCollaboratorMutation() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                10L, 22L);
        allow(22L, PracticeAction.EDIT);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findBySetIdAndCollaboratorIdAndRevokedAtIsNull(
                        10L, 22L)).thenReturn(Optional.of(grant));

        assertThrows(AccessDeniedException.class,
                () -> service.requireSet(10L, 22L, PracticeAction.EDIT));
    }

    @Test
    void unrelatedLecturerCannotMutateOwnerContent() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        allow(33L, PracticeAction.EDIT);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findBySetIdAndCollaboratorIdAndRevokedAtIsNull(
                        10L, 33L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.requireSet(10L, 33L, PracticeAction.EDIT));
    }

    @Test
    void explicitPermissionDenialStopsEvenOwner() throws Exception {
        PracticeSet set = set(10L, 11L);
        deny(11L, PracticeAction.EDIT);
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));

        assertThrows(AccessDeniedException.class,
                () -> service.requireSet(10L, 11L, PracticeAction.EDIT));
    }

    @Test
    void studentHeadAndAdminCannotAuthorEvenWithDirectPermission() {
        for (Role role : new Role[]{Role.STUDENT, Role.HEAD, Role.ADMIN}) {
            Long actorId = 100L + role.ordinal();
            allowPermissionOnly(actorId, PracticeAction.CREATE);
            User actor = activeUser(role);
            when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

            assertThrows(AccessDeniedException.class,
                    () -> service.requireGlobal(actorId, PracticeAction.CREATE));
        }
    }

    @Test
    void inactiveOrLockedLecturerCannotAuthor() {
        User inactive = mock(User.class);
        when(inactive.getRole()).thenReturn(Role.LECTURER);
        when(inactive.isActive()).thenReturn(false);
        when(userRepository.findById(41L)).thenReturn(Optional.of(inactive));
        allowPermissionOnly(41L, PracticeAction.CREATE);

        User locked = mock(User.class);
        when(locked.getRole()).thenReturn(Role.LECTURER);
        when(locked.isActive()).thenReturn(true);
        when(locked.isLocked()).thenReturn(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(locked));
        allowPermissionOnly(42L, PracticeAction.CREATE);

        assertThrows(AccessDeniedException.class,
                () -> service.requireGlobal(41L, PracticeAction.CREATE));
        assertThrows(AccessDeniedException.class,
                () -> service.requireGlobal(42L, PracticeAction.CREATE));
    }

    @Test
    void linkedSetLockAlsoBlocksDraftCollaborator() throws Exception {
        PracticeSet set = set(10L, 11L);
        set.lock(11L);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "",  "GLOBAL", null,
                "DRAFT", 11L, "{}");
        setId(draft, 20L);
        draft.setPublishedSetId(10L);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                10L, 22L);
        allow(22L, PracticeAction.EDIT);
        when(draftRepository.findById(20L)).thenReturn(Optional.of(draft));
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(collaborationRepository
                .findBySetIdAndCollaboratorIdAndRevokedAtIsNull(
                        10L, 22L)).thenReturn(Optional.of(grant));

        assertThrows(AccessDeniedException.class,
                () -> service.requireDraft(20L, 22L, PracticeAction.EDIT));
    }

    private void allow(Long actorId, PracticeAction action) {
        User lecturer = activeUser(Role.LECTURER);
        when(userRepository.findById(actorId))
                .thenReturn(Optional.of(lecturer));
        allowPermissionOnly(actorId, action);
    }

    private void allowPermissionOnly(Long actorId, PracticeAction action) {
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
                "Set", "", "READING",  "GLOBAL",
                null, null, "{}", "PUBLISHED", ownerId);
        setId(set, id);
        return set;
    }

    private static User activeUser(Role role) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return user;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
