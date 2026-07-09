package com.ksh.features.practice.ai.speaking.transcription;

import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationCategory;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SpeakingTranscriptionMediaResolverTest {

    @Test
    void readyMediaIsEligibleAndRequestDoesNotExposeStorageKey() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.LOCAL,
                "learner-speaking/ready/secret.webm", "audio/webm", 100L, 1200L)));
        when(fixture.storage.exists("learner-speaking/ready/secret.webm")).thenReturn(true);
        when(fixture.storage.open("learner-speaking/ready/secret.webm"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.request()).isPresent();
        SpeakingTranscriptionRequest request = resolution.request().orElseThrow();
        assertThat(request.mediaId()).isEqualTo(1L);
        assertThat(request.mimeType()).isEqualTo("audio/webm");
        assertThat(request.toString()).doesNotContain("secret.webm").doesNotContain("learner-speaking");
    }

    @Test
    void supersededOrDeletedMediaIsIgnoredAsNoReadyMedia() {
        Fixture fixture = fixture(List.of());

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.request()).isEmpty();
        assertThat(resolution.failure()).isPresent();
        assertThat(resolution.failure().orElseThrow().status()).isEqualTo(SpeakingEvaluationStatus.AUDIO_MISSING);
        assertThat(resolution.failure().orElseThrow().errorCategory())
                .isEqualTo(SpeakingTranscriptionErrorCategory.AUDIO_MISSING);
        verify(fixture.repository).findAuthorizedTranscriptionCandidates(
                eq(77L), eq(88L), eq(99L), eq(PracticeSpeakingMediaStatus.READY));
        verifyNoMoreInteractions(fixture.storage);
    }

    @Test
    void missingLocalObjectMapsAudioUnavailable() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.LOCAL,
                "learner-speaking/ready/missing.webm", "audio/webm", 100L, 1200L)));
        when(fixture.storage.exists("learner-speaking/ready/missing.webm")).thenReturn(false);

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.failure()).isPresent();
        assertThat(resolution.failure().orElseThrow().status()).isEqualTo(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE);
        assertThat(resolution.failure().orElseThrow().errorCategory())
                .isEqualTo(SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE);
    }

    @Test
    void storageValidationFailureMapsAudioUnavailable() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.LOCAL,
                "learner-speaking/ready/bad.webm", "audio/webm", 100L, 1200L)));
        when(fixture.storage.exists("learner-speaking/ready/bad.webm"))
                .thenThrow(new SpeakingAudioValidationException(
                        SpeakingAudioValidationCategory.STORAGE_FAILURE, "unavailable"));

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.failure().orElseThrow().status()).isEqualTo(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE);
    }

    @Test
    void unsupportedMimeMapsUnsupportedMedia() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.LOCAL,
                "learner-speaking/ready/a.wav", "audio/wav", 100L, 1200L)));

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.failure()).isPresent();
        assertThat(resolution.failure().orElseThrow().status())
                .isEqualTo(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE);
        assertThat(resolution.failure().orElseThrow().errorCategory())
                .isEqualTo(SpeakingTranscriptionErrorCategory.UNSUPPORTED_MEDIA);
    }

    @Test
    void oversizeMapsUnsupportedMedia() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.LOCAL,
                "learner-speaking/ready/large.webm", "audio/webm", 26214401L, 1200L)));

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.failure().orElseThrow().errorCategory())
                .isEqualTo(SpeakingTranscriptionErrorCategory.UNSUPPORTED_MEDIA);
    }

    @Test
    void objectStorageDeferredIsUnavailableForMvp() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.OBJECT_STORAGE,
                "object-key", "audio/webm", 100L, 1200L)));

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.failure().orElseThrow().status()).isEqualTo(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE);
        assertThat(resolution.failure().orElseThrow().toString()).doesNotContain("object-key");
    }

    @Test
    void uploadAndPlaybackGatesDoNotControlTranscriptionEligibility() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.LOCAL,
                "learner-speaking/ready/gates.webm", "audio/mp4", 100L, 1200L)));
        when(fixture.storage.exists("learner-speaking/ready/gates.webm")).thenReturn(true);

        var resolution = fixture.resolver.resolveForOwner(77L, 88L, 99L);

        assertThat(resolution.request()).isPresent();
        assertThat(resolution.request().orElseThrow().mimeType()).isEqualTo("audio/mp4");
    }

    @Test
    void textOnlyFallbackBypassesTranscription() {
        Fixture fixture = fixture(List.of());

        SpeakingTranscriptionResult result = fixture.resolver.textFallback("  저는   학생입니다.  ");

        assertThat(result.status()).isEqualTo(SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED);
        assertThat(result.transcript()).isEqualTo("저는 학생입니다.");
        assertThat(result.normalizedTranscript()).isEqualTo("저는 학생입니다.");
        assertThat(result.transcriptConfidence()).isNull();
    }

    @Test
    void failureToStringDoesNotExposeStoragePathApiKeyOrTranscript() {
        Fixture fixture = fixture(List.of(row(1L, PracticeSpeakingStorageProvider.OBJECT_STORAGE,
                "learner-speaking/ready/secret.webm", "audio/webm", 100L, 1200L)));

        SpeakingTranscriptionResult failure = fixture.resolver.resolveForOwner(77L, 88L, 99L).failure().orElseThrow();

        assertThat(failure.toString())
                .doesNotContain("secret.webm")
                .doesNotContain("learner-speaking")
                .doesNotContain("secret-key")
                .doesNotContain("저는 학생입니다");
    }

    private Fixture fixture(List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> rows) {
        PracticeSpeakingMediaRepository repository = mock(PracticeSpeakingMediaRepository.class);
        SpeakingAudioStorage storage = mock(SpeakingAudioStorage.class);
        SpeakingTranscriptionProperties properties = new SpeakingTranscriptionProperties(
                false,
                "openai",
                "https://api.openai.com/v1",
                "secret-key",
                "gpt-4o-mini-transcribe",
                "ko",
                26214400L,
                Duration.ofSeconds(30),
                2,
                true,
                "audio/webm,audio/mp4");
        when(repository.findAuthorizedTranscriptionCandidates(77L, 88L, 99L, PracticeSpeakingMediaStatus.READY))
                .thenReturn(rows);
        return new Fixture(new SpeakingTranscriptionMediaResolver(repository, storage, properties), repository, storage);
    }

    private PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection row(
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            String mimeType,
            Long byteSize,
            Long durationMs
    ) {
        return new TestProjection(mediaId, storageProvider, storageKey, mimeType, byteSize, durationMs);
    }

    private record Fixture(
            SpeakingTranscriptionMediaResolver resolver,
            PracticeSpeakingMediaRepository repository,
            SpeakingAudioStorage storage
    ) {}

    private record TestProjection(
            Long mediaId,
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            String mimeType,
            Long byteSize,
            Long durationMs
    ) implements PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection {
        @Override public Long getMediaId() { return mediaId; }
        @Override public Long getAttemptId() { return 88L; }
        @Override public Long getQuestionId() { return 99L; }
        @Override public Long getLockVersion() { return 3L; }
        @Override public PracticeSpeakingStorageProvider getStorageProvider() { return storageProvider; }
        @Override public String getStorageKey() { return storageKey; }
        @Override public String getMimeType() { return mimeType; }
        @Override public Long getByteSize() { return byteSize; }
        @Override public Long getDurationMs() { return durationMs; }
    }
}
