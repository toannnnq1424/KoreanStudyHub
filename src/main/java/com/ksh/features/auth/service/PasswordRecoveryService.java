package com.ksh.features.auth.service;

import com.ksh.entities.PasswordResetToken;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Handles the forgot-password flow: generates a single-use reset token, sends the
 * reset email, and applies the new password once the token is validated.
 *
 * <p>Enumeration-safe: always returns a neutral result regardless of whether the
 * requested email address exists in the system.
 *
 * <p>Mail sending is best-effort: {@link MailService#send} returns {@code false}
 * when SMTP is not configured (i.e. {@code smtp.host} is empty in
 * {@code system_settings}) or when delivery fails. In both cases a warning is
 * logged without the recipient or reset link. Raw tokens are sent only through
 * the requested email and only their SHA-256 digests are persisted. Typical
 * application logs therefore contain no reusable password-reset credential.
 */
@Service
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final SecureRandom RNG = new SecureRandom();
    /** 96 random bytes â†’ ~128 URL-safe Base64 characters; sufficient entropy for a password-reset token. */
    private static final int TOKEN_BYTES = 96;
    private static final int TOKEN_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final PasswordResetRequestThrottle requestThrottle;
    private final String baseUrl;

    public PasswordRecoveryService(UserRepository userRepository,
                                   PasswordResetTokenRepository tokenRepository,
                                   PasswordEncoder passwordEncoder,
                                   MailService mailService,
                                   PasswordResetRequestThrottle requestThrottle,
                                   @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.requestThrottle = requestThrottle;
        this.baseUrl = baseUrl;
    }

    /**
     * Initiates a password-reset request for the given email address.
     *
     * <p>If the email is not found the method returns silently, giving no
     * indication to the caller that the address is absent (enumeration-safe).
     * When SMTP is unavailable or the send fails, neither the recipient nor the
     * bearer-token reset link is written to application logs.
     *
     * @param email the email address of the account whose password should be reset
     */
    @Transactional
    public void requestReset(String email, String clientIp) {
        if (!requestThrottle.allow(email, clientIp)) {
            return;
        }
        var userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            return; // silent — neutral response to avoid user enumeration
        }

        User user = userOpt.get();
        tokenRepository.invalidateUnusedForUser(user.getId(), LocalDateTime.now());
        String rawToken = generateToken();
        PasswordResetToken entity = new PasswordResetToken(
                user, digestToken(rawToken), LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));

        tokenRepository.save(entity);

        String link = baseUrl + "/reset-password?token=" + rawToken;
        String body = "Hi " + user.getFullName() + ",\n\n"
                + "You (or someone else) requested a password reset for your KSH account.\n\n"
                + "Click the link below to set a new password (expires in " + TOKEN_TTL_HOURS + " hour(s)):\n"
                + link + "\n\n"
                + "If you did not request this, you can safely ignore this email.\n\n"
                + "— KSH Team";

        boolean sent = mailService.send(user.getEmail(),
                "KSH Password Reset", body);
        if (!sent) {
            // Keep both the recipient and bearer-token reset link out of logs.
            log.warn("Password-reset email was not sent (SMTP unavailable or delivery failed)");
        }
    }

    /**
     * Validates a password-reset token.
     *
     * <p>Returns the associated {@link User} when the token exists, has not been
     * used, and has not expired. Returns {@code null} in all failure cases.
     *
     * <p>The caller does not need to distinguish the reason for failure — the UX
     * only needs to know "valid or not" to render either the new-password form or
     * a generic error page. This is also enumeration-safe: the response does not
     * reveal whether a token was already used versus never existed.
     *
     * @param rawToken the plain-text reset token from the email link
     * @return the {@link User} that owns the token, or {@code null} if the token
     *         is invalid, expired, or already used
     */
    public User validateToken(String rawToken) {
        var opt = findToken(rawToken);
        if (opt.isEmpty()) {
            return null;
        }
        PasswordResetToken token = opt.get();
        if (!token.isValid()) {
            return null;
        }
        return token.getUser();
    }

    /**
     * Resets the user's password and marks the token as consumed.
     *
     * <p>The token must exist, be unused, and not have expired. If any of those
     * checks fail this method returns {@code false} and leaves the account
     * unchanged.
     *
     * @param rawToken    the plain-text reset token from the email link
     * @param newPassword the desired new password in plain text (will be BCrypt-encoded)
     * @return {@code true} if the password was updated successfully;
     *         {@code false} if the token is invalid, expired, or already used
     */
    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        var opt = findTokenForUpdate(rawToken);
        if (opt.isEmpty()) {
            return false;
        }
        PasswordResetToken token = opt.get();
        if (!token.isValid()) {
            return false;
        }

        User user = token.getUser();
        // Stamps activated_at alongside the hash — the owner now knows a working
        // password, so the account must not read as awaiting activation.
        user.setKnownPassword(passwordEncoder.encode(newPassword), LocalDateTime.now());
        userRepository.save(user);

        token.markUsed();
        tokenRepository.save(token);

        return true;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private java.util.Optional<PasswordResetToken> findToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return java.util.Optional.empty();
        }
        var digested = tokenRepository.findByToken(digestToken(rawToken));
        return digested.isPresent() ? digested : tokenRepository.findByToken(rawToken);
    }

    private java.util.Optional<PasswordResetToken> findTokenForUpdate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return java.util.Optional.empty();
        }
        var digested = tokenRepository.findByTokenForUpdate(digestToken(rawToken));
        return digested.isPresent() ? digested : tokenRepository.findByTokenForUpdate(rawToken);
    }

    static String digestToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
