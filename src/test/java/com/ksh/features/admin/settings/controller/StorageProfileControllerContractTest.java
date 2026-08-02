package com.ksh.features.admin.settings.controller;

import com.ksh.features.admin.settings.service.StorageProfileAdminService;
import com.ksh.features.storage.profile.StorageProfileCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageProfileControllerContractTest {

    @Test
    void existingStorageAuthorityGatesWholeController() {
        PreAuthorize annotation = StorageProfileController.class
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('PERM_system.storage')");
    }

    @Test
    void explicitRevealIsNoStoreAndOrdinaryPayloadDoesNotExistHere() {
        StorageProfileAdminService service = mock(StorageProfileAdminService.class);
        when(service.revealSecret(StorageProfileCode.PRACTICE_SPEAKING))
                .thenReturn(Optional.of("secret-value"));

        var response = new StorageProfileController(service)
                .reveal(StorageProfileCode.PRACTICE_SPEAKING);

        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).containsEntry("secret", "secret-value");
    }
}
