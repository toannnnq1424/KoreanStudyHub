package com.ksh.features.practice.service.storage;

import com.ksh.entities.PracticeStorageMigrationJob;
import com.ksh.entities.PracticeStorageMigrationLogicalType;
import com.ksh.entities.PracticeStorageMigrationStatus;
import com.ksh.features.practice.repository.PracticeStorageMigrationJobRepository;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeStorageMigrationIdentityServiceTest {

    private PracticeStorageMigrationJobRepository repository;
    private PracticeStorageMigrationJobService jobs;
    private JdbcTemplate jdbc;
    private PracticeStorageMigrationIdentityService service;

    @BeforeEach
    void setUp() {
        repository = mock(PracticeStorageMigrationJobRepository.class);
        jobs = mock(PracticeStorageMigrationJobService.class);
        jdbc = mock(JdbcTemplate.class);
        service = new PracticeStorageMigrationIdentityService(repository, jobs, jdbc);
    }

    @Test
    void missingJobFailsWithBoundedCode() {
        when(repository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.switchVerifiedTarget(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STORAGE_MIGRATION_JOB_NOT_FOUND");
        verifyNoInteractions(jobs, jdbc);
    }

    @ParameterizedTest
    @EnumSource(value = PracticeStorageMigrationStatus.class,
            names = {"CLEANUP_PENDING", "DELETING_SOURCE", "COMPLETED"})
    void terminalCleanupStatesAreIdempotent(PracticeStorageMigrationStatus status) {
        PracticeStorageMigrationJob job = job(status,
                PracticeStorageMigrationLogicalType.LECTURER_ASSET, StorageBackend.LOCAL);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        assertThat(service.switchVerifiedTarget(7L)).isFalse();
        verifyNoInteractions(jobs, jdbc);
    }

    @Test
    void unverifiedOrProviderlessJobsDoNotSwitchIdentity() {
        PracticeStorageMigrationJob planned = job(PracticeStorageMigrationStatus.PLANNED,
                PracticeStorageMigrationLogicalType.LECTURER_ASSET, StorageBackend.LOCAL);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(planned));
        assertThat(service.switchVerifiedTarget(7L)).isFalse();

        PracticeStorageMigrationJob providerless = job(
                PracticeStorageMigrationStatus.COPIED_VERIFIED,
                PracticeStorageMigrationLogicalType.LECTURER_ASSET, null);
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(providerless));
        assertThat(service.switchVerifiedTarget(8L)).isFalse();
        verifyNoInteractions(jobs, jdbc);
    }

    @Test
    void lecturerAssetCompareAndSetMarksCleanupOnlyAfterOneRowUpdated() {
        PracticeStorageMigrationJob job = job(
                PracticeStorageMigrationStatus.COPIED_VERIFIED,
                PracticeStorageMigrationLogicalType.LECTURER_ASSET,
                StorageBackend.R2);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

        assertThat(service.switchVerifiedTarget(7L)).isTrue();
        verify(jobs).markLogicalIdentityUpdated(7L);
        verify(jdbc).update(any(String.class), eq("PRACTICE_AUTHORING"),
                eq("R2"), eq("target/key"), eq(91L),
                eq("source/key"), eq("PRACTICE_AUTHORING"), eq(12L),
                eq("a".repeat(64)));
    }

    @Test
    void speakingMediaMapsLocalAndObjectStorageProviders() {
        PracticeStorageMigrationJob local = job(
                PracticeStorageMigrationStatus.COPIED_VERIFIED,
                PracticeStorageMigrationLogicalType.SPEAKING_MEDIA,
                StorageBackend.LOCAL);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(local));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        assertThat(service.switchVerifiedTarget(7L)).isTrue();
        verify(jdbc).update(any(String.class), eq("PRACTICE_SPEAKING"), eq("LOCAL"),
                eq("target/key"), eq(91L), eq("source/key"),
                eq("PRACTICE_SPEAKING"), eq(12L), eq("a".repeat(64)));

        PracticeStorageMigrationJob object = job(
                PracticeStorageMigrationStatus.COPIED_VERIFIED,
                PracticeStorageMigrationLogicalType.SPEAKING_MEDIA,
                StorageBackend.R2);
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(object));
        assertThat(service.switchVerifiedTarget(8L)).isTrue();
        verify(jdbc).update(any(String.class), eq("PRACTICE_SPEAKING"),
                eq("OBJECT_STORAGE"), eq("target/key"), eq(91L),
                eq("source/key"), eq("PRACTICE_SPEAKING"), eq(12L),
                eq("a".repeat(64)));
    }

    @Test
    void compareAndSetConflictAndRetiredLogicalTypeFailClosed() {
        PracticeStorageMigrationJob conflict = job(
                PracticeStorageMigrationStatus.COPIED_VERIFIED,
                PracticeStorageMigrationLogicalType.LECTURER_ASSET,
                StorageBackend.LOCAL);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(conflict));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.switchVerifiedTarget(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STORAGE_MIGRATION_LOGICAL_IDENTITY_CONFLICT");
        verify(jobs, never()).markLogicalIdentityUpdated(7L);

        PracticeStorageMigrationJob retired = job(
                PracticeStorageMigrationStatus.COPIED_VERIFIED,
                PracticeStorageMigrationLogicalType.PDF_IMPORT_SESSION,
                StorageBackend.LOCAL);
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(retired));
        assertThatThrownBy(() -> service.switchVerifiedTarget(8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PDF_IMPORT_SESSION_MIGRATION_RETIRED");
    }

    @Test
    void logicalTypeProfileMismatchFailsBeforeSql() {
        PracticeStorageMigrationJob job = job(
                PracticeStorageMigrationStatus.COPIED_VERIFIED,
                PracticeStorageMigrationLogicalType.LECTURER_ASSET,
                StorageBackend.LOCAL);
        when(job.getTargetProfileCode()).thenReturn(StorageProfileCode.PRACTICE_SPEAKING);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        assertThatThrownBy(() -> service.switchVerifiedTarget(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STORAGE_MIGRATION_PROFILE_INVALID");
        verifyNoInteractions(jdbc, jobs);
    }

    private static PracticeStorageMigrationJob job(
            PracticeStorageMigrationStatus status,
            PracticeStorageMigrationLogicalType type,
            StorageBackend targetProvider) {
        PracticeStorageMigrationJob job = mock(PracticeStorageMigrationJob.class);
        StorageProfileCode profile = type.requiredProfile();
        when(job.getStatus()).thenReturn(status);
        when(job.getLogicalType()).thenReturn(type);
        when(job.getLogicalId()).thenReturn(91L);
        when(job.getSourceProfileCode()).thenReturn(profile);
        when(job.getTargetProfileCode()).thenReturn(profile);
        when(job.getSourceStorageKey()).thenReturn("source/key");
        when(job.getTargetStorageKey()).thenReturn("target/key");
        when(job.getTargetStorageProvider()).thenReturn(targetProvider);
        when(job.getExpectedSize()).thenReturn(12L);
        when(job.getExpectedSha256()).thenReturn("a".repeat(64));
        return job;
    }
}
