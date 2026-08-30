package com.ksh.features.admin.settings.service;

import com.ksh.features.admin.settings.dto.StorageProfileDtos;
import com.ksh.features.admin.settings.dto.StorageProfileDtos.ConnectionTestStatus;
import com.ksh.features.storage.profile.ResolvedStorageProfile;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfile;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileException;
import com.ksh.features.storage.profile.StorageProfileR2Clients;
import com.ksh.features.storage.profile.StorageProfileRepository;
import com.ksh.features.storage.profile.StorageProfileResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void connectionTestUsesSavedR2ProfileAndBoundsTheNetworkCall() {
        StorageProfileRepository repository = mock(StorageProfileRepository.class);
        StorageProfileResolver resolver = mock(StorageProfileResolver.class);
        StorageProfileR2Clients clients = mock(StorageProfileR2Clients.class);
        S3Client client = mock(S3Client.class);
        StorageProfile profile = r2(true);
        ResolvedStorageProfile resolved = resolvedR2();
        when(repository.findById(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(Optional.of(profile));
        doReturn(resolved).when(resolver).validate(profile);
        when(clients.client(resolved)).thenReturn(client);
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        StorageProfileAdminService service = new StorageProfileAdminService(
                repository, resolver, clients, mock(JdbcTemplate.class));

        var result = service.testConnection(StorageProfileCode.PRACTICE_AUTHORING);

        assertThat(result.status()).isEqualTo(ConnectionTestStatus.SUCCESS);
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HeadBucketRequest.class);
        verify(client).headBucket(requestCaptor.capture());
        var timeout = requestCaptor.getValue().overrideConfiguration().orElseThrow();
        assertThat(timeout.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
        assertThat(timeout.apiCallTimeout()).contains(Duration.ofSeconds(8));
    }

    @Test
    void localAndMissingProfilesDoNotOpenAnR2Client() {
        StorageProfileRepository repository = mock(StorageProfileRepository.class);
        StorageProfileResolver resolver = mock(StorageProfileResolver.class);
        StorageProfileR2Clients clients = mock(StorageProfileR2Clients.class);
        StorageProfile local = new StorageProfile(
                StorageProfileCode.PRACTICE_AUTHORING, StorageBackend.LOCAL,
                "", "", "", "", "", "auto", false, 1L);
        when(repository.findById(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(Optional.of(local));
        when(repository.findById(StorageProfileCode.PRACTICE_SPEAKING))
                .thenReturn(Optional.empty());
        StorageProfileAdminService service = new StorageProfileAdminService(
                repository, resolver, clients, mock(JdbcTemplate.class));

        assertThat(service.testConnection(StorageProfileCode.PRACTICE_AUTHORING).status())
                .isEqualTo(ConnectionTestStatus.NOT_APPLICABLE);
        assertThat(service.testConnection(StorageProfileCode.PRACTICE_SPEAKING).status())
                .isEqualTo(ConnectionTestStatus.FAILED);
        verifyNoInteractions(resolver, clients);
    }

    @Test
    void validationAndSdkFailuresReturnSafeMessages() {
        StorageProfileRepository repository = mock(StorageProfileRepository.class);
        StorageProfileResolver resolver = mock(StorageProfileResolver.class);
        StorageProfileR2Clients clients = mock(StorageProfileR2Clients.class);
        StorageProfile profile = r2(true);
        when(repository.findById(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(Optional.of(profile));
        when(resolver.validate(profile))
                .thenThrow(new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE"));
        StorageProfileAdminService service = new StorageProfileAdminService(
                repository, resolver, clients, mock(JdbcTemplate.class));

        var invalid = service.testConnection(StorageProfileCode.PRACTICE_AUTHORING);
        assertThat(invalid.status()).isEqualTo(ConnectionTestStatus.FAILED);
        assertThat(invalid.message()).doesNotContain("STORAGE_PROFILE_UNAVAILABLE");

        ResolvedStorageProfile resolved = resolvedR2();
        S3Client client = mock(S3Client.class);
        doReturn(resolved).when(resolver).validate(profile);
        when(clients.client(resolved)).thenReturn(client);
        doThrow(S3Exception.builder().statusCode(403)
                .message("internal-secret-diagnostic").build())
                .when(client).headBucket(any(HeadBucketRequest.class));

        var denied = service.testConnection(StorageProfileCode.PRACTICE_AUTHORING);
        assertThat(denied.status()).isEqualTo(ConnectionTestStatus.FAILED);
        assertThat(denied.message())
                .contains("quyền truy cập")
                .doesNotContain("internal-secret-diagnostic");
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

    private static ResolvedStorageProfile resolvedR2() {
        return new ResolvedStorageProfile(
                StorageProfileCode.PRACTICE_AUTHORING, StorageBackend.R2,
                "account-1", "access", "top-secret", "private-bucket",
                "https://account-1.r2.cloudflarestorage.com", "auto",
                "practice-authoring", 0L);
    }
}
