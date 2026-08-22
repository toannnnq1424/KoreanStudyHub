package com.ksh.features.auth.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import com.ksh.features.auth.repository.AccountActivationTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Applies password and recovery-credential changes atomically.
 *
 * <p>Every password replacement invalidates all outstanding reset links for
 * the account in the same transaction. Callers that already own the user-row
 * lock use {@link #replacePassword(User, String)}; the self-service flow uses
 * {@link #changeOwnPassword(Long, String, String)} so verification and mutation
 * happen while that same lock is held.
 */
@Service
public class CredentialRotationService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final AccountActivationTokenRepository activationTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public CredentialRotationService(UserRepository userRepository,
                                     PasswordResetTokenRepository tokenRepository,
                                     AccountActivationTokenRepository activationTokenRepository,
                                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies and changes an authenticated user's password under a row lock.
     *
     * @return the changed account identity, or empty when the current password
     *         does not match
     */
    @Transactional
    public Optional<ChangedCredential> changeOwnPassword(Long userId,
                                                         String currentPassword,
                                                         String newPassword) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return Optional.empty();
        }

        User saved = replacePasswordInCurrentTransaction(user, newPassword);
        return Optional.of(new ChangedCredential(saved.getId(), saved.getEmail()));
    }

    /** Replaces a password inside a caller-owned transaction and user-row lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User replacePassword(User lockedUser, String newPassword) {
        return replacePasswordInCurrentTransaction(lockedUser, newPassword);
    }

    /** Invalidates every unused recovery link inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int invalidateRecoveryTokens(Long userId) {
        return invalidateRecoveryTokensInCurrentTransaction(userId);
    }

    private User replacePasswordInCurrentTransaction(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        User saved = userRepository.save(user);
        invalidateRecoveryTokensInCurrentTransaction(saved.getId());
        activationTokenRepository.invalidateUnusedForUser(saved.getId(), LocalDateTime.now());
        return saved;
    }

    private int invalidateRecoveryTokensInCurrentTransaction(Long userId) {
        return tokenRepository.invalidateUnusedForUser(userId, LocalDateTime.now());
    }

    /** Stable identity data needed after the credential transaction commits. */
    public record ChangedCredential(Long userId, String email) {
    }
}
