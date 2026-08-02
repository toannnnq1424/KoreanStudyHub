package com.ksh.features.storage.profile;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageProfileResolverTest {

    @Test
    void authorityContainsExactlyThreeFixedProfiles() {
        assertThat(StorageProfileCode.values()).containsExactly(
                StorageProfileCode.GENERAL_UPLOADS,
                StorageProfileCode.PRACTICE_AUTHORING,
                StorageProfileCode.PRACTICE_SPEAKING);
        assertThat(StorageProfileCode.PRACTICE_AUTHORING.fixedKeyPrefix())
                .isEqualTo("practice-authoring");
    }

    @Test
    void writesRequireEnabledButRollbackReadsRetainExactDisabledProfile() {
        StorageProfileRepository repository = mock(StorageProfileRepository.class);
        StorageProfile disabled = local(StorageProfileCode.PRACTICE_AUTHORING, false);
        when(repository.findById(StorageProfileCode.PRACTICE_AUTHORING))
                .thenReturn(Optional.of(disabled));
        StorageProfileResolver resolver = new StorageProfileResolver(repository, true);

        assertThatThrownBy(() -> resolver.resolveForWrite(StorageProfileCode.PRACTICE_AUTHORING))
                .isInstanceOf(StorageProfileException.class)
                .hasMessage("STORAGE_PROFILE_UNAVAILABLE");
        assertThat(resolver.resolveForRead(StorageProfileCode.PRACTICE_AUTHORING).profileCode())
                .isEqualTo(StorageProfileCode.PRACTICE_AUTHORING);
    }

    @Test
    void productionRejectsLocalAndIncompleteR2FailClosed() {
        StorageProfileResolver production = new StorageProfileResolver(mock(StorageProfileRepository.class), false);

        assertThatThrownBy(() -> production.validate(
                local(StorageProfileCode.PRACTICE_SPEAKING, true)))
                .isInstanceOf(StorageProfileException.class)
                .hasMessage("STORAGE_PROFILE_UNAVAILABLE");

        StorageProfile incomplete = new StorageProfile(
                StorageProfileCode.PRACTICE_SPEAKING, StorageBackend.R2,
                "account-1", "access", "", "private-bucket",
                "https://account-1.r2.cloudflarestorage.com", "auto", true, 7L);
        assertThatThrownBy(() -> production.validate(incomplete))
                .isInstanceOf(StorageProfileException.class)
                .hasMessage("STORAGE_PROFILE_UNAVAILABLE");
    }

    @Test
    void completeEnabledR2ResolvesWithoutProviderCall() {
        StorageProfileRepository repository = mock(StorageProfileRepository.class);
        StorageProfile complete = new StorageProfile(
                StorageProfileCode.PRACTICE_SPEAKING, StorageBackend.R2,
                "account-1", "access", "secret", "private-bucket",
                "https://account-1.r2.cloudflarestorage.com", "auto", true, 7L);
        when(repository.findById(StorageProfileCode.PRACTICE_SPEAKING))
                .thenReturn(Optional.of(complete));

        ResolvedStorageProfile resolved = new StorageProfileResolver(repository, false)
                .resolveForWrite(StorageProfileCode.PRACTICE_SPEAKING);

        assertThat(resolved.backend()).isEqualTo(StorageBackend.R2);
        assertThat(resolved.keyPrefix()).isEqualTo("practice-speaking");
    }

    @Test
    void fixedPrefixesAndObjectKeysRejectEscapeAliasAndCaseDrift() {
        assertThatThrownBy(() -> StorageProfileResolver.requireFixedPrefix(
                StorageProfileCode.PRACTICE_AUTHORING, "general-uploads"))
                .isInstanceOf(StorageProfileException.class);
        for (String key : new String[]{"../secret", "/absolute", "a//b", "a/./b",
                "a/../b", "A/upper", "a\\b", " a/b", "c:/windows", "a:b"}) {
            assertThatThrownBy(() -> StorageProfileResolver.requireSafeObjectKey(key))
                    .as(key)
                    .isInstanceOf(StorageProfileException.class)
                    .hasMessage("STORAGE_IDENTITY_INVALID");
        }
        assertThat(StorageProfileResolver.requireSafeObjectKey("a/b-1.bin"))
                .isEqualTo("a/b-1.bin");
    }

    private static StorageProfile local(StorageProfileCode code, boolean enabled) {
        return new StorageProfile(code, StorageBackend.LOCAL,
                "", "", null, "", "", "auto", enabled, 1L);
    }
}
