package com.ksh.features.upload;

import com.ksh.features.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static com.ksh.entities.LibraryAsset.KIND_DOCUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Storage-key contracts; ObjectStorage is mocked so no R2 client is touched. */
@ExtendWith(MockitoExtension.class)
class LibraryStorageServiceOwnerScopeTest {

    @Mock private ObjectStorage objectStorage;

    private LibraryStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LibraryStorageService(objectStorage);
    }

    @Test
    void upload_uses_deterministic_owner_prefix_in_shared_object_store() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "slide.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31});

        var stored = storage.store(pdf, 42L, KIND_DOCUMENT);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).put(key.capture(), any(InputStream.class),
                eq("application/pdf"), eq(pdf.getSize()));
        assertThat(key.getValue()).matches(
                "library/42/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
        assertThat(stored.storedPath()).isEqualTo(key.getValue());
    }

    @Test
    void upload_without_valid_owner_fails_before_storage_write() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "slide.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31});

        assertThatThrownBy(() -> storage.store(pdf, null, KIND_DOCUMENT))
                .isInstanceOf(IllegalArgumentException.class);
        verify(objectStorage, never()).put(any(), any(), any(), eq(pdf.getSize()));
    }

    @Test
    void persisted_key_must_match_authenticated_owner_prefix() {
        assertThat(storage.requireOwnedKey(42L, "library/42/file.pdf"))
                .isEqualTo("library/42/file.pdf");
        assertThatThrownBy(() -> storage.requireOwnedKey(42L, "library/7/file.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.requireOwnedKey(42L, "lessons/42/file.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
