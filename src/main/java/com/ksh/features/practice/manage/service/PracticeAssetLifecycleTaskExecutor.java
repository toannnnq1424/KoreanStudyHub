package com.ksh.features.practice.manage.service;

import org.springframework.stereotype.Service;

@Service
public class PracticeAssetLifecycleTaskExecutor {

    private final PracticeAssetLifecycleTaskTransactions transactions;
    private final AssetStorageService storageService;

    public PracticeAssetLifecycleTaskExecutor(
            PracticeAssetLifecycleTaskTransactions transactions,
            AssetStorageService storageService) {
        this.transactions = transactions;
        this.storageService = storageService;
    }

    public void processOne(Long taskId) {
        PracticeAssetLifecycleTaskTransactions.ClaimedDelete claim =
                transactions.claim(taskId);
        if (claim == null) return;
        try {
            if (!transactions.confirmPhysicalDeleteAllowed(claim)) {
                return;
            }
            storageService.delete(claim.storageKey());
            transactions.complete(claim);
        } catch (Exception exception) {
            transactions.retry(claim, exception);
        }
    }
}
