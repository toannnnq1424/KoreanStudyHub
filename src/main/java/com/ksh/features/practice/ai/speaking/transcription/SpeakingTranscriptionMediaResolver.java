package com.ksh.features.practice.ai.speaking.transcription;

import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

@Service
public class SpeakingTranscriptionMediaResolver {
    private final PracticeSpeakingMediaRepository mediaRepository;
    private final SpeakingAudioStorage storage;
    private final SpeakingTranscriptionProperties properties;

    public SpeakingTranscriptionMediaResolver(PracticeSpeakingMediaRepository mediaRepository,
                                              SpeakingAudioStorage storage,
                                              SpeakingTranscriptionProperties properties) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Resolution resolveForOwner(Long userId, Long attemptId, Long questionId) {
        var rows = mediaRepository.findAuthorizedTranscriptionCandidates(
                userId, attemptId, questionId, PracticeSpeakingMediaStatus.READY);
        if (rows.isEmpty()) {
            return Resolution.failure(SpeakingTranscriptionResult.failure(
                    SpeakingEvaluationStatus.AUDIO_MISSING,
                    properties.provider(),
                    properties.model(),
                    properties.language(),
                    SpeakingTranscriptionErrorCategory.AUDIO_MISSING,
                    false));
        }

        var selected = rows.get(0);
        if (selected.getStorageProvider() != PracticeSpeakingStorageProvider.LOCAL) {
            return unavailable(SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE);
        }
        if (!allowedMime(selected.getMimeType())
                || selected.getByteSize() == null
                || selected.getByteSize() <= 0
                || selected.getByteSize() > properties.maxBytes()
                || selected.getDurationMs() == null
                || selected.getDurationMs() <= 0) {
            return unavailable(SpeakingTranscriptionErrorCategory.UNSUPPORTED_MEDIA);
        }
        try {
            if (!storage.exists(selected.getStorageKey())) {
                return unavailable(SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE);
            }
        } catch (RuntimeException ex) {
            return unavailable(SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE);
        }

        return Resolution.request(new SpeakingTranscriptionRequest(
                selected.getMediaId(),
                selected.getAttemptId(),
                selected.getQuestionId(),
                selected.getLockVersion(),
                selected.getMimeType(),
                selected.getByteSize(),
                selected.getDurationMs(),
                properties.language(),
                () -> open(selected.getStorageKey())));
    }

    public SpeakingTranscriptionResult textFallback(String text) {
        String normalized = normalizeText(text);
        return new SpeakingTranscriptionResult(
                SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED,
                com.ksh.features.practice.ai.speaking.SpeakingEvaluationSource.TEXT_FALLBACK,
                properties.provider(),
                properties.model(),
                properties.language(),
                normalized,
                normalized,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    private InputStream open(String storageKey) throws IOException {
        try {
            return storage.open(storageKey);
        } catch (SpeakingAudioValidationException ex) {
            throw new IOException("Speaking audio object is unavailable", ex);
        }
    }

    private Resolution unavailable(SpeakingTranscriptionErrorCategory category) {
        return Resolution.failure(SpeakingTranscriptionResult.failure(
                category == SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE
                        ? SpeakingEvaluationStatus.AUDIO_UNAVAILABLE
                        : SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                properties.provider(),
                properties.model(),
                properties.language(),
                category,
                false));
    }

    private boolean allowedMime(String mimeType) {
        return mimeType != null
                && properties.allowedMimeTypes().contains(mimeType.trim().toLowerCase(Locale.ROOT));
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    public record Resolution(
            Optional<SpeakingTranscriptionRequest> request,
            Optional<SpeakingTranscriptionResult> failure
    ) {
        static Resolution request(SpeakingTranscriptionRequest request) {
            return new Resolution(Optional.of(request), Optional.empty());
        }

        static Resolution failure(SpeakingTranscriptionResult failure) {
            return new Resolution(Optional.empty(), Optional.of(failure));
        }
    }
}
