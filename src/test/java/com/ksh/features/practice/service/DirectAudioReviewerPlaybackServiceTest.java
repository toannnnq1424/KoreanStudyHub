package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.audio.SpeakingAudioProperties;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.StoredSpeakingAudioObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectAudioReviewerPlaybackServiceTest {
    private static final String STORAGE_KEY = "learner-speaking/ready/reviewer-secret.webm";
    private final DirectAudioReviewerPlaybackStore store = mock(DirectAudioReviewerPlaybackStore.class);
    private final DirectAudioReviewerAccessAudit audit = mock(DirectAudioReviewerAccessAudit.class);
    private final RecordingStorage storage = new RecordingStorage();
    private final DirectAudioReviewerPlaybackService service = new DirectAudioReviewerPlaybackService(
            store, storage, properties(), audit,
            Clock.fixed(Instant.parse("2026-08-03T09:00:00Z"), ZoneOffset.UTC));

    @Test
    void exactAuthorizedGrantConsentAndDarkBindingMayOpenPrivateOriginalForRangePlayback() throws Exception {
        when(store.findAuthorized(any(), any(), any(), any(), any())).thenReturn(Optional.of(descriptor()));
        storage.next = new ByteArrayInputStream(new byte[]{4, 5, 6});

        var stream = service.openForReviewer(77L, 10L, 20L, 30L);

        assertThat(stream.mimeType()).isEqualTo("audio/webm");
        assertThat(stream.inputStream().readAllBytes()).containsExactly(4, 5, 6);
        assertThat(stream.toString()).doesNotContain(STORAGE_KEY);
        verify(store).findAuthorized(any(), any(), any(), any(), any());
        verify(audit).recordAuthorized(
                DirectAudioReviewerAccessAudit.Action.PLAYBACK_OPEN,
                77L, 10L, 20L, 30L, "dark-observation-0001");
    }

    @Test
    void missingAnyAuthorizationFactorCollapsesToNotFoundBeforeStorageOpen() {
        when(store.findAuthorized(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openForReviewer(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);

        assertThat(storage.opened).isFalse();
        verify(audit, never()).recordAuthorized(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidIdentityOrDescriptorCannotReachStorage() {
        assertThatThrownBy(() -> service.openForReviewer(0L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);
        when(store.findAuthorized(any(), any(), any(), any(), any())).thenReturn(Optional.of(
                new DirectAudioReviewerPlaybackStore.PlaybackDescriptor(
                        PracticeSpeakingStorageProvider.LOCAL, "PRACTICE_SPEAKING",
                        STORAGE_KEY, "audio/wav", 3L, "dark-observation-0001")));

        assertThatThrownBy(() -> service.openForReviewer(77L, 10L, 20L, 30L))
                .isInstanceOf(PracticeSpeakingMediaPlaybackNotFoundException.class);
        assertThat(storage.opened).isFalse();
    }

    @Test
    void failedDurableAuditPreventsStorageOpen() {
        when(store.findAuthorized(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(descriptor()));
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).recordAuthorized(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.openForReviewer(77L, 10L, 20L, 30L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
        assertThat(storage.opened).isFalse();
    }

    private static DirectAudioReviewerPlaybackStore.PlaybackDescriptor descriptor() {
        return new DirectAudioReviewerPlaybackStore.PlaybackDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "PRACTICE_SPEAKING",
                STORAGE_KEY, "audio/webm", 3L, "dark-observation-0001");
    }

    private static SpeakingAudioProperties properties() {
        return new SpeakingAudioProperties("private", "uploads", "ffprobe", Duration.ofSeconds(1),
                1000, 256, 10L, Duration.ofMinutes(1));
    }

    private static final class RecordingStorage implements SpeakingAudioStorage {
        private InputStream next = InputStream.nullInputStream();
        private boolean opened;

        @Override public StoredSpeakingAudioObject writeTemporary(InputStream content, Long length) {
            throw new AssertionError("outside reviewer playback");
        }
        @Override public String promoteTemporary(String profile, String key) {
            throw new AssertionError("outside reviewer playback");
        }
        @Override public InputStream open(String profile, String key) {
            opened = true;
            assertThat(profile).isEqualTo("PRACTICE_SPEAKING");
            assertThat(key).isEqualTo(STORAGE_KEY);
            return next;
        }
        @Override public boolean exists(String profile, String key) { return true; }
        @Override public void delete(String profile, String key) {
            throw new AssertionError("outside reviewer playback");
        }
    }
}
