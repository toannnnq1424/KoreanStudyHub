package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAssetLifecycleTaskExecutorTest {

    private final PracticeAssetLifecycleTaskRepository taskRepository =
            mock(PracticeAssetLifecycleTaskRepository.class);
    private final LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
    private final PracticeMaterialReferenceService referenceService =
            mock(PracticeMaterialReferenceService.class);
    private final AssetStorageService storageService = mock(AssetStorageService.class);

    private PracticeAssetLifecycleTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PracticeAssetLifecycleTaskExecutor(
                taskRepository, assetRepository, referenceService, storageService);
    }

    @Test
    void deleteTaskRemovesStorageAndMarksAssetDeleted() throws Exception {
        PracticeAssetLifecycleTask task = task(1L, PracticeAssetLifecycleTask.DELETE);
        LecturerAsset asset = new LecturerAsset();
        asset.setId(9L);
        asset.setStatus("DELETION_PENDING");
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(referenceService.hasAnyReference(9L)).thenReturn(false);
        when(assetRepository.findById(9L)).thenReturn(Optional.of(asset));

        executor.processOne(1L);

        verify(storageService).delete("private/source.bin");
        assertEquals("COMPLETED", task.getStatus());
        assertEquals("DELETED", asset.getStatus());
        assertNotNull(asset.getDeletedAt());
    }

    @Test
    void referencedDeleteIsCancelledAndAssetRemainsArchived() throws Exception {
        PracticeAssetLifecycleTask task = task(1L, PracticeAssetLifecycleTask.DELETE);
        LecturerAsset asset = new LecturerAsset();
        asset.setId(9L);
        asset.setStatus("DELETION_PENDING");
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(referenceService.hasAnyReference(9L)).thenReturn(true);
        when(assetRepository.findById(9L)).thenReturn(Optional.of(asset));

        executor.processOne(1L);

        assertEquals("COMPLETED", task.getStatus());
        assertEquals(0, task.getAttemptCount());
        assertEquals("ARCHIVED", asset.getStatus());
        verify(storageService, never()).delete("private/source.bin");
    }

    @Test
    void storageFailureSchedulesBoundedRetry() throws Exception {
        PracticeAssetLifecycleTask task = task(1L, PracticeAssetLifecycleTask.ORPHAN_RECONCILE);
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        org.mockito.Mockito.doThrow(new IOException("storage unavailable"))
                .when(storageService).delete("private/source.bin");

        executor.processOne(1L);

        assertEquals("PENDING", task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertEquals("storage unavailable", task.getLastError());
        assertNotNull(task.getNextAttemptAt());
    }

    @Test
    void completedTaskIsIdempotentlyIgnored() throws Exception {
        PracticeAssetLifecycleTask task = task(1L, PracticeAssetLifecycleTask.DELETE);
        task.markCompleted();
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));

        executor.processOne(1L);

        verify(storageService, never()).delete("private/source.bin");
        verify(taskRepository, never()).saveAndFlush(task);
    }

    private static PracticeAssetLifecycleTask task(Long id, String operation) throws Exception {
        PracticeAssetLifecycleTask task = new PracticeAssetLifecycleTask(
                9L, operation, "private/source.bin", null);
        Field field = PracticeAssetLifecycleTask.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(task, id);
        return task;
    }
}
