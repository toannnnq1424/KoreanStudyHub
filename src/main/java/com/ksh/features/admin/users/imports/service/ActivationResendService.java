package com.ksh.features.admin.users.imports.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Re-sends the activation email for an account whose owner has not yet
 * activated it.
 *
 * <p>The recovery path when SMTP was misconfigured at import time, or when the
 * 7-day link has lapsed. Issuing a fresh token invalidates any outstanding one,
 * so the link from the earlier email stops working.
 *
 * <p>Refuses to act on any account someone can already sign in to: there is
 * nothing for its owner to activate, and sending a link anyway would let anyone
 * with mailbox access reset that account's password outside the audited reset
 * flow. The test is {@link User#hasNoUsablePassword()} rather than
 * {@link User#isPendingActivation()} — the latter reads {@code activated_at}
 * alone, which is a display status and not a statement about whether the
 * account is usable.
 */
@Service
public class ActivationResendService {

    private final UserRepository userRepository;
    private final ActivationMailComposer activationMailComposer;

    public ActivationResendService(UserRepository userRepository,
                                   ActivationMailComposer activationMailComposer) {
        this.userRepository = userRepository;
        this.activationMailComposer = activationMailComposer;
    }

    /** Distinguishes the outcomes the controller must report differently. */
    public enum Outcome {
        /** A fresh token was issued and its email queued. */
        QUEUED,
        /** The account is already usable, so nothing was issued. */
        ALREADY_ACTIVATED,
        /** No such account (or it is soft-deleted). */
        NOT_FOUND
    }

    /**
     * Issues a fresh activation token for the account and queues its email,
     * recording an {@code ACTIVATION_SENT} audit entry against the account.
     *
     * @param userId  the account to re-send for
     * @param actorId the administrator performing the re-send
     * @return what happened, so the caller can pick the right toast
     */
    @Transactional
    public Outcome resend(Long userId, Long actorId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        User user = found.get();
        // Gates on the capability, not on the status label: an account with a
        // working password must never be issued an activation link, whatever
        // its activated_at happens to say. See User#hasNoUsablePassword.
        if (!user.hasNoUsablePassword()) {
            return Outcome.ALREADY_ACTIVATED;
        }
        activationMailComposer.issueAndQueue(user, actorId);
        return Outcome.QUEUED;
    }
}