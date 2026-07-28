package com.ksh.features.lessons.service;

import com.ksh.entities.LessonAttachment;
import com.ksh.entities.PublicViewToken;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.repository.PublicViewTokenRepository;
import com.ksh.features.storage.StorageKeys;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Creates and resolves short-lived tokens that grant anonymous
 * view-only access to a lesson attachment. Tokens are consumed by
 * MS Office Online Viewer which requires a public URL to embed files.
 */
@Service
public class PublicViewTokenService {

    private static final Logger log = LoggerFactory.getLogger(PublicViewTokenService.class);
    private static final int TOKEN_VALIDITY_HOURS = 1;

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
     * Creates a token and returns the absolute public URL that MS Office
     * Viewer should embed. The token expires after
     * {@value #TOKEN_VALIDITY_HOURS} hour(s).
     */
    @Transactional
    public String createPublicViewUrl(Long attachmentId) {
        attachmentRepository.findByIdForUpdate(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found"));
        List<PublicViewToken> live =
                tokenRepository.findLiveTokensByAttachmentId(attachmentId, LocalDateTime.now());
        if (!live.isEmpty()) {
            PublicViewToken retained = live.get(0);
            if (live.size() > 1) tokenRepository.deleteAll(live.subList(1, live.size()));
            return appBaseUrl + "/public/view/" + retained.getToken();
        }
        PublicViewToken created = PublicViewToken.create(attachmentId, TOKEN_VALIDITY_HOURS);
        tokenRepository.save(created);
        return appBaseUrl + "/public/view/" + created.getToken();
    }

    /**
     * Resolves a token to the attachment's storage key handle.
     *
     * @throws EntityNotFoundException if the token is invalid or expired
     */
    @Transactional
    public AttachmentHandle resolve(String tokenValue) {
        PublicViewToken tok = tokenRepository.findByToken(tokenValue)
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

    /** Tuple returned by {@link #resolve} so the controller can stream the file. */
    public record AttachmentHandle(String storageKey, String originalFilename,
                                    String mimeType, long sizeBytes) {
    }
}
