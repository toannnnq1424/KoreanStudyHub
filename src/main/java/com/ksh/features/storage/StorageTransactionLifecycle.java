package com.ksh.features.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordinates irreversible object-storage side effects with the surrounding
 * database transaction.
 */
public final class StorageTransactionLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(StorageTransactionLifecycle.class);

    private StorageTransactionLifecycle() {
    }

    /** Deletes a newly-created object if the current transaction rolls back. */
    public static void deleteOnRollback(Runnable deleteAction) {
        if (!transactionSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            runSafely(deleteAction, "rollback compensation");
                        }
                    }
                });
    }

    /**
     * Defers destructive deletion until the database transaction commits. When
     * no transaction is active the action runs immediately.
     */
    public static void deleteAfterCommit(Runnable deleteAction) {
        if (!transactionSynchronizationActive()) {
            runSafely(deleteAction, "immediate deletion");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        runSafely(deleteAction, "after-commit deletion");
                    }
                });
    }

    private static boolean transactionSynchronizationActive() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private static void runSafely(Runnable action, String phase) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.error("Object-storage {} failed; cleanup must be retried", phase, ex);
        }
    }
}
