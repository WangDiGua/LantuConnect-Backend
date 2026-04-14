package com.lantu.connect.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a callback after the current transaction commits, or immediately when no
 * transactional context is active.
 */
public final class TransactionCommitExecutor {

    private TransactionCommitExecutor() {
    }

    public static void runAfterCommitOrNow(Runnable task) {
        if (task == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
