package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeSeedAssetStorageTest {
    private final AssetStorageService storage = mock(AssetStorageService.class);
    private final PracticeSeedAssetStorage seeds = new PracticeSeedAssetStorage(storage);

    @Test
    void storesThroughCanonicalPortAndReturnsLogicalKeyOnly() throws Exception {
        String digest = "60fb5fa5e5a211609d0d6e36bc1cacc490c34ff5a0009e6952e931f9a850d17b";
        String namespace = "practice-seed/topik35-v1/source/document";
        String key = namespace + "/" + digest + ".pdf";
        byte[] bytes = new byte[]{1, 2, 3};
        when(storage.store(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("answers.pdf"),
                org.mockito.ArgumentMatchers.eq(namespace)))
                .thenReturn(new AssetStorageService.StoredAsset(
                        key, bytes.length, digest, true,
                        "PRACTICE_AUTHORING", "LOCAL"));

        PracticeSeedAssetStorage.StoredSeedAsset stored = seeds.store(
                "topik35-v1", PracticeSeedAssetStorage.AssetKind.SOURCE_DOCUMENT,
                new ByteArrayInputStream(bytes), "answers.pdf", "application/pdf");

        assertThat(stored.logicalKey()).isEqualTo(key);
        assertThat(stored.mediaType()).isEqualTo("application/pdf");
        assertThat(stored.storageProvider()).isEqualTo("LOCAL");
        assertThat(stored.logicalKey()).doesNotStartWith("/")
                .doesNotContain("file:", "http:", "https:", "r2:", "\\");
        verify(storage).store(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("answers.pdf"),
                org.mockito.ArgumentMatchers.eq(namespace));
    }

    @Test
    void invalidBundleMediaTypeOrStorageResultFailsClosed() throws Exception {
        for (String bundleId : new String[]{
                "TOPIK35", "../topik35", "/tmp/topik35", "topik35/v1"}) {
            assertThatThrownBy(() -> seeds.store(bundleId,
                    PracticeSeedAssetStorage.AssetKind.SOURCE_DOCUMENT,
                    new ByteArrayInputStream(new byte[]{1}), "source.pdf",
                    "application/pdf"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PRACTICE_SEED_ASSET_INVALID");
        }
        assertThatThrownBy(() -> seeds.store("topik35-v1",
                PracticeSeedAssetStorage.AssetKind.SOURCE_DOCUMENT,
                new ByteArrayInputStream(new byte[]{1}), "source.pdf",
                "Application/PDF"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);

        String digest = "60fb5fa5e5a211609d0d6e36bc1cacc490c34ff5a0009e6952e931f9a850d17b";
        when(storage.store(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("source.pdf"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AssetStorageService.StoredAsset(
                        "/tmp/source.pdf", 1L, digest, true,
                        "PRACTICE_AUTHORING", "LOCAL"));

        assertThatThrownBy(() -> seeds.store("topik35-v1",
                PracticeSeedAssetStorage.AssetKind.SOURCE_DOCUMENT,
                new ByteArrayInputStream(new byte[]{1}), "source.pdf",
                "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
