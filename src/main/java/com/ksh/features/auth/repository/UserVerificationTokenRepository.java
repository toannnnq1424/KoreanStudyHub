package com.ksh.features.auth.repository;

import com.ksh.entities.UserVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link UserVerificationToken} — the account-activation tokens.
 *
 * <p>Separate from {@code PasswordResetTokenRepository} on purpose: an
 * activation token must never satisfy a password-reset lookup, and vice versa.
 * See {@link UserVerificationToken} for the rationale.
 */
public interface UserVerificationTokenRepository extends JpaRepository<UserVerificationToken, Long> {

    Optional<UserVerificationToken> findByToken(String token);

    /**
     * Returns every still-usable token belonging to one account, so the caller
     * can consume them before issuing a replacement.
     *
     * <p>Deliberately loads entities rather than running a bulk {@code UPDATE}:
     * a bulk statement writes straight to the database and leaves any token
     * already managed in the persistence context reporting its old
     * {@code used_at}, so a caller issuing and then validating within one
     * transaction would still see the superseded token as valid.
     *
     * @param userId the account whose outstanding tokens should be invalidated
     * @return the unused tokens for that account
     */
    List<UserVerificationToken> findByUserIdAndUsedAtIsNull(Long userId);
}