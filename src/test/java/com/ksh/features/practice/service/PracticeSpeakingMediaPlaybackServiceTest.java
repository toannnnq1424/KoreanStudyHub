package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.service.audio.SpeakingAudioProperties;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationCategory;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationException;
import com.ksh.features.practice.service.audio.StoredSpeakingAudioObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSpeakingMediaPlaybackServiceTest {
    private static final String SECRET_KEY = "learner-speaking/ready/secret-b3b1";
    private static final String SECRET_HASH = "SECRET_HASH_B3B1";

    private final PracticeSpeakingMediaRepository mediaRepository = mock(PracticeSpeakingMediaRepository.class);
    private final ObservingStorage storage = new ObservingStorage();
    private final PracticeSpeakingMediaPlaybackService service =
            new PracticeSpeakingMediaPlaybackService(mediaRepository, storage, properties());

    @Test
    void readyLocalDescriptorOpensStorageOutsideTransactionAndReturnsSafeStream() throws Exception {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, "audio/webm", 3L));
        storage.next = new ByteArrayInputStream(new byte[]{1, 2, 3});

        PracticeSpeakingMediaPlaybackService.PlaybackStream stream =
                service.openForOwner(77L, 10L, 20L, 30L);

        assertThat(stream.mimeType()).isEqualTo("audio/webm");
        assertThat(stream.byteSize()).isEqualTo(3L);
        assertThat(stream.toString()).doesNotContain(SECRET_KEY).doesNotContain(SECRET_HASH);
        assertThat(storage.openTransactionActive).isFalse();
        assertThat(stream.inputStream().read()).isEqualTo(1);
    }

    @Test
    void missingAuthorizationCollapsesToNotFoundWithoutOpeningStorage() {
        when(mediaRepository.findAuthorizedPlayback(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);

        assertThat(storage.openCalled).isFalse();
    }

    @Test
    void unsupportedProviderIsBoundedAndDoesNotOpenStorage() {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.OBJECT_STORAGE, SECRET_KEY, "audio/webm", 3L));

        assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class)
                .hasMessageNotContaining(SECRET_KEY);

        assertThat(storage.openCalled).isFalse();
    }

    @Test
    void storageFailureIsBoundedAndDoesNotLeakStorageKey() {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, "audio/webm", 3L));
        storage.failure = new SpeakingAudioValidationException(
                SpeakingAudioValidationCategory.STORAGE_FAILURE, SECRET_KEY);

        assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class)
                .hasMessageNotContaining(SECRET_KEY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not a media type", "text/plain", "audio/wav"})
    void invalidMimeIsDeniedBeforeStorageOpen(String mimeType) {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, mimeType, 3L));

        assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);

        assertThat(storage.openCalled).isFalse();
    }

    @Test
    void invalidByteSizeIsDeniedBeforeStorageOpen() {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, "audio/webm", 0L));

        assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);

        assertThat(storage.openCalled).isFalse();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidByteSizes")
    void invalidByteSizesAreDeniedBeforeStorageOpen(Long byteSize) {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, "audio/webm", byteSize));

        assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);

        assertThat(storage.openCalled).isFalse();
    }

    @Test
    void repositoryQueryUsesExactOwnerRouteAndReadyStatusAllowlist() {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, "audio/webm", 3L));
        storage.next = new ByteArrayInputStream(new byte[]{1, 2, 3});

        service.openForOwner(77L, 10L, 20L, 30L);

        verify(mediaRepository).findAuthorizedPlayback(
                eq(77L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(com.ksh.entities.PracticeSpeakingMediaStatus.READY),
                eq(java.util.Set.of("IN_PROGRESS", "SUBMITTED", "GRADED")));
    }

    @Test
    void serviceRefusesStorageOpenIfTransactionIsStillActive() {
        whenAuthorized(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, "audio/webm", 3L));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not run in a transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void alreadyOpenedStreamCanCompleteWhileFutureLookupIsDenied() throws Exception {
        when(mediaRepository.findAuthorizedPlayback(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(projection(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY, "audio/webm", 3L)))
                .thenReturn(Optional.empty());
        storage.next = new ByteArrayInputStream(new byte[]{1, 2, 3});

        PracticeSpeakingMediaPlaybackService.PlaybackStream opened =
                service.openForOwner(77L, 10L, 20L, 30L);

        assertThat(opened.inputStream().readAllBytes()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> service.openForOwner(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);
    }

    private void whenAuthorized(PracticeSpeakingMediaRepository.PlaybackAuthorizationProjection projection) {
        when(mediaRepository.findAuthorizedPlayback(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(projection));
    }

    private static PracticeSpeakingMediaRepository.PlaybackAuthorizationProjection projection(
            PracticeSpeakingStorageProvider provider,
            String key,
            String mimeType,
            Long byteSize) {
        return new PracticeSpeakingMediaRepository.PlaybackAuthorizationProjection() {
            @Override
            public PracticeSpeakingStorageProvider getStorageProvider() {
                return provider;
            }

            @Override
            public String getStorageKey() {
                return key;
            }

            @Override
            public String getMimeType() {
                return mimeType;
            }

            @Override
            public Long getByteSize() {
                return byteSize;
            }
        };
    }

    private static java.util.stream.Stream<Long> invalidByteSizes() {
        return java.util.stream.Stream.of(null, -1L, 2_000_001L);
    }

    private static SpeakingAudioProperties properties() {
        return new SpeakingAudioProperties(
                "private-storage/practice-speaking-audio",
                "uploads",
                "ffprobe",
                Duration.ofSeconds(10),
                262144,
                65536,
                10L,
                Duration.ofMinutes(10));
    }

    private static final class ObservingStorage implements SpeakingAudioStorage {
        private InputStream next = InputStream.nullInputStream();
        private RuntimeException failure;
        private boolean openCalled;
        private Boolean openTransactionActive;

        @Override
        public StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredContentLength) {
            throw new AssertionError("write is outside playback scope");
        }

        @Override
        public String promoteTemporary(String temporaryKey) {
            throw new AssertionError("promotion is outside playback scope");
        }

        @Override
        public InputStream open(String storageKey) {
            openCalled = true;
            openTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            if (failure != null) {
                throw failure;
            }
            return next;
        }

        @Override
        public boolean exists(String storageKey) {
            return true;
        }

        @Override
        public void delete(String storageKey) {
            throw new AssertionError("delete is outside playback scope");
        }
    }
}
