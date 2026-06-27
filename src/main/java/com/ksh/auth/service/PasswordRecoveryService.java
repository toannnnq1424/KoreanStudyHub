package com.ksh.auth.service;

import com.ksh.auth.entity.PasswordResetToken;
import com.ksh.auth.entity.User;
import com.ksh.auth.repository.PasswordResetTokenRepository;
import com.ksh.auth.repository.UserRepository;
import com.ksh.shared.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Processes password recovery flow — creates single-use tokens, sends emails, resets passwords.
 * Enumeration-safe: always returns neutral results even if the email does not exist.
 *
 * <p>Mail sending is best-effort: {@link MailService#send} returns {@code false}
 * when SMTP is not configured (smtp.host empty in {@code system_settings}) or
 * when sending fails. In both cases, a warning is logged at {@code WARN} level
 * (without the token) and the token is logged separately at {@code DEBUG} level
 * so devs can enable it when testing workflows locally. The token NEVER appears
 * in {@code INFO}/{@code WARN} logs because these levels are usually collected by log
 * aggregators (Graylog, Loki, CloudWatch) — token leak = account takeover.
 */
@Service
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final SecureRandom RNG = new SecureRandom();
    /** 96 random bytes → ~128 base64 URL-safe characters (sufficient randomness for reset token). */
    private static final int TOKEN_BYTES = 96;
    private static final int TOKEN_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final String baseUrl;

    public PasswordRecoveryService(UserRepository userRepository,
                                   PasswordResetTokenRepository tokenRepository,
                                   PasswordEncoder passwordEncoder,
                                   MailService mailService,
                                   @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.baseUrl = baseUrl;
    }

    /**
     * Handles forgot-password request. Returns the same outcome for all cases
     * (whether email exists or not, SMTP failure, etc.) to prevent enumeration.
     */
    @Transactional
    public void requestReset(String email) {
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Forgot-password requested for unknown email: {}", email);
            return; // silent — neutral UX
        }

        User user = userOpt.get();
        String rawToken = generateToken();
        PasswordResetToken entity = new PasswordResetToken(
                user, rawToken, LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));

        tokenRepository.save(entity);

        String link = baseUrl + "/reset-password?token=" + rawToken;
        String body = "Chào " + user.getFullName() + ",\n\n"
                + "Bạn (hoặc ai đó) đã yêu cầu đặt lại mật khẩu cho tài khoản ksh của bạn.\n\n"
                + "Nhấp vào liên kết bên dưới để đặt mật khẩu mới (hết hạn sau " + TOKEN_TTL_HOURS + " giờ):\n"
                + link + "\n\n"
                + "Nếu bạn không yêu cầu điều này, vui lòng bỏ qua email này.\n\n"
                + "— ksh Team";

        boolean sent = mailService.send(user.getEmail(),
                "Đặt lại mật khẩu ksh", body);
        if (!sent) {
            // SMTP not configured or sending failed. Log WARN without the token
            // (this level is usually collected by log aggregators). Token is only logged at
            // DEBUG level so devs can enable it locally without leaking it in production.
            log.warn("Password-reset email NOT sent to {} (SMTP not configured or send failed). "
                    + "Token logged at DEBUG level.", user.getEmail());
            log.debug("Password-reset link for {}: {}", user.getEmail(), link);
        }
    }

    /**
     * Checks if the reset token is valid (exists, unused, and not expired).
     * Returns the user if the token is valid, or {@code null} otherwise.
     *
     * <p>The caller does not need to distinguish the failure reason — the UX flow only needs
     * to know if it's "valid or not" to render the new password entry page or a general error.
     * This is also enumeration-safe (does not leak "used token" vs "non-existent token").
     */
    public User validateToken(String rawToken) {
        var opt = tokenRepository.findByToken(rawToken);
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
     * Resets password and marks the token as used.
     */
    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        var opt = tokenRepository.findByToken(rawToken);
        if (opt.isEmpty()) {
            return false;
        }
        PasswordResetToken token = opt.get();
        if (!token.isValid()) {
            return false;
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
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
}