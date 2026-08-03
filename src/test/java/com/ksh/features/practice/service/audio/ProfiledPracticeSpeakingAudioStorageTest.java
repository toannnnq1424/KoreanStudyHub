package com.ksh.features.practice.service.audio;

import com.ksh.features.storage.StoredObject;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileObjectStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfiledPracticeSpeakingAudioStorageTest {
    @Test
    void exactProfileReadsCanonicalKeyAndRejectsNullOrCrossProfile() throws Exception {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        String key = "learner-speaking/ready/test";
        when(profiles.open(StorageProfileCode.PRACTICE_SPEAKING, key))
                .thenReturn(new StoredObject(new ByteArrayInputStream(new byte[]{4, 5, 6}),
                        3L, "audio/webm"));
        ProfiledPracticeSpeakingAudioStorage adapter =
                new ProfiledPracticeSpeakingAudioStorage(profiles, properties());

        try (var input = adapter.open("PRACTICE_SPEAKING", key)) {
            assertThat(input.readAllBytes()).containsExactly(4, 5, 6);
        }
        assertThatThrownBy(() -> adapter.open(null, key))
                .isInstanceOf(SpeakingAudioValidationException.class);
        assertThatThrownBy(() -> adapter.open("GENERAL_UPLOADS", key))
                .isInstanceOf(SpeakingAudioValidationException.class);
    }

    @Test
    void invalidProfileFailsBeforeObjectStoreAccess() {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        ProfiledPracticeSpeakingAudioStorage adapter =
                new ProfiledPracticeSpeakingAudioStorage(profiles, properties());
        assertThatThrownBy(() -> adapter.exists(null, "learner-speaking/ready/test"))
                .isInstanceOf(SpeakingAudioValidationException.class);
        verifyNoInteractions(profiles);
    }

    private SpeakingAudioProperties properties() {
        return new SpeakingAudioProperties("unused-private", "unused-public",
                "ffprobe", Duration.ofSeconds(10), 262144, 65536,
                1024L, Duration.ofMinutes(10));
    }
}
