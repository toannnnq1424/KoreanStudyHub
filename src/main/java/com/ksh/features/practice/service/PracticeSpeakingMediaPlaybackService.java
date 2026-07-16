package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.service.audio.SpeakingAudioProperties;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.util.Set;

@Service
public class PracticeSpeakingMediaPlaybackService {
    private static final Set<String> PLAYABLE_ATTEMPT_STATUSES = Set.of(
            PracticeAttempt.STATUS_IN_PROGRESS,
            PracticeAttempt.STATUS_SUBMITTED,
            PracticeAttempt.STATUS_GRADED);
    private static final Set<String> PLAYABLE_MIME_TYPES = Set.of("audio/webm", "audio/mp4");

    private final PracticeSpeakingMediaRepository mediaRepository;
    private final SpeakingAudioStorage storage;
    private final SpeakingAudioProperties properties;

    public PracticeSpeakingMediaPlaybackService(PracticeSpeakingMediaRepository mediaRepository,
                                                SpeakingAudioStorage storage,
                                                SpeakingAudioProperties properties) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.properties = properties;
    }

    public PlaybackStream openForOwner(Long userId, Long attemptId, Long questionId, Long mediaId) {
        PlaybackDescriptor descriptor = mediaRepository.findAuthorizedPlayback(
                        userId,
                        attemptId,
                        questionId,
                        mediaId,
                        PracticeSpeakingMediaStatus.READY,
                        PLAYABLE_ATTEMPT_STATUSES)
                .map(PlaybackDescriptor::from)
                .orElseThrow(PracticeSpeakingMediaPlaybackNotFoundException::new);
        descriptor.validate(properties.getMaxAudioBytes());
        if (descriptor.storageProvider() != PracticeSpeakingStorageProvider.LOCAL) {
            throw new PracticeSpeakingMediaPlaybackNotFoundException();
        }
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Playback storage open must not run in a transaction.");
            }
            InputStream input = storage.open(descriptor.storageKey());
            return new PlaybackStream(descriptor.mimeType(), descriptor.byteSize(), input);
        } catch (SpeakingAudioValidationException ex) {
            throw new PracticeSpeakingMediaPlaybackNotFoundException();
        }
    }

    private record PlaybackDescriptor(
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            String mimeType,
            long byteSize
    ) {
        private static PlaybackDescriptor from(
                PracticeSpeakingMediaRepository.PlaybackAuthorizationProjection projection) {
            return new PlaybackDescriptor(
                    projection.getStorageProvider(),
                    projection.getStorageKey(),
                    projection.getMimeType(),
                    projection.getByteSize() == null ? -1L : projection.getByteSize());
        }

        private void validate(long maxAudioBytes) {
            if (storageProvider == null || storageKey == null || storageKey.isBlank()) {
                throw new PracticeSpeakingMediaPlaybackNotFoundException();
            }
            if (byteSize <= 0L || byteSize > maxAudioBytes) {
                throw new PracticeSpeakingMediaPlaybackNotFoundException();
            }
            if (mimeType == null || mimeType.isBlank()) {
                throw new PracticeSpeakingMediaPlaybackNotFoundException();
            }
            MediaType parsed;
            try {
                parsed = MediaType.parseMediaType(mimeType);
            } catch (IllegalArgumentException ex) {
                throw new PracticeSpeakingMediaPlaybackNotFoundException();
            }
            if (!PLAYABLE_MIME_TYPES.contains(parsed.toString())) {
                throw new PracticeSpeakingMediaPlaybackNotFoundException();
            }
        }

        @Override
        public String toString() {
            return "PlaybackDescriptor{mimeType='" + mimeType + "', byteSize=" + byteSize + "}";
        }
    }

    public record PlaybackStream(String mimeType, long byteSize, InputStream inputStream) {
        @Override
        public String toString() {
            return "PlaybackStream{mimeType='" + mimeType + "', byteSize=" + byteSize + "}";
        }
    }
}
