package com.ksh.features.practice.manage.service;

import com.ksh.features.storage.StoredObject;
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
}
