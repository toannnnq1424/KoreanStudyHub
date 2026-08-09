package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeAuthorizationService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PracticeDraftRepository draftRepository;
    private final PracticeSetRepository setRepository;

    public PracticeAuthorizationService(JdbcTemplate jdbcTemplate,
                                        UserRepository userRepository,
                                        PracticeDraftRepository draftRepository,
                                        PracticeSetRepository setRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
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
        if (actorId.equals(draft.getOwnerId())) {
            return new Decision(draft.getOwnerId());
        }
        throw denied(action);
    }

    @Transactional(readOnly = true)
    public boolean canReadDraft(Long draftId, Long actorId) {
        try {
            requireDraft(draftId, actorId, PracticeAction.READ);
            return true;
        } catch (EntityNotFoundException | AccessDeniedException exception) {
            return false;
        }
    }

    @Transactional
    public Decision requireSet(Long setId, Long actorId, PracticeAction action) {
        requireGlobal(actorId, action);
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        if (actorId.equals(set.getCreatedBy())) {
            return new Decision(set.getCreatedBy());
        }
        throw denied(action);
    }

    @Transactional(readOnly = true)
    public boolean canReadSet(Long setId, Long actorId) {
        try {
            requireSet(setId, actorId, PracticeAction.READ);
            return true;
        } catch (EntityNotFoundException | AccessDeniedException exception) {
            return false;
        }
    }

    @Transactional
    public Decision requireSetOwner(Long setId, Long actorId,
                                    PracticeAction action) {
        requireGlobal(actorId, action);
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        if (actorId.equals(set.getCreatedBy())) {
            return new Decision(set.getCreatedBy());
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
            return new Decision(draft.getOwnerId());
        }
        throw denied(action);
    }

    private static AccessDeniedException denied(PracticeAction action) {
        return new AccessDeniedException(
                "Bạn không có quyền thực hiện thao tác " + action.permissionKey() + ".");
    }

    public record Decision(Long ownerId) {
    }
}
