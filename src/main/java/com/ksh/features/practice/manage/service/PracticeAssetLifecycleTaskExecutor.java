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
            storageService.delete(claim.storageProfileCode(), claim.storageKey());
            boolean stillExists = storageService.exists(
                    claim.storageProfileCode(), claim.storageKey());
            if (stillExists) {
                throw new java.io.IOException("Physical asset deletion was not confirmed.");
            }
            transactions.complete(claim);
        } catch (Exception exception) {
            transactions.retry(claim, exception);
        }
    }
}
