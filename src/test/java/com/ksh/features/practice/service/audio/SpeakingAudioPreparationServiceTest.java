package com.ksh.features.practice.service.audio;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.ValidatedSpeakingMediaDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingAudioPreparationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void preparesValidatedFinalDescriptorWithoutDbInteraction() {
        LocalPrivateSpeakingAudioStorage storage = storage();
        SpeakingAudioPreparationService service = new SpeakingAudioPreparationService(storage,
                path -> new SpeakingAudioInspection("webm", "opus", "audio/webm", 1500L));

        PreparedSpeakingAudio prepared = service.prepare(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "video/webm;codecs=opus");
        ValidatedSpeakingMediaDescriptor descriptor = prepared.toDescriptor();

        assertThat(prepared.storageProvider()).isEqualTo(PracticeSpeakingStorageProvider.LOCAL);
        assertThat(prepared.storageKey()).startsWith("learner-speaking/ready/");
        assertThat(prepared.mimeType()).isEqualTo("audio/webm");
        assertThat(prepared.container()).isEqualTo("webm");
        assertThat(prepared.codec()).isEqualTo("opus");
        assertThat(prepared.byteSize()).isEqualTo(3L);
        assertThat(prepared.durationMs()).isEqualTo(1500L);
        assertThat(prepared.contentHash()).isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(descriptor.storageKey()).isEqualTo(prepared.storageKey());
        assertThat(descriptor.mimeType()).isEqualTo("audio/webm");
        assertThat(storage.exists(prepared.storageKey())).isTrue();
        assertThat(prepared.toString())
                .doesNotContain("private-root")
                .doesNotContain("learner-speaking")
                .doesNotContain(prepared.contentHash());
    }

    @Test
    void allowsOctetStreamHintButRejectsClearContradiction() {
        LocalPrivateSpeakingAudioStorage storage = storage();
        SpeakingAudioPreparationService service = new SpeakingAudioPreparationService(storage,
                path -> new SpeakingAudioInspection("mp4", "aac", "audio/mp4", 1000L));

        PreparedSpeakingAudio prepared = service.prepare(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "APPLICATION/OCTET-STREAM");

        assertThat(prepared.mimeType()).isEqualTo("audio/mp4");
        assertThatThrownBy(() -> service.prepare(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "audio/webm"))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.UNSUPPORTED_TYPE);
    }

    @Test
    void validationFailureDeletesTemporaryObjectAndReturnsNoDescriptor() throws Exception {
        LocalPrivateSpeakingAudioStorage storage = storage();
        SpeakingAudioPreparationService service = new SpeakingAudioPreparationService(storage, path -> {
            throw new SpeakingAudioValidationException(SpeakingAudioValidationCategory.INVALID_CONTAINER, "Audio container is unsupported");
        });

        assertThatThrownBy(() -> service.prepare(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "audio/webm"))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.INVALID_CONTAINER);

        Path temporaryRoot = tempDir.resolve("private-root").resolve("learner-speaking").resolve("temporary");
        try (var paths = java.nio.file.Files.list(temporaryRoot)) {
            assertThat(paths).isEmpty();
        }
        Path readyRoot = tempDir.resolve("private-root").resolve("learner-speaking").resolve("ready");
        assertThat(java.nio.file.Files.exists(readyRoot)).isFalse();
    }

    @Test
    void mimeMismatchDeletesTemporaryObject() throws Exception {
        LocalPrivateSpeakingAudioStorage storage = storage();
        SpeakingAudioPreparationService service = new SpeakingAudioPreparationService(storage,
                path -> new SpeakingAudioInspection("mp4", "aac", "audio/mp4", 1000L));

        assertThatThrownBy(() -> service.prepare(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "audio/webm"))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.UNSUPPORTED_TYPE);

        Path temporaryRoot = tempDir.resolve("private-root").resolve("learner-speaking").resolve("temporary");
        try (var paths = java.nio.file.Files.list(temporaryRoot)) {
            assertThat(paths).isEmpty();
        }
    }

    @Test
    void promoteFailureReturnsNoDescriptorAndLeavesNoFinalObject() {
        SpeakingAudioStorage storage = new FailingPromoteStorage();
        SpeakingAudioPreparationService service = new SpeakingAudioPreparationService(storage,
                path -> new SpeakingAudioInspection("webm", "opus", "audio/webm", 1000L));

        assertThatThrownBy(() -> service.prepare(new ByteArrayInputStream(new byte[]{1}), 1L, "audio/webm"))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.STORAGE_FAILURE);

        FailingPromoteStorage failing = (FailingPromoteStorage) storage;
        assertThat(failing.tempDeleted).isTrue();
        assertThat(failing.finalCreated).isFalse();
    }

    private LocalPrivateSpeakingAudioStorage storage() {
        SpeakingAudioProperties properties = new SpeakingAudioProperties(
                tempDir.resolve("private-root").toString(),
                tempDir.resolve("public-root").toString(),
                "ffprobe",
                Duration.ofSeconds(10),
                262144,
                65536,
                25 * 1024 * 1024L,
                Duration.ofMinutes(10)
        );
        return new LocalPrivateSpeakingAudioStorage(properties);
    }

    private static final class FailingPromoteStorage implements SpeakingAudioStorage {
        boolean tempDeleted;
        boolean finalCreated;

        @Override
        public StoredSpeakingAudioObject writeTemporary(java.io.InputStream content, Long declaredContentLength) {
            return new StoredSpeakingAudioObject(
                    "learner-speaking/temporary/fake",
                    1L,
                    "4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7c928f53e1c3f70",
                    Path.of("private")
            );
        }

        @Override
        public String promoteTemporary(String temporaryKey) {
            throw new SpeakingAudioValidationException(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage operation failed");
        }

        @Override
        public java.io.InputStream open(String storageKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String storageKey) {
            return false;
        }

        @Override
        public void delete(String storageKey) {
            if (storageKey.startsWith("learner-speaking/temporary/")) {
                tempDeleted = true;
            } else {
                finalCreated = true;
            }
        }
    }
}
