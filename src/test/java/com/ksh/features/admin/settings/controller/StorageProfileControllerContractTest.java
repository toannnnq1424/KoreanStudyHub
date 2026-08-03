package com.ksh.features.admin.settings.controller;

import com.ksh.features.admin.settings.service.StorageProfileAdminService;
import com.ksh.features.admin.settings.dto.StorageProfileDtos.ProfileRow;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.ConcurrentModel;

import java.util.List;
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

    @Test
    void practiceListFiltersGeneralUploadsButDirectGeneralRouteRemainsSupported() {
        StorageProfileAdminService service = mock(StorageProfileAdminService.class);
        when(service.profiles()).thenReturn(List.of(
                row(StorageProfileCode.GENERAL_UPLOADS),
                row(StorageProfileCode.PRACTICE_AUTHORING)));
        when(service.missingCodes()).thenReturn(List.of(StorageProfileCode.PRACTICE_SPEAKING));
        ConcurrentModel model = new ConcurrentModel();

        String view = new StorageProfileController(service).list(model);

        assertThat(view).isEqualTo("admin/settings-storage-profiles");
        assertThat((List<?>) model.getAttribute("profiles"))
                .extracting("profileCode")
                .containsExactly(StorageProfileCode.PRACTICE_AUTHORING);
        assertThat(model.getAttribute("missingCodes"))
                .isEqualTo(List.of(StorageProfileCode.PRACTICE_SPEAKING));
    }

    private static ProfileRow row(StorageProfileCode code) {
        return new ProfileRow(code, StorageBackend.LOCAL, "", "", "", "",
                "auto", code.fixedKeyPrefix(), false, 0L, null);
    }
}
