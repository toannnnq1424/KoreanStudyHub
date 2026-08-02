package com.ksh.features.admin.settings.service;

import com.ksh.features.admin.settings.dto.StorageProfileDtos;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfile;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileR2Clients;
import com.ksh.features.storage.profile.StorageProfileRepository;
import com.ksh.features.storage.profile.StorageProfileResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageProfileAdminServiceTest {

    @Test
    void ordinaryFormMasksSecretAndMaskedUpdateRetainsIt() {
        StorageProfileRepository repository = mock(StorageProfileRepository.class);
        StorageProfile profile = r2(false);
        when(repository.findById(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(Optional.of(profile));
        when(repository.findByCodeForUpdate(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(Optional.of(profile));
        StorageProfileR2Clients clients = mock(StorageProfileR2Clients.class);
        StorageProfileAdminService service = new StorageProfileAdminService(
                repository, mock(StorageProfileResolver.class), clients, mock(JdbcTemplate.class));

        assertThat(service.form(StorageProfileCode.PRACTICE_AUTHORING).orElseThrow()
                .secretAccessKey()).isEqualTo(StorageProfileDtos.MASKED);
        service.save(new StorageProfileDtos.ProfileForm(
                StorageProfileCode.PRACTICE_AUTHORING, StorageBackend.R2,
                "account-1", "access", StorageProfileDtos.MASKED,
                "private-bucket", "https://account-1.r2.cloudflarestorage.com",
                "auto", "practice-authoring", false, 0L), 42L);

        assertThat(profile.getSecretAccessKey()).isEqualTo("top-secret");
        assertThat(profile.getUpdatedBy()).isEqualTo(42L);
        verify(clients).invalidate(StorageProfileCode.PRACTICE_AUTHORING);
    }

    @Test
    void staleRevisionAndMutablePrefixAreRejected() {
        StorageProfileRepository repository = mock(StorageProfileRepository.class);
        StorageProfile profile = r2(false);
        when(repository.findByCodeForUpdate(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(Optional.of(profile));
        StorageProfileAdminService service = new StorageProfileAdminService(
                repository, mock(StorageProfileResolver.class),
                mock(StorageProfileR2Clients.class), mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.save(form("practice-authoring", 9L), 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STORAGE_PROFILE_REVISION_CONFLICT");
        assertThatThrownBy(() -> service.save(form("general-uploads", 0L), 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("STORAGE_IDENTITY_INVALID");
        assertThatThrownBy(() -> service.toggle(
                StorageProfileCode.PRACTICE_AUTHORING, 9L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STORAGE_PROFILE_REVISION_CONFLICT");
        assertThatThrownBy(() -> service.delete(
                StorageProfileCode.PRACTICE_AUTHORING, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STORAGE_PROFILE_REVISION_CONFLICT");
    }

    private static StorageProfileDtos.ProfileForm form(String prefix, Long revision) {
        return new StorageProfileDtos.ProfileForm(
                StorageProfileCode.PRACTICE_AUTHORING, StorageBackend.R2,
                "account-1", "access", StorageProfileDtos.MASKED,
                "private-bucket", "https://account-1.r2.cloudflarestorage.com",
                "auto", prefix, false, revision);
    }

    private static StorageProfile r2(boolean enabled) {
        return new StorageProfile(StorageProfileCode.PRACTICE_AUTHORING, StorageBackend.R2,
                "account-1", "access", "top-secret", "private-bucket",
                "https://account-1.r2.cloudflarestorage.com", "auto", enabled, 1L);
    }
}
