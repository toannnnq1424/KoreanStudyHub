package com.ksh.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs non-database side effects at a transaction-safe lifecycle point. */
public final class TransactionLifecycle {

    private TransactionLifecycle() {
    }

    /** Runs after a successful commit, or immediately when no transaction is active. */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
            return;
        }
        action.run();
    }
}
