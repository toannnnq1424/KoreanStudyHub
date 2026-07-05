package com.ksh.features.practice.service.audio;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Service
public class SpeakingAudioPreparationService {
    private static final Set<String> WEBM_MIME_ALIASES = Set.of("audio/webm", "video/webm");
    private static final Set<String> MP4_MIME_ALIASES = Set.of("audio/mp4", "audio/m4a", "audio/x-m4a", "video/mp4");

    private final SpeakingAudioStorage storage;
    private final SpeakingAudioInspector inspector;

    public SpeakingAudioPreparationService(SpeakingAudioStorage storage, SpeakingAudioInspector inspector) {
        this.storage = storage;
        this.inspector = inspector;
    }

    /**
     * Produces a private final object. The caller must either activate DB metadata
     * with this result or delete the returned storage key as compensation.
     */
    public PreparedSpeakingAudio prepare(InputStream content, Long declaredContentLength, String clientMimeType) {
        StoredSpeakingAudioObject temporary = storage.writeTemporary(content, declaredContentLength);
        try {
            SpeakingAudioInspection inspection = inspector.inspect(temporary.getPrivatePath());
            validateMimeHint(clientMimeType, inspection.canonicalMimeType());
            String finalKey = storage.promoteTemporary(temporary.getStorageKey());
            return new PreparedSpeakingAudio(
                    PracticeSpeakingStorageProvider.LOCAL,
                    finalKey,
                    inspection.canonicalMimeType(),
                    inspection.container(),
                    inspection.codec(),
                    temporary.getByteSize(),
                    inspection.durationMs(),
                    temporary.getSha256()
            );
        } catch (RuntimeException ex) {
            storage.delete(temporary.getStorageKey());
            throw ex;
        }
    }

    private void validateMimeHint(String clientMimeType, String canonicalMimeType) {
        if (clientMimeType == null || clientMimeType.isBlank()) {
            return;
        }
        String normalized = clientMimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("application/octet-stream".equals(normalized)) {
            return;
        }
        Set<String> allowedAliases = switch (canonicalMimeType) {
            case "audio/webm" -> WEBM_MIME_ALIASES;
            case "audio/mp4" -> MP4_MIME_ALIASES;
            default -> Set.of(canonicalMimeType);
        };
        if (!allowedAliases.contains(normalized)) {
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.UNSUPPORTED_TYPE,
                    "Audio MIME hint does not match validated media"
            );
        }
    }
}
