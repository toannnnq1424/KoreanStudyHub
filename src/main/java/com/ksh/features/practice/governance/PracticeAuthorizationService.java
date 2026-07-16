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
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PracticeAuthorizationService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PracticeDraftRepository draftRepository;
    private final PracticeSetRepository setRepository;
    private final PracticeAuthoringCollaborationRepository collaborationRepository;

    public PracticeAuthorizationService(JdbcTemplate jdbcTemplate,
                                        UserRepository userRepository,
                                        PracticeDraftRepository draftRepository,
                                        PracticeSetRepository setRepository,
                                        PracticeAuthoringCollaborationRepository collaborationRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
        this.collaborationRepository = collaborationRepository;
    }

    public void requireGlobal(Long actorId, PracticeAction action) {
        if (!isActiveLecturer(actorId) || !hasPermission(actorId, action)) {
            throw denied(action);
        }
    }

    private boolean isActiveLecturer(Long actorId) {
        if (actorId == null) {
            return false;
        }
        return userRepository.findById(actorId)
                .filter(user -> user.getRole() == Role.LECTURER)
                .filter(User::isActive)
                .filter(user -> !user.isLocked())
                .isPresent();
    }

    public boolean hasPermission(Long actorId, PracticeAction action) {
        if (actorId == null || action == null) return false;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM v_user_effective_permissions
                WHERE user_id = ? AND feature_key = ? AND is_granted = 1
                """, Integer.class, actorId, action.permissionKey());
        return count != null && count > 0;
    }

    @Transactional
    public Decision requireDraft(Long draftId, Long actorId, PracticeAction action) {
        requireGlobal(actorId, action);
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
        boolean locked = isLinkedSetLocked(draft);
        if (actorId.equals(draft.getOwnerId())) {
            return new Decision(draft.getOwnerId(), locked);
        }

        Optional<PracticeAuthoringCollaboration> collaboration = draft.getPublishedSetId() == null
                ? Optional.empty()
                : collaborationRepository.findBySetIdAndCollaboratorIdAndRevokedAtIsNull(
                        draft.getPublishedSetId(), actorId);
        boolean grantAllows = collaboration.isPresent() && isCollaborationAction(action);
        if ((!locked || canReadLocked(action)) && grantAllows) {
            return new Decision(draft.getOwnerId(), locked);
        }
        throw denied(action);
    }

    @Transactional
    public Decision requireSet(Long setId, Long actorId, PracticeAction action) {
        requireGlobal(actorId, action);
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        if (actorId.equals(set.getCreatedBy())) {
            return new Decision(set.getCreatedBy(), set.isOwnerLocked());
        }
        Optional<PracticeAuthoringCollaboration> collaboration = collaborationRepository
                .findBySetIdAndCollaboratorIdAndRevokedAtIsNull(setId, actorId);
        boolean grantAllows = collaboration.isPresent() && isCollaborationAction(action);
        if ((!set.isOwnerLocked() || canReadLocked(action)) && grantAllows) {
            return new Decision(set.getCreatedBy(), set.isOwnerLocked());
        }
        throw denied(action);
    }

    @Transactional
    public Decision requireSetOwner(Long setId, Long actorId,
                                    PracticeAction action) {
        requireGlobal(actorId, action);
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        if (actorId.equals(set.getCreatedBy())) {
            return new Decision(set.getCreatedBy(), set.isOwnerLocked());
        }
        throw denied(action);
    }

    @Transactional
    public Decision requireDraftOwner(Long draftId, Long actorId,
                                      PracticeAction action) {
        requireGlobal(actorId, action);
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
        if (actorId.equals(draft.getOwnerId())) {
            return new Decision(draft.getOwnerId(), isLinkedSetLocked(draft));
        }
        throw denied(action);
    }

    private boolean isLinkedSetLocked(PracticeDraft draft) {
        return draft.getPublishedSetId() != null
                && setRepository.findById(draft.getPublishedSetId())
                .map(PracticeSet::isOwnerLocked)
                .orElse(false);
    }

    private static boolean isCollaborationAction(PracticeAction action) {
        return action == PracticeAction.READ
                || action == PracticeAction.EDIT
                || action == PracticeAction.PUBLISH
                || action == PracticeAction.RESTORE
                || action == PracticeAction.MATERIAL_MANAGE;
    }

    private static boolean canReadLocked(PracticeAction action) {
        return action == PracticeAction.READ;
    }

    private static AccessDeniedException denied(PracticeAction action) {
        return new AccessDeniedException(
                "Bạn không có quyền thực hiện thao tác " + action.permissionKey() + ".");
    }

    public record Decision(Long ownerId, boolean ownerLocked) {
    }
}
