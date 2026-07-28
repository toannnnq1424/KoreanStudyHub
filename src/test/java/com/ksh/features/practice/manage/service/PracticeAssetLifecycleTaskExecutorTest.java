package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAssetLifecycleTaskExecutorTest {

    private final PracticeAssetLifecycleTaskTransactions transactions =
            mock(PracticeAssetLifecycleTaskTransactions.class);
    private final AssetStorageService storageService = mock(AssetStorageService.class);
    private PracticeAssetLifecycleTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PracticeAssetLifecycleTaskExecutor(
                transactions, storageService);
    }

    @Test
    void claimedStorageDeleteRunsBetweenClaimAndFinalizeBoundaries()
            throws Exception {
        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                new PracticeAssetLifecycleTaskTransactions.ClaimedDelete(
                        1L, 9L, "DELETE", "private/source.bin", "claim-1");
        when(transactions.claim(1L)).thenReturn(claim);
        when(transactions.confirmPhysicalDeleteAllowed(claim))
                .thenReturn(true);

        executor.processOne(1L);

        verify(transactions).claim(1L);
        verify(storageService).delete("private/source.bin");
        verify(transactions).complete(claim);
        verify(transactions, never()).retry(
                org.mockito.ArgumentMatchers.eq(claim),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void storageFailureIsReturnedToBoundedRetryTransaction()
            throws Exception {
        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                new PracticeAssetLifecycleTaskTransactions.ClaimedDelete(
                        1L, null, "ORPHAN_RECONCILE", "private/source.bin",
                        "claim-1");
        IOException failure = new IOException("storage unavailable");
        when(transactions.claim(1L)).thenReturn(claim);
        when(transactions.confirmPhysicalDeleteAllowed(claim))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(failure)
                .when(storageService).delete("private/source.bin");

        executor.processOne(1L);

        verify(transactions).retry(claim, failure);
        verify(transactions, never()).complete(claim);
    }

    @Test
    void nonClaimableOrReferenceRetainedTaskDoesNoPhysicalIo()
            throws Exception {
        when(transactions.claim(1L)).thenReturn(null);

        executor.processOne(1L);

        verify(storageService, never()).delete(
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void storageKeyReusedAfterClaimDoesNoPhysicalIo() throws Exception {
        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                new PracticeAssetLifecycleTaskTransactions.ClaimedDelete(
                        1L,
                        null,
                        "ORPHAN_RECONCILE",
                        "private/reused.bin",
                        "claim-1");
        when(transactions.claim(1L)).thenReturn(claim);
        when(transactions.confirmPhysicalDeleteAllowed(claim))
                .thenReturn(false);

        executor.processOne(1L);

        verify(storageService, never()).delete(
                org.mockito.ArgumentMatchers.anyString());
        verify(transactions, never()).complete(claim);
        verify(transactions, never()).retry(
                org.mockito.ArgumentMatchers.eq(claim),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retainedSiblingAfterAssetClaimDoesNoPhysicalIo() throws Exception {
        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                new PracticeAssetLifecycleTaskTransactions.ClaimedDelete(
                        1L,
                        9L,
                        "DELETE",
                        "private/shared.bin",
                        "claim-1");
        when(transactions.claim(1L)).thenReturn(claim);
        when(transactions.confirmPhysicalDeleteAllowed(claim))
                .thenReturn(false);

        executor.processOne(1L);

        verify(storageService, never()).delete(
                org.mockito.ArgumentMatchers.anyString());
        verify(transactions, never()).complete(claim);
        verify(transactions, never()).retry(
                org.mockito.ArgumentMatchers.eq(claim),
                org.mockito.ArgumentMatchers.any());
    }
}
