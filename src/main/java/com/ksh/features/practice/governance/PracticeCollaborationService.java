package com.ksh.features.practice.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PracticeCollaborationService {

    private final PracticeAuthoringCollaborationRepository repository;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeGovernanceAuditService auditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PracticeCollaborationService(PracticeAuthoringCollaborationRepository repository,
                                        PracticeAuthorizationService authorizationService,
                                        PracticeGovernanceAuditService auditService,
                                        UserRepository userRepository,
                                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PracticeAuthoringCollaboration shareSet(Long setId, Long collaboratorId,
                                                   Grants grants, Long actorId,
                                                   String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireSetOwnerOrOverride(setId, actorId, PracticeAction.EDIT, overrideReason);
        return grant(PracticeAuthoringCollaboration.TARGET_SET, setId, decision,
                collaboratorId, grants, actorId, overrideReason);
    }

    @Transactional
    public PracticeAuthoringCollaboration shareSetByEmail(Long setId, String email,
                                                          Grants grants, Long actorId,
                                                          String overrideReason) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cộng tác viên là bắt buộc.");
        }
        User collaborator = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy tài khoản theo email đã nhập."));
        return shareSet(setId, collaborator.getId(), grants, actorId, overrideReason);
    }

    @Transactional
    public PracticeAuthoringCollaboration shareDraft(Long draftId, Long collaboratorId,
                                                     Grants grants, Long actorId,
                                                     String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireDraftOwnerOrOverride(draftId, actorId, PracticeAction.EDIT, overrideReason);
        return grant(PracticeAuthoringCollaboration.TARGET_DRAFT, draftId, decision,
                collaboratorId, grants, actorId, overrideReason);
    }

    @Transactional
    public void revokeSet(Long setId, Long collaboratorId, Long actorId,
                          String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireSetOwnerOrOverride(setId, actorId, PracticeAction.EDIT, overrideReason);
        revoke(PracticeAuthoringCollaboration.TARGET_SET, setId, collaboratorId,
                decision, actorId, overrideReason);
    }

    @Transactional
    public void revokeDraft(Long draftId, Long collaboratorId, Long actorId,
                            String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireDraftOwnerOrOverride(draftId, actorId, PracticeAction.EDIT, overrideReason);
        revoke(PracticeAuthoringCollaboration.TARGET_DRAFT, draftId, collaboratorId,
                decision, actorId, overrideReason);
    }

    @Transactional(readOnly = true)
    public List<PracticeAuthoringCollaboration> listSet(Long setId, Long actorId,
                                                       String overrideReason) {
        authorizationService.requireSetOwnerOrOverride(
                setId, actorId, PracticeAction.READ, overrideReason);
        return repository.findByTargetTypeAndTargetIdAndRevokedAtIsNull(
                PracticeAuthoringCollaboration.TARGET_SET, setId);
    }

    @Transactional(readOnly = true)
    public List<PracticeAuthoringCollaboration> sharedWith(Long actorId) {
        authorizationService.requireGlobal(actorId, PracticeAction.READ);
        return repository.findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(actorId);
    }

    private PracticeAuthoringCollaboration grant(String targetType, Long targetId,
                                                  PracticeAuthorizationService.Decision decision,
                                                  Long collaboratorId, Grants requested,
                                                  Long actorId, String overrideReason) {
        validateCollaborator(decision.ownerId(), collaboratorId);
        Grants grants = requested == null ? Grants.editor() : requested;
        PracticeAuthoringCollaboration collaboration = repository
                .findByTargetTypeAndTargetIdAndCollaboratorId(
                        targetType, targetId, collaboratorId)
                .orElseGet(() -> new PracticeAuthoringCollaboration(
                        targetType, targetId, decision.ownerId(), collaboratorId,
                        grants.edit(), grants.publish(), grants.restore(),
                        grants.material(), actorId));
        collaboration.updateGrants(grants.edit(), grants.publish(), grants.restore(),
                grants.material(), actorId);
        PracticeAuthoringCollaboration saved = repository.save(collaboration);
        auditService.record("COLLABORATOR_GRANTED", targetType, targetId,
                decision.ownerId(), actorId, null, decision.overrideUsed(), overrideReason,
                null, json(grantSnapshot(saved)));
        return saved;
    }

    private void revoke(String targetType, Long targetId, Long collaboratorId,
                        PracticeAuthorizationService.Decision decision, Long actorId,
                        String overrideReason) {
        PracticeAuthoringCollaboration collaboration = repository
                .findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
                        targetType, targetId, collaboratorId)
                .orElseThrow(() -> new EntityNotFoundException("Quyền cộng tác không tồn tại."));
        String before = json(grantSnapshot(collaboration));
        collaboration.revoke();
        repository.save(collaboration);
        auditService.record("COLLABORATOR_REVOKED", targetType, targetId,
                decision.ownerId(), actorId, null, decision.overrideUsed(), overrideReason,
                before, null);
    }

    private void validateCollaborator(Long ownerId, Long collaboratorId) {
        if (collaboratorId == null || collaboratorId.equals(ownerId)) {
            throw new IllegalArgumentException("Cộng tác viên phải khác chủ sở hữu.");
        }
        User user = userRepository.findById(collaboratorId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản cộng tác viên không tồn tại."));
        if (user.getRole() == Role.STUDENT || !user.isActive() || user.isLocked()) {
            throw new IllegalArgumentException(
                    "Chỉ tài khoản giảng viên/Head/Admin đang hoạt động mới được cộng tác.");
        }
    }

    private Map<String, Object> grantSnapshot(PracticeAuthoringCollaboration value) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("collaboratorId", value.getCollaboratorId());
        snapshot.put("canEdit", value.isCanEdit());
        snapshot.put("canPublish", value.isCanPublish());
        snapshot.put("canRestore", value.isCanRestore());
        snapshot.put("canManageMaterial", value.isCanManageMaterial());
        return snapshot;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể ghi audit cộng tác.", exception);
        }
    }

    public record Grants(boolean edit, boolean publish, boolean restore, boolean material) {
        public static Grants editor() {
            return new Grants(true, true, true, true);
        }
    }
}
