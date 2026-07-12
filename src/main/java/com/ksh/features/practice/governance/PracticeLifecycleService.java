package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeLifecycleService {

    private final PracticeAuthorizationService authorizationService;
    private final PracticeGovernanceAuditService auditService;
    private final PracticeSetRepository setRepository;
    private final PracticeDraftRepository draftRepository;

    public PracticeLifecycleService(PracticeAuthorizationService authorizationService,
                                    PracticeGovernanceAuditService auditService,
                                    PracticeSetRepository setRepository,
                                    PracticeDraftRepository draftRepository) {
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.setRepository = setRepository;
        this.draftRepository = draftRepository;
    }

    @Transactional
    public void lockSet(Long setId, Long actorId, String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireSetOwnerOrOverride(setId, actorId, PracticeAction.LOCK, overrideReason);
        PracticeSet set = requireSet(setId);
        set.lock(actorId);
        setRepository.save(set);
        audit("OWNER_LOCKED", "SET", setId, decision, actorId, overrideReason,
                "{\"ownerLocked\":false}", "{\"ownerLocked\":true}");
    }

    @Transactional
    public void unlockSet(Long setId, Long actorId, String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireSetOwnerOrOverride(setId, actorId, PracticeAction.LOCK, overrideReason);
        PracticeSet set = requireSet(setId);
        set.unlock();
        setRepository.save(set);
        audit("OWNER_UNLOCKED", "SET", setId, decision, actorId, overrideReason,
                "{\"ownerLocked\":true}", "{\"ownerLocked\":false}");
    }

    @Transactional
    public void lockDraft(Long draftId, Long actorId, String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireDraftOwnerOrOverride(draftId, actorId, PracticeAction.LOCK, overrideReason);
        PracticeDraft draft = requireDraft(draftId);
        draft.lock(actorId);
        draftRepository.save(draft);
        audit("OWNER_LOCKED", "DRAFT", draftId, decision, actorId, overrideReason,
                "{\"ownerLocked\":false}", "{\"ownerLocked\":true}");
    }

    @Transactional
    public void unlockDraft(Long draftId, Long actorId, String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireDraftOwnerOrOverride(draftId, actorId, PracticeAction.LOCK, overrideReason);
        PracticeDraft draft = requireDraft(draftId);
        draft.unlock();
        draftRepository.save(draft);
        audit("OWNER_UNLOCKED", "DRAFT", draftId, decision, actorId, overrideReason,
                "{\"ownerLocked\":true}", "{\"ownerLocked\":false}");
    }

    @Transactional
    public void archiveSet(Long setId, Long actorId, String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireSetOwnerOrOverride(setId, actorId, PracticeAction.ARCHIVE, overrideReason);
        PracticeSet set = requireSet(setId);
        String before = set.getStatus();
        set.archive();
        setRepository.save(set);
        audit("SET_ARCHIVED", "SET", setId, decision, actorId, overrideReason,
                status(before), status(set.getStatus()));
    }

    @Transactional
    public void unarchiveSet(Long setId, Long actorId, String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService
                .requireSetOwnerOrOverride(setId, actorId, PracticeAction.ARCHIVE, overrideReason);
        PracticeSet set = requireSet(setId);
        if (!PracticeSet.STATUS_ARCHIVED.equals(set.getStatus())) {
            throw new IllegalStateException("Chỉ học liệu đã archive mới có thể khôi phục trạng thái.");
        }
        set.restoreFromArchive();
        setRepository.save(set);
        audit("SET_UNARCHIVED", "SET", setId, decision, actorId, overrideReason,
                status(PracticeSet.STATUS_ARCHIVED), status(set.getStatus()));
    }

    private PracticeSet requireSet(Long setId) {
        return setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
    }

    private PracticeDraft requireDraft(Long draftId) {
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
    }

    private void audit(String action, String targetType, Long targetId,
                       PracticeAuthorizationService.Decision decision, Long actorId,
                       String reason, String before, String after) {
        auditService.record(action, targetType, targetId, decision.ownerId(), actorId,
                null, decision.overrideUsed(), reason, before, after);
    }

    private static String status(String value) {
        return "{\"status\":\"" + value + "\"}";
    }
}
