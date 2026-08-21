package com.ksh.features.auth.service;

import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.entities.UserVerificationToken;
import com.ksh.features.admin.users.service.AdminUsersAuditWriter;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.auth.repository.UserVerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Issues and consumes account-activation tokens.
 *
 * <p>An account created by the admin roster import starts at
 * {@code is_active = 0} with {@code activated_at = NULL} and a password hash
 * nobody knows. Its owner activates it either by opening the emailed link
 * handled here, or by signing in with Google for the first time — the two are
 * parallel paths and whichever happens first completes activation.
 *
 * <p><b>Enumeration-safe.</b> {@link #validateToken} and {@link #activate}
 * report a single generic failure whether the token is unknown, expired, or
 * already consumed, so the endpoint cannot be used to probe for live tokens.
 *
 * <p><b>Tokens are never logged above DEBUG.</b> {@code INFO} and {@code WARN}
 * are typically shipped to log aggregators, and a leaked activation token
 * lets its holder set a password on that account — the same reasoning
 * {@link PasswordRecoveryService} documents for reset tokens.
 */
@Service
public class AccountActivationService {

    private static final Logger log = LoggerFactory.getLogger(AccountActivationService.class);
    private static final SecureRandom RNG = new SecureRandom();
    /** 96 random bytes → 128 URL-safe Base64 characters, matching the reset-token entropy. */
    private static final int TOKEN_BYTES = 96;
    /** Activation lasts far longer than a password reset: a student may not check mail promptly. */
    private static final int TOKEN_TTL_DAYS = 7;

    private final UserRepository userRepository;
    private final UserVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminUsersAuditWriter auditWriter;

    public AccountActivationService(UserRepository userRepository,
                                    UserVerificationTokenRepository tokenRepository,
                                    PasswordEncoder passwordEncoder,
                                    AdminUsersAuditWriter auditWriter) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditWriter = auditWriter;
    }

    /**
     * Issues a fresh activation token for the given account, invalidating every
     * token previously issued to it.
     *
     * <p>At most one activation token per account is ever usable, so an admin
     * re-send makes the link from the earlier email stop working.
     *
     * @param user the account whose owner must activate it
     * @return the raw token string, to be embedded in the activation link
     */
    @Transactional
    public String issueToken(User user) {
        // Consume through the entities so the persistence context agrees with
        // the database; see the repository method's Javadoc.
        List<UserVerificationToken> outstanding =
                tokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
        outstanding.forEach(UserVerificationToken::markUsed);
        tokenRepository.saveAll(outstanding);

        String rawToken = generateToken();
        tokenRepository.save(new UserVerificationToken(
                user, rawToken, LocalDateTime.now().plusDays(TOKEN_TTL_DAYS)));

        log.debug("Issued activation token for user {}", user.getId());
        return rawToken;
    }

    /** Days an activation token stays usable; surfaced so callers can word the email. */
    public int tokenTtlDays() {
        return TOKEN_TTL_DAYS;
    }

    /**
     * Validates an activation token without consuming it.
     *
     * <p>Returns the owning account when the token exists, is unused, and has
     * not expired; {@code null} in every failure case, deliberately without
     * distinguishing them.
     *
     * @param rawToken the plain-text token from the activation link
     * @return the account to activate, or {@code null} when the token is unusable
     */
    public User validateToken(String rawToken) {
        return tokenRepository.findByToken(rawToken)
                .filter(UserVerificationToken::isValid)
                .map(UserVerificationToken::getUser)
                .orElse(null);
    }

    /**
     * Completes activation: sets the chosen password, enables the account,
     * stamps {@code activated_at}, consumes the token, and records a
     * {@code SELF_ACTIVATED} audit entry — all in one transaction, so a failure
     * leaves the account, the token, and the audit log untouched.
     *
     * <p>{@code activated_at} is only stamped when still {@code NULL}: an
     * account that already activated through Google keeps its original moment
     * (see {@link com.ksh.entities.User#markActivated}).
     *
     * @param rawToken    the plain-text token from the activation link
     * @param newPassword the owner's chosen password, encoded here
     * @return {@code true} on success; {@code false} when the token is unknown,
     *         expired, or already consumed
     */
    @Transactional
    public boolean activate(String rawToken, String newPassword) {
        var opt = tokenRepository.findByToken(rawToken);
        if (opt.isEmpty()) {
            return false;
        }
        UserVerificationToken token = opt.get();
        if (!token.isValid()) {
            return false;
        }

        User user = token.getUser();
        // setKnownPassword stamps activated_at; markActivated additionally flips
        // is_active, which activation — unlike a password reset — must do.
        user.setKnownPassword(passwordEncoder.encode(newPassword), LocalDateTime.now());
        user.markActivated(LocalDateTime.now());
        userRepository.save(user);

        token.markUsed();
        tokenRepository.save(token);

        // Actor is the account owner itself — no administrator is involved.
        // Metadata stays null: the only details here are the chosen password
        // and the token, neither of which may reach the audit log.
        auditWriter.write(user.getId(), UserActivity.TYPE_SELF_ACTIVATED,
                "Chủ tài khoản tự kích hoạt " + user.getEmail(), null, user.getId());

        return true;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}