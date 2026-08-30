package com.ksh.features.admin.users.imports.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reissues activation mail only for a still-pending, non-deleted account. */
@Service
public class ActivationResendService {

    public enum Outcome { QUEUED, NOT_PENDING, NOT_FOUND }

    private final UserRepository userRepository;
    private final ActivationMailComposer activationMailComposer;

    public ActivationResendService(UserRepository userRepository,
                                   ActivationMailComposer activationMailComposer) {
        this.userRepository = userRepository;
        this.activationMailComposer = activationMailComposer;
    }

    @Transactional
    public Outcome resend(Long userId, Long actorId) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) return Outcome.NOT_FOUND;
        if (!user.isPendingActivation() || user.isActive() || user.isLocked()) {
            return Outcome.NOT_PENDING;
        }
        activationMailComposer.issueAndQueue(user, actorId);
        return Outcome.QUEUED;
    }
}
