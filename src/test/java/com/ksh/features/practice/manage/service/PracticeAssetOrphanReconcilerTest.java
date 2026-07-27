package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAssetOrphanReconcilerTest {

    private final LecturerAssetRepository assets =
            mock(LecturerAssetRepository.class);
    private final PracticeAssetLifecycleTaskRepository tasks =
            mock(PracticeAssetLifecycleTaskRepository.class);
    private final PracticeAssetReferenceGuard guard =
            mock(PracticeAssetReferenceGuard.class);
    private final PracticeAssetOrphanReconciler reconciler =
            new PracticeAssetOrphanReconciler(assets, tasks, guard);

    @Test
    void expiredUnboundPrivateUploadIsMarkedBeforeDeleteIsQueued() {
        LecturerAsset asset = stagedAsset(LocalDateTime.now().minusHours(1));
        when(assets.findByIdForUpdate(9L)).thenReturn(Optional.of(asset));

        assertTrue(invokeEnqueueOne(9L));
        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETION_PENDING", asset.getStatus());
        verify(tasks).save(
                org.mockito.ArgumentMatchers.any(PracticeAssetLifecycleTask.class));
    }

    @Test
    void expiredUnboundGeneratedCandidateUsesSameLockedRetentionBoundary() {
        LecturerAsset asset = stagedAsset(
                LocalDateTime.now().minusHours(1), "AI_TTS");
        when(assets.findByIdForUpdate(9L)).thenReturn(Optional.of(asset));

        assertTrue(invokeEnqueueOne(9L));

        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETION_PENDING", asset.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(asset.getDeletedAt());
        verify(assets).findByIdForUpdate(9L);
        verify(guard).isRetained(9L);
        verify(tasks).save(
                org.mockito.ArgumentMatchers.argThat(task ->
                        PracticeAssetLifecycleTask.DELETE.equals(
                                task.getOperation())
                                && Long.valueOf(9L).equals(
                                        task.getAssetId())));
    }

    @Test
    void retentionDoesNotExpandBeyondManualUploadAndGeneratedCandidate() {
        LecturerAsset asset = stagedAsset(
                LocalDateTime.now().minusHours(1), "PDF_REGION");
        when(assets.findByIdForUpdate(9L)).thenReturn(Optional.of(asset));

        assertFalse(invokeEnqueueOne(9L));

        org.junit.jupiter.api.Assertions.assertEquals(
                "TEMPORARY", asset.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(asset.getDeletedAt());
        verify(guard, never()).isRetained(9L);
        verify(assets, never()).save(asset);
        verify(tasks, never()).save(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activeOrRetainedAssetNeverBecomesOrphanCandidate() {
        LecturerAsset asset = stagedAsset(LocalDateTime.now().minusHours(1));
        when(assets.findByIdForUpdate(9L)).thenReturn(Optional.of(asset));
        when(guard.isRetained(9L)).thenReturn(true);

        assertFalse(invokeEnqueueOne(9L));
        org.junit.jupiter.api.Assertions.assertEquals(
                "TEMPORARY", asset.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(
                asset.getDeletedAt());
        verify(assets, never()).save(asset);
        verify(tasks, never()).save(
                org.mockito.ArgumentMatchers.any());
    }

    private boolean invokeEnqueueOne(Long assetId) {
        try {
            java.lang.reflect.Method method =
                    PracticeAssetOrphanReconciler.class.getDeclaredMethod(
                            "enqueueOne", Long.class);
            method.setAccessible(true);
            return (boolean) method.invoke(reconciler, assetId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static LecturerAsset stagedAsset(LocalDateTime retentionUntil) {
        return stagedAsset(retentionUntil, "MANUAL_UPLOAD");
    }

    private static LecturerAsset stagedAsset(
            LocalDateTime retentionUntil,
            String sourceType) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(9L);
        asset.setStatus("TEMPORARY");
        asset.setVisibility("PRIVATE");
        asset.setSourceType(sourceType);
        asset.setStorageKey("private/upload.mp3");
        asset.setRetentionUntil(retentionUntil);
        return asset;
    }
}
