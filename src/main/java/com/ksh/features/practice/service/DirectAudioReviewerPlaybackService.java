package com.ksh.features.practice.service;

import com.ksh.features.practice.service.audio.SpeakingAudioProperties;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * Separate branch-B reviewer playback boundary. A named active grant, current
 * consent and an undeleted dark observation must all authorize every open.
 */
@Service
public class DirectAudioReviewerPlaybackService {
    private static final Set<String> PLAYABLE_MIME_TYPES = Set.of("audio/webm", "audio/mp4");

    private final DirectAudioReviewerPlaybackStore store;
    private final SpeakingAudioStorage storage;
    private final SpeakingAudioProperties properties;
    private final Clock clock;

    public DirectAudioReviewerPlaybackService(
            DirectAudioReviewerPlaybackStore store,
            SpeakingAudioStorage storage,
            SpeakingAudioProperties properties) {
        this(store, storage, properties, Clock.systemUTC());
    }

    DirectAudioReviewerPlaybackService(
            DirectAudioReviewerPlaybackStore store,
            SpeakingAudioStorage storage,
            SpeakingAudioProperties properties,
            Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.storage = Objects.requireNonNull(storage);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    public PlaybackStream openForReviewer(
            Long reviewerId, Long attemptId, Long questionId, Long mediaId) {
        requireIdentity(reviewerId);
        requireIdentity(attemptId);
        requireIdentity(questionId);
        requireIdentity(mediaId);
        Descriptor descriptor = store.findAuthorized(
                        reviewerId, attemptId, questionId, mediaId, clock.instant())
                .map(Descriptor::from)
                .orElseThrow(PracticeSpeakingMediaPlaybackNotFoundException::new);
        descriptor.validate(properties.getMaxAudioBytes());
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Reviewer playback storage open must not run in a transaction.");
            }
            return new PlaybackStream(descriptor.mimeType(), descriptor.byteSize(),
                    storage.open(descriptor.storageProfileCode(), descriptor.storageKey()));
        } catch (SpeakingAudioValidationException ex) {
            throw new PracticeSpeakingMediaPlaybackNotFoundException();
        }
    }

    private static void requireIdentity(Long value) {
        if (value == null || value <= 0L) {
            throw new PracticeSpeakingMediaPlaybackNotFoundException();
        }
    }

    private record Descriptor(
            com.ksh.entities.PracticeSpeakingStorageProvider storageProvider,
            String storageProfileCode, String storageKey, String mimeType, long byteSize) {
        private static Descriptor from(DirectAudioReviewerPlaybackStore.PlaybackDescriptor value) {
            return new Descriptor(value.storageProvider(), value.storageProfileCode(),
                    value.storageKey(), value.mimeType(), value.byteSize());
        }

        private void validate(long maxAudioBytes) {
            if (storageProvider == null || storageKey == null || storageKey.isBlank()
                    || !"PRACTICE_SPEAKING".equals(storageProfileCode)
                    || byteSize <= 0L || byteSize > maxAudioBytes) {
                throw new PracticeSpeakingMediaPlaybackNotFoundException();
            }
            try {
                if (mimeType == null || !PLAYABLE_MIME_TYPES.contains(
                        MediaType.parseMediaType(mimeType).toString())) {
                    throw new PracticeSpeakingMediaPlaybackNotFoundException();
                }
            } catch (IllegalArgumentException ex) {
                throw new PracticeSpeakingMediaPlaybackNotFoundException();
            }
        }

        @Override
        public String toString() {
            return "Descriptor{mimeType='" + mimeType + "', byteSize=" + byteSize + "}";
        }
    }

    public record PlaybackStream(String mimeType, long byteSize, InputStream inputStream) {
        @Override
        public String toString() {
            return "PlaybackStream{mimeType='" + mimeType + "', byteSize=" + byteSize + "}";
        }
    }
}
