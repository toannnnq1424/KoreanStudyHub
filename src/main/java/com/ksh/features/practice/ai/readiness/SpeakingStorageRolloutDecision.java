package com.ksh.features.practice.ai.readiness;

public record SpeakingStorageRolloutDecision(
        boolean liveRollout,
        SpeakingStorageMode storageMode,
        boolean singleNodeDeployment,
        boolean cleanupWorkerEnabled,
        boolean objectStorageProviderSelected
) {
    public SpeakingStorageRolloutDecision {
        if (storageMode == null) {
            throw new IllegalArgumentException("storageMode is required");
        }
    }

    public static SpeakingStorageRolloutDecision devSingleNodeLocal() {
        return new SpeakingStorageRolloutDecision(false, SpeakingStorageMode.LOCAL_PRIVATE,
                true, false, false);
    }

    public static SpeakingStorageRolloutDecision liveObjectStorageReady() {
        return new SpeakingStorageRolloutDecision(true, SpeakingStorageMode.OBJECT_STORAGE,
                false, true, true);
    }
}
