package com.ksh.features.lessons.service;

import com.ksh.entities.LessonAttachment;
import com.ksh.entities.PublicViewToken;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.repository.PublicViewTokenRepository;
import com.ksh.features.storage.StorageKeys;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Creates and resolves short-lived tokens that grant anonymous
 * view-only access to a lesson attachment. Tokens are consumed by
 * MS Office Online Viewer which requires a public URL to embed files.
 */
@Service
public class PublicViewTokenService {

    private static final int TOKEN_VALIDITY_HOURS = 1;
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final Pattern LEGACY_RAW_TOKEN = Pattern.compile("[0-9a-f]{32}");

    private final PublicViewTokenRepository tokenRepository;
    private final LessonAttachmentRepository attachmentRepository;
    private final String appBaseUrl;

    public PublicViewTokenService(PublicViewTokenRepository tokenRepository,
                                   LessonAttachmentRepository attachmentRepository,
                                   @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.attachmentRepository = attachmentRepository;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Creates a fresh replacement bearer credential and returns the absolute
     * public URL that MS Office Viewer should embed. Only a SHA-256 digest is
     * persisted; any older live URL for the same attachment is revoked.
     * The token expires after {@value #TOKEN_VALIDITY_HOURS} hour(s).
     */
    @Transactional
    public String createPublicViewUrl(Long attachmentId) {
        attachmentRepository.findByIdForUpdate(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found"));
        List<PublicViewToken> live =
                tokenRepository.findLiveTokensByAttachmentId(attachmentId, LocalDateTime.now());
        if (!live.isEmpty()) {
            tokenRepository.deleteAll(live);
        }

        String rawToken = newRawToken();
        PublicViewToken created = PublicViewToken.createWithDigest(
                attachmentId, hashToken(rawToken), TOKEN_VALIDITY_HOURS);
        tokenRepository.save(created);
        return appBaseUrl + "/public/view/" + rawToken;
    }

    /**
     * Resolves a token to the attachment's storage key handle.
     *
     * @throws EntityNotFoundException if the token is invalid or expired
     */
    @Transactional(noRollbackFor = EntityNotFoundException.class)
    public AttachmentHandle resolve(String tokenValue) {
        String raw = tokenValue == null ? "" : tokenValue.trim();
        if (raw.isEmpty()) {
            throw new EntityNotFoundException("Invalid token");
        }
        // New rows store only the digest. The raw fallback keeps already-issued
        // pre-hardening URLs valid until their normal one-hour expiry.
        Optional<PublicViewToken> found = tokenRepository.findByToken(hashToken(raw));
        if (found.isEmpty() && LEGACY_RAW_TOKEN.matcher(raw).matches()) {
            found = tokenRepository.findByToken(raw);
        }
        PublicViewToken tok = found
                .orElseThrow(() -> new EntityNotFoundException("Invalid token"));
        if (tok.isExpired()) {
            tokenRepository.delete(tok);
            throw new EntityNotFoundException("Token expired");
        }
        LessonAttachment att = attachmentRepository.findById(tok.getAttachmentId())
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found"));
        String key = StorageKeys.requireSafeKey(att.getStoredPath());
        return new AttachmentHandle(key, att.getOriginalFilename(),
                att.getMimeType(), att.getSizeBytes());
    }

    /** Deletes expired tokens. Called by the scheduled cleanup task. */
    @Transactional
    public int cleanupExpired() {
        return tokenRepository.deleteExpired(LocalDateTime.now());
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** Tuple returned by {@link #resolve} so the controller can stream the file. */
    public record AttachmentHandle(String storageKey, String originalFilename,
                                    String mimeType, long sizeBytes) {
    }
}
