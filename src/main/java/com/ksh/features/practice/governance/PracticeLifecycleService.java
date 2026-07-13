package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeLifecycleService {

    private final PracticeAuthorizationService authorizationService;
    private final PracticeSetRepository setRepository;

    public PracticeLifecycleService(PracticeAuthorizationService authorizationService,
                                    PracticeSetRepository setRepository) {
        this.authorizationService = authorizationService;
        this.setRepository = setRepository;
    }

    @Transactional
    public void lockSet(Long setId, Long actorId) {
        authorizationService.requireSetOwner(setId, actorId, PracticeAction.LOCK);
        PracticeSet set = requireSet(setId);
        set.lock(actorId);
        setRepository.save(set);
    }

    @Transactional
    public void unlockSet(Long setId, Long actorId) {
        authorizationService.requireSetOwner(setId, actorId, PracticeAction.LOCK);
        PracticeSet set = requireSet(setId);
        set.unlock();
        setRepository.save(set);
    }

    @Transactional
    public void archiveSet(Long setId, Long actorId) {
        authorizationService.requireSetOwner(setId, actorId, PracticeAction.ARCHIVE);
        PracticeSet set = requireSet(setId);
        set.archive();
        setRepository.save(set);
    }

    @Transactional
    public void unarchiveSet(Long setId, Long actorId) {
        authorizationService.requireSetOwner(setId, actorId, PracticeAction.ARCHIVE);
        PracticeSet set = requireSet(setId);
        if (!PracticeSet.STATUS_ARCHIVED.equals(set.getStatus())) {
            throw new IllegalStateException("Chỉ học liệu đã archive mới có thể khôi phục trạng thái.");
        }
        set.restoreFromArchive();
        setRepository.save(set);
    }

    private PracticeSet requireSet(Long setId) {
        return setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
    }

}
