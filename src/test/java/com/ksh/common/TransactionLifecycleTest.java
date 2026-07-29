package com.ksh.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionLifecycleTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void actionRunsOnlyAfterCommit() {
        AtomicInteger calls = new AtomicInteger();
        beginTransaction();

        TransactionLifecycle.afterCommit(calls::incrementAndGet);
        assertEquals(0, calls.get());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertEquals(1, calls.get());
    }

    @Test
    void rollbackDoesNotRunAction() {
        AtomicInteger calls = new AtomicInteger();
        beginTransaction();

        TransactionLifecycle.afterCommit(calls::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertEquals(0, calls.get());
    }

    @Test
    void actionRunsImmediatelyWithoutTransaction() {
        AtomicInteger calls = new AtomicInteger();
        TransactionLifecycle.afterCommit(calls::incrementAndGet);
        assertEquals(1, calls.get());
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
