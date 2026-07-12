package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PracticeAuthorizationService {

    private final JdbcTemplate jdbcTemplate;
    private final PracticeDraftRepository draftRepository;
    private final PracticeSetRepository setRepository;
    private final PracticeAuthoringCollaborationRepository collaborationRepository;
    private final PracticeGovernanceAuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired
    public PracticeAuthorizationService(JdbcTemplate jdbcTemplate,
                                        PracticeDraftRepository draftRepository,
                                        PracticeSetRepository setRepository,
                                        PracticeAuthoringCollaborationRepository collaborationRepository,
                                        PracticeGovernanceAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
        this.collaborationRepository = collaborationRepository;
        this.auditService = auditService;
    }

    public PracticeAuthorizationService(JdbcTemplate jdbcTemplate,
                                        PracticeDraftRepository draftRepository,
                                        PracticeSetRepository setRepository,
                                        PracticeAuthoringCollaborationRepository collaborationRepository) {
        this(jdbcTemplate, draftRepository, setRepository, collaborationRepository, null);
    }

    public void requireGlobal(Long actorId, PracticeAction action) {
        if (!hasPermission(actorId, action)) {
            throw denied(action);
        }
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
    public Decision requireDraft(Long draftId, Long actorId, PracticeAction action,
                                 String overrideReason) {
        requireGlobal(actorId, action);
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
        boolean linkedSetLocked = draft.getPublishedSetId() != null
                && setRepository.findById(draft.getPublishedSetId())
                .map(PracticeSet::isOwnerLocked)
                .orElse(false);
        boolean locked = draft.isOwnerLocked() || linkedSetLocked;
        if (actorId.equals(draft.getOwnerId())) {
            return new Decision(draft.getOwnerId(), false, locked, true);
        }

        Optional<PracticeAuthoringCollaboration> collaboration = collaborationRepository
                .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                        PracticeAuthoringCollaboration.TARGET_DRAFT, draftId, actorId);
        if (collaboration.isEmpty() && draft.getPublishedSetId() != null) {
            collaboration = collaborationRepository
                    .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                            PracticeAuthoringCollaboration.TARGET_SET,
                            draft.getPublishedSetId(), actorId);
        }
        boolean grantAllows = collaboration.map(value -> allows(value, action)).orElse(false);
        if ((!locked || canReadLocked(action)) && grantAllows) {
            return new Decision(draft.getOwnerId(), false, locked, true);
        }
        return requireOverride(actorId, action, overrideReason, draft.getOwnerId(), locked,
                PracticeAuthoringCollaboration.TARGET_DRAFT, draftId);
    }

    @Transactional
    public Decision requireSet(Long setId, Long actorId, PracticeAction action,
                               String overrideReason) {
        requireGlobal(actorId, action);
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        if (actorId.equals(set.getCreatedBy())) {
            return new Decision(set.getCreatedBy(), false, set.isOwnerLocked(), true);
        }
        Optional<PracticeAuthoringCollaboration> collaboration = collaborationRepository
                .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                        PracticeAuthoringCollaboration.TARGET_SET, setId, actorId);
        boolean grantAllows = collaboration.map(value -> allows(value, action)).orElse(false);
        if ((!set.isOwnerLocked() || canReadLocked(action)) && grantAllows) {
            return new Decision(set.getCreatedBy(), false, set.isOwnerLocked(), true);
        }
        return requireOverride(actorId, action, overrideReason, set.getCreatedBy(),
                set.isOwnerLocked(), PracticeAuthoringCollaboration.TARGET_SET, setId);
    }

    @Transactional
    public Decision requireSetOwnerOrOverride(Long setId, Long actorId,
                                              PracticeAction action,
                                              String overrideReason) {
        requireGlobal(actorId, action);
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        if (actorId.equals(set.getCreatedBy())) {
            return new Decision(set.getCreatedBy(), false, set.isOwnerLocked(), true);
        }
        return requireOverride(actorId, action, overrideReason, set.getCreatedBy(),
                set.isOwnerLocked(), PracticeAuthoringCollaboration.TARGET_SET, setId);
    }

    @Transactional
    public Decision requireDraftOwnerOrOverride(Long draftId, Long actorId,
                                                PracticeAction action,
                                                String overrideReason) {
        requireGlobal(actorId, action);
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
        if (actorId.equals(draft.getOwnerId())) {
            return new Decision(draft.getOwnerId(), false, draft.isOwnerLocked(), true);
        }
        return requireOverride(actorId, action, overrideReason, draft.getOwnerId(),
                draft.isOwnerLocked(), PracticeAuthoringCollaboration.TARGET_DRAFT, draftId);
    }

    private Decision requireOverride(Long actorId, PracticeAction action, String reason,
                                     Long ownerId, boolean locked,
                                     String targetType, Long targetId) {
        if (!hasPermission(actorId, PracticeAction.EMERGENCY_OVERRIDE)) {
            throw denied(action);
        }
        if (reason == null || reason.isBlank()) {
            throw new AccessDeniedException("Override khẩn cấp phải có lý do.");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 500) {
            throw new AccessDeniedException(
                    "Lý do override không được vượt quá 500 ký tự.");
        }
        if (auditService != null) {
            auditService.record("EMERGENCY_OVERRIDE_AUTHORIZED", targetType, targetId,
                    ownerId, actorId, null, true, normalizedReason, null,
                    "{\"action\":\"" + action.name() + "\"}");
        }
        return new Decision(ownerId, true, locked, false);
    }

    private static boolean allows(PracticeAuthoringCollaboration collaboration,
                                  PracticeAction action) {
        return switch (action) {
            case READ -> true;
            case EDIT -> collaboration.isCanEdit();
            case PUBLISH -> collaboration.isCanPublish();
            case RESTORE -> collaboration.isCanRestore();
            case MATERIAL_MANAGE, MEDIA_REVIEW -> collaboration.isCanManageMaterial();
            default -> false;
        };
    }

    private static boolean canReadLocked(PracticeAction action) {
        return action == PracticeAction.READ || action == PracticeAction.MEDIA_REVIEW;
    }

    private static AccessDeniedException denied(PracticeAction action) {
        return new AccessDeniedException(
                "Bạn không có quyền thực hiện thao tác " + action.permissionKey() + ".");
    }

    public record Decision(Long ownerId, boolean overrideUsed, boolean ownerLocked,
                           boolean ownerOrCollaborator) {
    }
}
