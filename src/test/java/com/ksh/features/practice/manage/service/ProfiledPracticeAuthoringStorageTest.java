package com.ksh.features.practice.manage.service;

import com.ksh.features.storage.profile.StorageProfileObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProfiledPracticeAuthoringStorageTest {
    @TempDir Path legacyRoot;

    @Test
    void nullIdentityReadsOnlyCurrentLegacyRoot() throws Exception {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        LocalAssetStorageService legacy = new LocalAssetStorageService(legacyRoot.toString());
        var stored = legacy.store(new ByteArrayInputStream(new byte[]{1, 2, 3}),
                "a.bin", "lecturer-assets/legacy");
        ProfiledPracticeAuthoringStorage adapter =
                new ProfiledPracticeAuthoringStorage(profiles, legacy);

        assertThat(adapter.load(null, stored.storageKey()).getInputStream().readAllBytes())
                .containsExactly(1, 2, 3);
        assertThat(adapter.load(stored.storageKey()).getInputStream().readAllBytes())
                .containsExactly(1, 2, 3);
        assertThatThrownBy(() -> adapter.load(null, "../outside.bin"))
                .isInstanceOf(java.io.FileNotFoundException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void nonNullIdentityRejectsEveryCrossProfileCodeBeforeRead() {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        ProfiledPracticeAuthoringStorage adapter = new ProfiledPracticeAuthoringStorage(
                profiles, new LocalAssetStorageService(legacyRoot.toString()));

        assertThatThrownBy(() -> adapter.load(
                "GENERAL_UPLOADS", "lecturer-assets/same-key.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STORAGE_IDENTITY_INVALID");
        assertThatThrownBy(() -> adapter.load(
                "PRACTICE_SPEAKING", "lecturer-assets/same-key.bin"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(profiles);
    }
}
