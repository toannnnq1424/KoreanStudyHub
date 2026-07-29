package com.ksh.features.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StorageTransactionLifecycleTest {

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void newObjectIsDeletedOnRollbackButNotCommit() {
        beginTransaction();
        AtomicInteger deletes = new AtomicInteger();
        StorageTransactionLifecycle.deleteOnRollback(deletes::incrementAndGet);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(deletes).hasValue(1);
    }

    @Test
    void oldObjectIsDeletedOnlyAfterCommit() {
        beginTransaction();
        AtomicInteger deletes = new AtomicInteger();
        StorageTransactionLifecycle.deleteAfterCommit(deletes::incrementAndGet);

        assertThat(deletes).hasValue(0);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(deletes).hasValue(1);
    }

    @Test
    void destructiveDeleteDoesNotRunWhenTransactionRollsBack() {
        beginTransaction();
        AtomicInteger deletes = new AtomicInteger();
        StorageTransactionLifecycle.deleteAfterCommit(deletes::incrementAndGet);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(deletes).hasValue(0);
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void complete(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(status));
    }
}
