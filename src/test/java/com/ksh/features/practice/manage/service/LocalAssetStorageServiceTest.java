package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAssetStorageServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void contentAddressedStoreReportsWhetherItCreatedTheObject() throws Exception {
        LocalAssetStorageService storage = new LocalAssetStorageService(uploadRoot.toString());
        byte[] content = new byte[]{1, 2, 3, 4};

        AssetStorageService.StoredAsset first = storage.store(
                new ByteArrayInputStream(content), "audio.MP3", "private/audio");
        AssetStorageService.StoredAsset duplicate = storage.store(
                new ByteArrayInputStream(content), "audio.MP3", "private/audio");

        assertThat(first.storageKey()).isEqualTo(duplicate.storageKey()).endsWith(".mp3");
        assertThat(first.newlyCreated()).isTrue();
        assertThat(duplicate.newlyCreated()).isFalse();
        assertThat(storage.exists(first.storageKey())).isTrue();
    }

    @Test
    void unsafeFilenameSuffixCannotCreateNestedStoragePath() throws Exception {
        LocalAssetStorageService storage = new LocalAssetStorageService(uploadRoot.toString());

        AssetStorageService.StoredAsset stored = storage.store(
                new ByteArrayInputStream(new byte[]{9}),
                "image.png/child", "private/images");

        assertThat(stored.storageKey()).endsWith(".bin");
        assertThat(storage.exists(stored.storageKey())).isTrue();
    }
}
