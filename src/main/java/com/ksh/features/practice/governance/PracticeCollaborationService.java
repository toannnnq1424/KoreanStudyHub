package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PracticeCollaborationService {

    private final PracticeAuthoringCollaborationRepository repository;
    private final PracticeAuthorizationService authorizationService;
    private final UserRepository userRepository;

    public PracticeCollaborationService(PracticeAuthoringCollaborationRepository repository,
                                        PracticeAuthorizationService authorizationService,
                                        UserRepository userRepository) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public PracticeAuthoringCollaboration shareSet(Long setId, Long collaboratorId,
                                                   Long actorId) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireSetOwner(setId, actorId, PracticeAction.EDIT);
        return grant(setId, decision.ownerId(), collaboratorId);
    }

    @Transactional
    public PracticeAuthoringCollaboration shareSetByEmail(Long setId, String email,
                                                          Long actorId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cộng tác viên là bắt buộc.");
        }
        User collaborator = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy tài khoản theo email đã nhập."));
        return shareSet(setId, collaborator.getId(), actorId);
    }

    @Transactional
    public void revokeSet(Long setId, Long collaboratorId, Long actorId) {
        authorizationService
                .requireSetOwner(setId, actorId, PracticeAction.EDIT);
        revoke(setId, collaboratorId);
    }

    @Transactional(readOnly = true)
    public List<PracticeAuthoringCollaboration> listSet(Long setId, Long actorId) {
        authorizationService.requireSetOwner(setId, actorId, PracticeAction.READ);
        return repository.findBySetIdAndRevokedAtIsNull(setId);
    }

    @Transactional(readOnly = true)
    public List<PracticeAuthoringCollaboration> sharedWith(Long actorId) {
        authorizationService.requireGlobal(actorId, PracticeAction.READ);
        return repository.findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(actorId);
    }

    private PracticeAuthoringCollaboration grant(Long setId, Long ownerId,
                                                  Long collaboratorId) {
        validateCollaborator(ownerId, collaboratorId);
        PracticeAuthoringCollaboration collaboration = repository
                .findBySetIdAndCollaboratorId(setId, collaboratorId)
                .orElseGet(() -> new PracticeAuthoringCollaboration(
                        setId, collaboratorId));
        collaboration.reactivate();
        return repository.save(collaboration);
    }

    private void revoke(Long setId, Long collaboratorId) {
        PracticeAuthoringCollaboration collaboration = repository
                .findBySetIdAndCollaboratorIdAndRevokedAtIsNull(setId, collaboratorId)
                .orElseThrow(() -> new EntityNotFoundException("Quyền cộng tác không tồn tại."));
        collaboration.revoke();
        repository.save(collaboration);
    }

    private void validateCollaborator(Long ownerId, Long collaboratorId) {
        if (collaboratorId == null || collaboratorId.equals(ownerId)) {
            throw new IllegalArgumentException("Cộng tác viên phải khác chủ sở hữu.");
        }
        User user = userRepository.findById(collaboratorId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản cộng tác viên không tồn tại."));
        if (user.getRole() != Role.LECTURER || !user.isActive() || user.isLocked()) {
            throw new IllegalArgumentException(
                    "Chỉ tài khoản giảng viên đang hoạt động mới được cộng tác.");
        }
    }
}
