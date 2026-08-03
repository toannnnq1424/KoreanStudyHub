package com.ksh.features.practice.service.audio;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingAudioPreparationServiceTest {
    @TempDir Path tempDir;

    @Test
    void preparesExactProfileTemporaryDescriptorWithoutDbInteraction() {
        ExactTemporaryStorage storage = new ExactTemporaryStorage(tempDir);
        SpeakingAudioPreparationService service = new SpeakingAudioPreparationService(storage,
                path -> new SpeakingAudioInspection("webm", "opus", "audio/webm", 1500L));

        PreparedSpeakingAudio prepared = service.prepare(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L,
                "video/webm;codecs=opus");

        assertThat(prepared.storageProvider()).isEqualTo(PracticeSpeakingStorageProvider.LOCAL);
        assertThat(prepared.storageProfileCode()).isEqualTo("PRACTICE_SPEAKING");
        assertThat(prepared.storageKey()).startsWith("learner-speaking/temporary/");
        assertThat(prepared.temporaryDescriptor().storageProfileCode())
                .isEqualTo("PRACTICE_SPEAKING");
        assertThat(prepared.mimeType()).isEqualTo("audio/webm");
        assertThat(prepared.byteSize()).isEqualTo(3L);
        assertThat(prepared.durationMs()).isEqualTo(1500L);
        assertThat(prepared.contentHash())
                .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(storage.deleted).isFalse();
    }

    @Test
    void mimeMismatchDeletesExactProfileTemporaryObject() {
        ExactTemporaryStorage storage = new ExactTemporaryStorage(tempDir);
        SpeakingAudioPreparationService service = new SpeakingAudioPreparationService(storage,
                path -> new SpeakingAudioInspection("mp4", "aac", "audio/mp4", 1000L));

        assertThatThrownBy(() -> service.prepare(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "audio/webm"))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.UNSUPPORTED_TYPE);
        assertThat(storage.deleted).isTrue();
        assertThat(storage.deletedProfile).isEqualTo("PRACTICE_SPEAKING");
    }

    private static final class ExactTemporaryStorage implements SpeakingAudioStorage {
        private final Path root;
        private byte[] bytes;
        private boolean deleted;
        private String deletedProfile;

        private ExactTemporaryStorage(Path root) {
            this.root = root;
        }

        @Override
        public StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredLength) {
            try {
                bytes = content.readAllBytes();
                Path inspection = Files.createTempFile(root, "inspection-", ".bin");
                Files.write(inspection, bytes);
                return new StoredSpeakingAudioObject(
                        "learner-speaking/temporary/test", bytes.length,
                        "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                        inspection, "PRACTICE_SPEAKING", PracticeSpeakingStorageProvider.LOCAL);
            } catch (java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override public String promoteTemporary(String profile, String key) { return key; }
        @Override public InputStream open(String profile, String key) {
            return new ByteArrayInputStream(bytes);
        }
        @Override public boolean exists(String profile, String key) { return !deleted; }
        @Override public void delete(String profile, String key) {
            deleted = true;
            deletedProfile = profile;
        }
    }
}
