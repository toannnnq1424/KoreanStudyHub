package com.ksh.features.practice.manage.service;

import com.ksh.features.storage.StoredObject;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileObjectStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfiledPracticeAuthoringStorageTest {
    @Test
    void exactProfileReadsCanonicalObject() throws Exception {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        String key = "lecturer-assets/7/object.bin";
        when(profiles.open(StorageProfileCode.PRACTICE_AUTHORING, key))
                .thenReturn(new StoredObject(new ByteArrayInputStream(new byte[]{1, 2, 3}),
                        3L, "application/octet-stream"));
        ProfiledPracticeAuthoringStorage adapter =
                new ProfiledPracticeAuthoringStorage(profiles);

        assertThat(adapter.load("PRACTICE_AUTHORING", key)
                .getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void nullAndCrossProfileIdentityFailBeforeRead() {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        ProfiledPracticeAuthoringStorage adapter =
                new ProfiledPracticeAuthoringStorage(profiles);

        assertThatThrownBy(() -> adapter.load(null, "lecturer-assets/same-key.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STORAGE_IDENTITY_INVALID");
        assertThatThrownBy(() -> adapter.load(
                "GENERAL_UPLOADS", "lecturer-assets/same-key.bin"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void practiceSeedKeyIsBackendNeutralAndContainsNoLocalPath() throws Exception {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        String namespace = "practice-seed/topik35-v1/source/document";
        byte[] bytes = "canonical-source".getBytes();
        String digest = "1cc1112cbea7c8ac653b2af6f70e052ed14add9919d468e939d03e2ef5151042";
        String key = namespace + "/" + digest + ".pdf";
        when(profiles.exists(StorageProfileCode.PRACTICE_AUTHORING, key))
                .thenReturn(false);
        when(profiles.put(org.mockito.ArgumentMatchers.eq(
                        StorageProfileCode.PRACTICE_AUTHORING),
                org.mockito.ArgumentMatchers.eq(key),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("application/octet-stream"),
                org.mockito.ArgumentMatchers.eq((long) bytes.length)))
                .thenReturn(StorageBackend.LOCAL, StorageBackend.R2);
        ProfiledPracticeAuthoringStorage adapter =
                new ProfiledPracticeAuthoringStorage(profiles);

        AssetStorageService.StoredAsset local = adapter.store(
                new ByteArrayInputStream(bytes), "source.pdf", namespace);
        AssetStorageService.StoredAsset futureR2 = adapter.store(
                new ByteArrayInputStream(bytes), "source.pdf", namespace);

        assertThat(local.storageKey()).isEqualTo(key);
        assertThat(futureR2.storageKey()).isEqualTo(key);
        assertThat(local.storageProvider()).isEqualTo("LOCAL");
        assertThat(futureR2.storageProvider()).isEqualTo("R2");
        assertThat(key).doesNotStartWith("/")
                .doesNotContain("file:", "r2:", "http:", "https:", "\\");
    }

    @Test
    void practiceSeedNamespaceRejectsPathsUrisAndUnknownPrefixesBeforeWrite() {
        StorageProfileObjectStore profiles = mock(StorageProfileObjectStore.class);
        ProfiledPracticeAuthoringStorage adapter =
                new ProfiledPracticeAuthoringStorage(profiles);

        for (String namespace : new String[]{
                "/practice-seed/topik35-v1/source/document",
                "practice-seed/../source/document",
                "practice-seed/topik35-v1/file:/document",
                "practice-seed\\topik35-v1\\source",
                "other-seed/topik35-v1/source/document"}) {
            assertThatThrownBy(() -> adapter.store(
                    new ByteArrayInputStream(new byte[]{1}), "source.pdf", namespace))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("STORAGE_IDENTITY_INVALID");
        }
        verifyNoInteractions(profiles);
    }
}
