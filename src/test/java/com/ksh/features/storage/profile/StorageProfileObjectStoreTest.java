package com.ksh.features.storage.profile;

import com.ksh.features.storage.LocalObjectStorage;
import com.ksh.features.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageProfileObjectStoreTest {
    @TempDir Path root;

    @Test
    void sameLogicalKeyIsFencedByExactProfileWithoutBucketSearch() throws Exception {
        StorageProfileResolver resolver = mock(StorageProfileResolver.class);
        for (StorageProfileCode code : StorageProfileCode.values()) {
            ResolvedStorageProfile profile = local(code);
            when(resolver.resolveForWrite(code)).thenReturn(profile);
            when(resolver.resolveForRead(code)).thenReturn(profile);
        }
        StorageProfileObjectStore store = new StorageProfileObjectStore(
                resolver, new LocalObjectStorage(root), mock(StorageProfileR2Clients.class));
        byte[] bytes = "private-authoring".getBytes(StandardCharsets.UTF_8);

        store.put(StorageProfileCode.PRACTICE_AUTHORING, "lecturer-assets/a.bin",
                new ByteArrayInputStream(bytes), "application/octet-stream", bytes.length);

        assertThat(Files.readAllBytes(root.resolve(
                "practice-authoring/lecturer-assets/a.bin"))).isEqualTo(bytes);
        assertThatThrownBy(() -> store.open(
                StorageProfileCode.GENERAL_UPLOADS, "lecturer-assets/a.bin"))
                .isInstanceOf(IOException.class);
        try (StoredObject object = store.open(
                StorageProfileCode.PRACTICE_AUTHORING, "lecturer-assets/a.bin")) {
            assertThat(object.inputStream().readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void traversalStopsBeforeAnyFilesystemWrite() {
        StorageProfileResolver resolver = mock(StorageProfileResolver.class);
        when(resolver.resolveForWrite(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(local(StorageProfileCode.PRACTICE_AUTHORING));
        StorageProfileObjectStore store = new StorageProfileObjectStore(
                resolver, new LocalObjectStorage(root), mock(StorageProfileR2Clients.class));

        assertThatThrownBy(() -> store.put(StorageProfileCode.PRACTICE_AUTHORING,
                "../general-uploads/escape", new ByteArrayInputStream(new byte[]{1}),
                "application/octet-stream", 1L))
                .isInstanceOf(StorageProfileException.class);
        assertThat(root).isEmptyDirectory();
    }

    private static ResolvedStorageProfile local(StorageProfileCode code) {
        return new ResolvedStorageProfile(code, StorageBackend.LOCAL,
                "", "", "", "", "", "auto", code.fixedKeyPrefix(), 0L);
    }
}
