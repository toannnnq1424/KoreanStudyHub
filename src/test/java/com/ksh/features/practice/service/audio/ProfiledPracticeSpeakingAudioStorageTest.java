package com.ksh.features.practice.service.audio;

import com.ksh.features.storage.profile.StorageProfileObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProfiledPracticeSpeakingAudioStorageTest {
    @TempDir Path root;

    @Test
    void nullAndLegacyOverloadsStayInsideCurrentPrivateLocalRoot() throws Exception {
        SpeakingAudioProperties properties = properties();
        LocalPrivateSpeakingAudioStorage legacy =
                new LocalPrivateSpeakingAudioStorage(properties);
        StoredSpeakingAudioObject temporary = legacy.writeTemporary(
                new ByteArrayInputStream(new byte[]{4, 5, 6}), 3L);
        String readyKey = legacy.promoteTemporary(temporary.getStorageKey());
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        ProfiledPracticeSpeakingAudioStorage adapter =
                new ProfiledPracticeSpeakingAudioStorage(profiles, legacy, properties);

        try (var input = adapter.open(readyKey)) {
            assertThat(input.readAllBytes()).containsExactly(4, 5, 6);
        }
        try (var input = adapter.open(null, readyKey)) {
            assertThat(input.readAllBytes()).containsExactly(4, 5, 6);
        }
        assertThatThrownBy(() -> adapter.open("GENERAL_UPLOADS", readyKey))
                .isInstanceOf(SpeakingAudioValidationException.class);
        verifyNoInteractions(profiles);
    }

    private SpeakingAudioProperties properties() {
        return new SpeakingAudioProperties(
                root.resolve("private").toString(),
                root.resolve("public").toString(),
                "ffprobe", Duration.ofSeconds(10), 262144, 65536,
                1024L, Duration.ofMinutes(10));
    }
}
