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
            if (claim.storageProfileCode() == null) {
                storageService.delete(claim.storageKey());
            } else {
                storageService.delete(claim.storageProfileCode(), claim.storageKey());
            }
            boolean stillExists = claim.storageProfileCode() == null
                    ? storageService.exists(claim.storageKey())
                    : storageService.exists(claim.storageProfileCode(), claim.storageKey());
            if (stillExists) {
                throw new java.io.IOException("Physical asset deletion was not confirmed.");
            }
            transactions.complete(claim);
        } catch (Exception exception) {
            transactions.retry(claim, exception);
        }
    }
}
