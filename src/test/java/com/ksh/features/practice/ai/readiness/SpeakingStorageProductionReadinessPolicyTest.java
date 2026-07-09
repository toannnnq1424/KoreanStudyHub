package com.ksh.features.practice.ai.readiness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingStorageProductionReadinessPolicyTest {

    private final SpeakingStorageProductionReadinessPolicy policy = new SpeakingStorageProductionReadinessPolicy();

    @Test
    void localPrivateStorageIsAcceptedForDevSingleNodeStagingOnly() {
        AiReadinessReport report = policy.assess(SpeakingStorageRolloutDecision.devSingleNodeLocal());

        assertThat(report.blockers()).isEmpty();
        assertThat(report.issues())
                .extracting(AiReadinessIssue::code)
                .contains("LOCAL_PRIVATE_ACCEPTED_FOR_DEV_SINGLE_NODE_STAGING");
    }

    @Test
    void localPrivateStorageBlocksLiveProductionRollout() {
        AiReadinessReport report = policy.assess(new SpeakingStorageRolloutDecision(
                true, SpeakingStorageMode.LOCAL_PRIVATE, true, true, false));

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("LOCAL_PRIVATE_STORAGE_NOT_PRODUCTION_FINAL",
                        "OBJECT_STORAGE_DECISION_MISSING");
    }

    @Test
    void multiNodeLocalStorageIsUnsafeForLearnerMedia() {
        AiReadinessReport report = policy.assess(new SpeakingStorageRolloutDecision(
                true, SpeakingStorageMode.LOCAL_PRIVATE, false, true, false));

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("MULTI_NODE_LOCAL_STORAGE_UNSAFE");
    }

    @Test
    void objectStorageWithCleanupReadyPassesStorageGate() {
        AiReadinessReport report = policy.assess(SpeakingStorageRolloutDecision.liveObjectStorageReady());

        assertThat(report.blockers()).isEmpty();
        assertThat(report.issues())
                .extracting(AiReadinessIssue::code)
                .contains("OBJECT_STORAGE_PATH_READY_FOR_ROLLOUT_GATE");
    }

    @Test
    void liveRolloutRequiresCleanupReadiness() {
        AiReadinessReport report = policy.assess(new SpeakingStorageRolloutDecision(
                true, SpeakingStorageMode.OBJECT_STORAGE, false, false, true));

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("CLEANUP_WORKER_NOT_READY_FOR_PRODUCTION");
    }
}
