package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PracticeAssetLifecycleTaskTransactionsTest {

    @Test
    void profileCodedCleanupLocksOnlyTheExactProfileAndKey() throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = new PracticeAssetLifecycleTask(
                9L, "PRACTICE_AUTHORING", PracticeAssetLifecycleTask.DELETE,
                "lecturer-assets/shared.png", null);
        set(task, "id", 1L);
        LecturerAsset asset = asset(9L, "lecturer-assets/shared.png");
        asset.setStorageProfileCode("PRACTICE_AUTHORING");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageProfileCodeAndStorageKeyForUpdate(
                "PRACTICE_AUTHORING", "lecturer-assets/shared.png"))
                .thenReturn(List.of(asset));
        when(guard.isRetained(9L)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertNotNull(transactions.claim(1L));

        verify(assets).findByStorageProfileCodeAndStorageKeyForUpdate(
                "PRACTICE_AUTHORING", "lecturer-assets/shared.png");
        verify(assets, never()).findByStorageKeyForUpdate(
                "lecturer-assets/shared.png");
    }

    @Test
    void legacyLifecycleReservationQueryCannotMatchProfileCodedTasks()
            throws Exception {
        org.springframework.data.jpa.repository.Query query =
                PracticeAssetLifecycleTaskRepository.class
                        .getMethod("findActiveBySourceStorageKeyForUpdate", String.class)
                        .getAnnotation(org.springframework.data.jpa.repository.Query.class);

        org.junit.jupiter.api.Assertions.assertNotNull(query);
        org.junit.jupiter.api.Assertions.assertTrue(query.value()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("t.storageprofilecode is null"));
    }

    @Test
    void dueQueueOrdersByEligibilityBeforeStableIdToPreventDeferralStarvation()
            throws Exception {
        org.springframework.data.jpa.repository.Query query =
                PracticeAssetLifecycleTaskRepository.class
                        .getMethod(
                                "findDueIds",
                                LocalDateTime.class,
                                org.springframework.data.domain.Pageable.class)
                        .getAnnotation(
                                org.springframework.data.jpa.repository.Query.class);
        org.junit.jupiter.api.Assertions.assertNotNull(query);
        String normalized = query.value()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);

        org.junit.jupiter.api.Assertions.assertTrue(normalized.contains(
                "order by t.nextattemptat asc, t.id asc"));
    }

    @Test
    void immutableOrCurrentReferenceCancelsPhysicalEligibilityUnderAssetLock()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/audio.mp3");
        LecturerAsset asset = asset("private/audio.mp3");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/audio.mp3"))
                .thenReturn(List.of(asset));
        when(guard.isRetained(9L)).thenReturn(true);

        assertNull(transactions.claim(1L));

        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETION_PENDING", asset.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING", task.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(task.getNextAttemptAt());
        verify(assets).findByStorageKeyForUpdate("private/audio.mp3");
        verify(assets, never()).save(asset);
        verify(tasks).save(task);
    }

    @Test
    void finalDeleteRecheckLeavesNewlyRetainedAssetStateUnchanged()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/audio.mp3");
        task.markRunning(
                "claim-a",
                LocalDateTime.now().plusMinutes(5));
        LecturerAsset asset = asset("private/audio.mp3");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/audio.mp3"))
                .thenReturn(List.of(asset));
        when(guard.isRetained(9L)).thenReturn(true);
        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                new PracticeAssetLifecycleTaskTransactions.ClaimedDelete(
                        1L,
                        9L,
                        PracticeAssetLifecycleTask.DELETE,
                        "private/audio.mp3",
                        "claim-a");

        org.junit.jupiter.api.Assertions.assertFalse(
                transactions.confirmPhysicalDeleteAllowed(claim));

        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETION_PENDING", asset.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING", task.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(task.getNextAttemptAt());
        verify(assets, never()).save(asset);
        verify(tasks).save(task);
    }

    @Test
    void mismatchedStorageKeyFailsClosedWithoutPhysicalClaim()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/stale.mp3");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/stale.mp3"))
                .thenReturn(List.of());

        assertNull(transactions.claim(1L));
        org.junit.jupiter.api.Assertions.assertEquals(
                "COMPLETED", task.getStatus());
    }

    @Test
    void freshRunningLeaseCannotBeClaimedByCompetingWorker()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/audio.mp3");
        set(task, "status", "RUNNING");
        set(task, "claimToken", "claim-a");
        set(task, "nextAttemptAt", LocalDateTime.now().plusMinutes(5));
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));

        assertNull(transactions.claim(1L));

        verify(assets, never()).findByStorageKeyForUpdate(
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void staleRunningLeaseGetsNewTokenAndOldWorkerCannotFinalize()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/audio.mp3");
        LecturerAsset asset = asset("private/audio.mp3");
        set(task, "status", "RUNNING");
        set(task, "claimToken", "old-claim");
        set(task, "nextAttemptAt", LocalDateTime.now().minusMinutes(1));
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/audio.mp3"))
                .thenReturn(List.of(asset));

        PracticeAssetLifecycleTaskTransactions.ClaimedDelete reclaimed =
                transactions.claim(1L);

        org.junit.jupiter.api.Assertions.assertNotNull(reclaimed);
        org.junit.jupiter.api.Assertions.assertNotEquals(
                "old-claim", reclaimed.claimToken());
        assertNull(transactions.claim(1L));
        transactions.complete(
                new PracticeAssetLifecycleTaskTransactions.ClaimedDelete(
                        1L,
                        9L,
                        PracticeAssetLifecycleTask.DELETE,
                        "private/audio.mp3",
                        "old-claim"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "RUNNING", task.getStatus());
    }

    @Test
    void assetDeleteClaimStopsForRetainedSiblingSharingPhysicalKey()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard =
                mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/shared.mp3");
        LecturerAsset candidate = asset(9L, "private/shared.mp3");
        LecturerAsset retainedSibling = asset(10L, "private/shared.mp3");
        retainedSibling.setStatus("DELETED");
        retainedSibling.setDeletedAt(LocalDateTime.now());
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/shared.mp3"))
                .thenReturn(List.of(candidate, retainedSibling));
        when(guard.isRetained(9L)).thenReturn(false);
        when(guard.isRetained(10L)).thenReturn(true);

        assertNull(transactions.claim(1L));

        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETION_PENDING", candidate.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETED", retainedSibling.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING", task.getStatus());
        verify(assets, never()).save(candidate);
        verify(assets, never()).save(retainedSibling);
        verify(tasks).save(task);
    }

    @Test
    void assetDeleteClaimStopsForActiveSiblingSharingPhysicalKey()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard =
                mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/shared.mp3");
        LecturerAsset candidate = asset(9L, "private/shared.mp3");
        LecturerAsset activeSibling = asset(10L, "private/shared.mp3");
        activeSibling.setStatus("ACTIVE");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/shared.mp3"))
                .thenReturn(List.of(candidate, activeSibling));
        when(guard.isRetained(9L)).thenReturn(false);
        when(guard.isRetained(10L)).thenReturn(false);

        assertNull(transactions.claim(1L));

        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETION_PENDING", candidate.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "ACTIVE", activeSibling.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING", task.getStatus());
        verify(assets, never()).save(candidate);
        verify(assets, never()).save(activeSibling);
        verify(tasks).save(task);
    }

    @Test
    void finalDeleteRecheckStopsForLateRetainedSiblingSharingPhysicalKey()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard =
                mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/shared.mp3");
        task.markRunning(
                "claim-a",
                LocalDateTime.now().plusMinutes(5));
        LecturerAsset candidate = asset(9L, "private/shared.mp3");
        LecturerAsset retainedSibling = asset(10L, "private/shared.mp3");
        retainedSibling.setStatus("DELETED");
        retainedSibling.setDeletedAt(LocalDateTime.now());
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/shared.mp3"))
                .thenReturn(List.of(candidate, retainedSibling));
        when(guard.isRetained(9L)).thenReturn(false);
        when(guard.isRetained(10L)).thenReturn(true);
        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                new PracticeAssetLifecycleTaskTransactions.ClaimedDelete(
                        1L,
                        9L,
                        PracticeAssetLifecycleTask.DELETE,
                        "private/shared.mp3",
                        "claim-a");

        org.junit.jupiter.api.Assertions.assertFalse(
                transactions.confirmPhysicalDeleteAllowed(claim));

        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING", task.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETION_PENDING", candidate.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETED", retainedSibling.getStatus());
        verify(assets, never()).save(candidate);
        verify(assets, never()).save(retainedSibling);
        verify(tasks).save(task);
    }

    @Test
    void blockedCandidateBecomesDeletableAfterSiblingMovesToFreshKey()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard =
                mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task("private/shared.mp3");
        LecturerAsset candidate = asset(9L, "private/shared.mp3");
        LecturerAsset sibling = asset(10L, "private/shared.mp3");
        sibling.setStatus("TEMPORARY");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/shared.mp3"))
                .thenReturn(
                        List.of(candidate, sibling),
                        List.of(candidate),
                        List.of(candidate));
        when(assets.findByIdForUpdate(9L))
                .thenReturn(Optional.of(candidate));
        when(guard.isRetained(9L)).thenReturn(false);
        when(guard.isRetained(10L)).thenReturn(false);

        assertNull(transactions.claim(1L));
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING", task.getStatus());

        sibling.setStorageKey("private/fresh/sibling.mp3");
        sibling.setStatus("ACTIVE");
        set(task, "nextAttemptAt", LocalDateTime.now().minusMinutes(1));

        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                transactions.claim(1L);
        org.junit.jupiter.api.Assertions.assertNotNull(claim);
        org.junit.jupiter.api.Assertions.assertTrue(
                transactions.confirmPhysicalDeleteAllowed(claim));
        transactions.complete(claim);

        org.junit.jupiter.api.Assertions.assertEquals(
                "COMPLETED", task.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETED", candidate.getStatus());
        verify(assets).save(candidate);
    }

    @Test
    void delayedOrphanCleanupStopsWhenStorageKeyHasCurrentAsset()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task(
                null,
                PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                "private/reused.mp3");
        LecturerAsset current = asset("private/reused.mp3");
        current.setStatus("ACTIVE");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/reused.mp3"))
                .thenReturn(List.of(current));

        assertNull(transactions.claim(1L));

        org.junit.jupiter.api.Assertions.assertEquals(
                "COMPLETED", task.getStatus());
        verify(assets).findByStorageKeyForUpdate("private/reused.mp3");
    }

    @Test
    void preDeleteConfirmationClosesReuseAfterNonAssetClaim()
            throws Exception {
        PracticeAssetLifecycleTaskRepository tasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeAssetReferenceGuard guard = mock(PracticeAssetReferenceGuard.class);
        PracticeAssetLifecycleTaskTransactions transactions =
                new PracticeAssetLifecycleTaskTransactions(tasks, assets, guard);
        PracticeAssetLifecycleTask task = task(
                null,
                PracticeAssetLifecycleTask.PROMOTE_CLEANUP,
                "private/reused.mp3");
        LecturerAsset current = asset("private/reused.mp3");
        current.setStatus("TEMPORARY");
        when(tasks.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(assets.findByStorageKeyForUpdate("private/reused.mp3"))
                .thenReturn(List.of(), List.of(current));

        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                transactions.claim(1L);

        org.junit.jupiter.api.Assertions.assertNotNull(claim);
        org.junit.jupiter.api.Assertions.assertFalse(
                transactions.confirmPhysicalDeleteAllowed(claim));
        org.junit.jupiter.api.Assertions.assertEquals(
                "COMPLETED", task.getStatus());
    }

    private static PracticeAssetLifecycleTask task(String key)
            throws Exception {
        return task(9L, PracticeAssetLifecycleTask.DELETE, key);
    }

    private static PracticeAssetLifecycleTask task(
            Long assetId, String operation, String key) throws Exception {
        PracticeAssetLifecycleTask task = new PracticeAssetLifecycleTask(
                assetId, operation, key, null);
        Field id = PracticeAssetLifecycleTask.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(task, 1L);
        return task;
    }

    private static void set(
            Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static LecturerAsset asset(String key) {
        return asset(9L, key);
    }

    private static LecturerAsset asset(Long id, String key) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(id);
        asset.setStatus("DELETION_PENDING");
        asset.setStorageKey(key);
        return asset;
    }
}
