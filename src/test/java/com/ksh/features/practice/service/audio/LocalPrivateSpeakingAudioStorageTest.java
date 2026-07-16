package com.ksh.features.practice.service.audio;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPrivateSpeakingAudioStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsPrivateRootUnderPublicRoot() throws Exception {
        Path publicRoot = tempDir.resolve("public-root");
        Files.createDirectories(publicRoot);

        assertThatThrownBy(() -> storage(publicRoot, publicRoot, 10))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.STORAGE_FAILURE);
        assertThatThrownBy(() -> storage(publicRoot.resolve("nested-private"), publicRoot, 10))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.STORAGE_FAILURE);
        assertThatThrownBy(() -> storage(publicRoot, publicRoot.resolve("nested-public"), 10))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.STORAGE_FAILURE);
        assertThat(storage(tempDir.resolve("private-root"), tempDir.resolve("public-root"), 10))
                .isNotNull();
    }

    @Test
    void writesTemporaryStreamWithByteCountAndLowercaseSha256() {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(10);

        StoredSpeakingAudioObject object = storage.writeTemporary(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L);

        assertThat(object.getStorageKey()).startsWith("learner-speaking/temporary/");
        assertThat(object.getByteSize()).isEqualTo(3L);
        assertThat(object.getSha256()).isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(storage.exists(object.getStorageKey())).isTrue();
        assertThat(object.toString()).doesNotContain(object.getStorageKey()).doesNotContain(object.getSha256());
    }

    @Test
    void rejectsDeclaredOversizeBeforeWrite() {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(2);

        StoredSpeakingAudioObject exactMax = storage.writeTemporary(new ByteArrayInputStream(new byte[]{1, 2}), 2L);
        assertThat(exactMax.getByteSize()).isEqualTo(2L);

        assertThatThrownBy(() -> storage.writeTemporary(new ByteArrayInputStream(new byte[]{1}), 3L))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.TOO_LARGE);
    }

    @Test
    void rejectsActualStreamOversizeAndDeletesPartialTemp() throws Exception {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(2);

        assertThatThrownBy(() -> storage.writeTemporary(new ByteArrayInputStream(new byte[]{1, 2, 3}), null))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.TOO_LARGE);

        Path temporaryRoot = tempDir.resolve("private-root").resolve("learner-speaking").resolve("temporary");
        assertThat(Files.exists(temporaryRoot))
                .as("temporary directory may exist but must not contain partial objects")
                .isTrue();
        try (var paths = Files.list(temporaryRoot)) {
            assertThat(paths).isEmpty();
        }
    }

    @Test
    void sameBytesProduceSameHashWithoutLeakingHash() {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(10);

        StoredSpeakingAudioObject first = storage.writeTemporary(new ByteArrayInputStream(new byte[]{4, 5, 6}), 3L);
        StoredSpeakingAudioObject second = storage.writeTemporary(new ByteArrayInputStream(new byte[]{4, 5, 6}), 3L);

        assertThat(first.getSha256()).isEqualTo(second.getSha256());
        assertThat(first.getSha256()).hasSize(64).isLowerCase();
        assertThat(first.toString()).doesNotContain(first.getSha256()).doesNotContain(first.getStorageKey());
    }

    @Test
    void rejectsZeroByteStreamAndDeletesPartialTemp() throws Exception {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(10);

        assertThatThrownBy(() -> storage.writeTemporary(InputStream.nullInputStream(), null))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.EMPTY);

        Path temporaryRoot = tempDir.resolve("private-root").resolve("learner-speaking").resolve("temporary");
        assertThat(Files.exists(temporaryRoot)).isTrue();
        try (var paths = Files.list(temporaryRoot)) {
            assertThat(paths).isEmpty();
        }
    }

    @Test
    void promotesTemporaryToReadyWithoutOverwritingAndRemovesTemp() throws Exception {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(10);
        StoredSpeakingAudioObject temporary = storage.writeTemporary(new ByteArrayInputStream(new byte[]{9, 8}), 2L);

        String finalKey = storage.promoteTemporary(temporary.getStorageKey());

        assertThat(finalKey).startsWith("learner-speaking/ready/");
        assertThat(finalKey).isNotEqualTo(temporary.getStorageKey());
        assertThat(storage.exists(temporary.getStorageKey())).isFalse();
        assertThat(storage.exists(finalKey)).isTrue();
        assertThat(readTwoBytes(storage.open(finalKey))).containsExactly(9, 8);
    }

    @Test
    void deleteIsIdempotentAndDoesNotExposeUrl() {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(10);
        StoredSpeakingAudioObject temporary = storage.writeTemporary(new ByteArrayInputStream(new byte[]{7}), 1L);

        storage.delete(temporary.getStorageKey());
        storage.delete(temporary.getStorageKey());

        assertThat(storage.exists(temporary.getStorageKey())).isFalse();
        assertThat(temporary.getStorageKey()).doesNotContain("uploads");
    }

    @Test
    void rejectsPathTraversalAbsoluteWindowsAndDirectoryDelete() throws Exception {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(10);

        assertThatThrownBy(() -> storage.exists("../secret"))
                .isInstanceOf(SpeakingAudioValidationException.class);
        assertThatThrownBy(() -> storage.exists("/secret"))
                .isInstanceOf(SpeakingAudioValidationException.class);
        assertThatThrownBy(() -> storage.exists("c:/secret"))
                .isInstanceOf(SpeakingAudioValidationException.class);
        assertThatThrownBy(() -> storage.exists("\\\\server\\share"))
                .isInstanceOf(SpeakingAudioValidationException.class);

        Path directory = tempDir.resolve("private-root").resolve("learner-speaking").resolve("ready").resolve("dir");
        Files.createDirectories(directory);
        assertThatThrownBy(() -> storage.delete("learner-speaking/ready/dir"))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.STORAGE_FAILURE);
        assertThat(Files.exists(directory)).isTrue();
    }

    @Test
    void rejectsSymlinkObjectWhereSupported() throws Exception {
        LocalPrivateSpeakingAudioStorage storage = defaultStorage(10);

        Path target = tempDir.resolve("private-root").resolve("learner-speaking").resolve("ready").resolve("target");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{1});
        Path symlink = tempDir.resolve("private-root").resolve("learner-speaking").resolve("ready").resolve("link");
        try {
            Files.createSymbolicLink(symlink, target);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            Assumptions.abort("Symbolic links are not available in this test environment");
        }
        assertThatThrownBy(() -> storage.open("learner-speaking/ready/link"))
                .isInstanceOf(SpeakingAudioValidationException.class);
        assertThatThrownBy(() -> storage.exists("learner-speaking/ready/link"))
                .isInstanceOf(SpeakingAudioValidationException.class);
        assertThatThrownBy(() -> storage.delete("learner-speaking/ready/link"))
                .isInstanceOf(SpeakingAudioValidationException.class);
    }

    @Test
    void rejectsSymlinkEscapeWhereSupported() throws Exception {
        Path privateRoot = tempDir.resolve("private-root");
        Path publicRoot = tempDir.resolve("public-root");
        Files.createDirectories(privateRoot.resolve("learner-speaking"));
        Files.createDirectories(publicRoot);
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Path symlink = privateRoot.resolve("learner-speaking").resolve("temporary");
        try {
            Files.createSymbolicLink(symlink, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            Assumptions.abort("Symbolic links are not available in this test environment");
        }

        LocalPrivateSpeakingAudioStorage storage = storage(privateRoot, publicRoot, 10);
        assertThatThrownBy(() -> storage.writeTemporary(new ByteArrayInputStream(new byte[]{1}), 1L))
                .isInstanceOf(SpeakingAudioValidationException.class)
                .extracting("category")
                .isEqualTo(SpeakingAudioValidationCategory.STORAGE_FAILURE);
    }

    private LocalPrivateSpeakingAudioStorage defaultStorage(long maxBytes) {
        return storage(tempDir.resolve("private-root"), tempDir.resolve("public-root"), maxBytes);
    }

    private LocalPrivateSpeakingAudioStorage storage(Path privateRoot, Path publicRoot, long maxBytes) {
        SpeakingAudioProperties properties = new SpeakingAudioProperties(
                privateRoot.toString(),
                publicRoot.toString(),
                "ffprobe",
                Duration.ofSeconds(10),
                262144,
                65536,
                maxBytes,
                Duration.ofMinutes(10)
        );
        return new LocalPrivateSpeakingAudioStorage(properties);
    }

    private int[] readTwoBytes(InputStream input) throws Exception {
        try (InputStream in = input) {
            return new int[]{in.read(), in.read()};
        }
    }
}
