package com.ksh.features.auth.service;

import com.ksh.entities.AccountActivationToken;
import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.features.admin.users.service.AdminUsersAuditWriter;
import com.ksh.features.auth.repository.AccountActivationTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Issues and consumes single-use email activation links for imported users. */
@Service
public class AccountActivationService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 48;
    private static final int TOKEN_TTL_DAYS = 7;

    private final UserRepository userRepository;
    private final AccountActivationTokenRepository tokenRepository;
    private final CredentialRotationService credentialRotationService;
    private final AdminUsersAuditWriter auditWriter;
    private final Clock clock;

    @Autowired
    public AccountActivationService(UserRepository userRepository,
                                    AccountActivationTokenRepository tokenRepository,
                                    CredentialRotationService credentialRotationService,
                                    AdminUsersAuditWriter auditWriter) {
        this(userRepository, tokenRepository, credentialRotationService,
                auditWriter, Clock.systemUTC());
    }

    AccountActivationService(UserRepository userRepository,
                             AccountActivationTokenRepository tokenRepository,
                             CredentialRotationService credentialRotationService,
                             AdminUsersAuditWriter auditWriter,
                             Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.credentialRotationService = credentialRotationService;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    /** Replaces every older activation link inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issueToken(User user) {
        Objects.requireNonNull(user, "user");
        if (user.getId() == null || !user.isPendingActivation()
                || user.isActive() || user.isLocked() || user.isDeleted()) {
            throw new IllegalStateException("Tài khoản không ở trạng thái chờ kích hoạt.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        tokenRepository.invalidateUnusedForUser(user.getId(), now);
        String rawToken = generateToken();
        tokenRepository.save(new AccountActivationToken(
                user, digestToken(rawToken), now.plusDays(TOKEN_TTL_DAYS)));
        return rawToken;
    }

    public int tokenTtlDays() {
        return TOKEN_TTL_DAYS;
    }

    @Transactional(readOnly = true)
    public User validateToken(String rawToken) {
        String digest = digestOrNull(rawToken);
        if (digest == null) return null;
        LocalDateTime now = LocalDateTime.now(clock);
        Long ownerId = tokenRepository.findUserIdByTokenDigest(digest).orElse(null);
        if (ownerId == null) return null;
        AccountActivationToken token = tokenRepository.findByTokenDigest(digest).orElse(null);
        if (token == null || !Objects.equals(ownerId, token.getUser().getId())
                || !token.isValidAt(now)) return null;
        User user = userRepository.findById(ownerId).orElse(null);
        return isActivatable(user) ? user : null;
    }

    /** Sets the owner's password, verifies the email, enables the user and consumes all links. */
    @Transactional
    public boolean activate(String rawToken, String newPassword) {
        String digest = digestOrNull(rawToken);
        if (digest == null || newPassword == null || newPassword.isBlank()) return false;

        Long ownerId = tokenRepository.findUserIdByTokenDigest(digest).orElse(null);
        if (ownerId == null) return false;
        User user = userRepository.findByIdForUpdate(ownerId).orElse(null);
        if (!isActivatable(user)) return false;

        AccountActivationToken token = tokenRepository
                .findByTokenDigestForUpdate(digest).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (token == null || !Objects.equals(ownerId, token.getUser().getId())
                || !token.isValidAt(now)) {
            return false;
        }

        User saved = credentialRotationService.replacePassword(user, newPassword);
        saved.markActivated(now);
        userRepository.save(saved);
        auditWriter.write(saved.getId(), UserActivity.TYPE_SELF_ACTIVATED,
                "Chủ tài khoản hoàn tất kích hoạt qua email", null, saved.getId());
        return true;
    }

    private static boolean isActivatable(User user) {
        return user != null && user.isPendingActivation() && !user.isActive()
                && !user.isLocked() && !user.isDeleted();
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private static String digestOrNull(String rawToken) {
        return rawToken == null || rawToken.isBlank() ? null : digestToken(rawToken);
    }
}
