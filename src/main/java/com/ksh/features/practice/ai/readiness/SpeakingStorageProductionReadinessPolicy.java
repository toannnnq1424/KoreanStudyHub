package com.ksh.features.practice.ai.readiness;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpeakingStorageProductionReadinessPolicy {

    public AiReadinessReport assess(SpeakingStorageRolloutDecision decision) {
        SpeakingStorageRolloutDecision safe = decision == null
                ? new SpeakingStorageRolloutDecision(true, SpeakingStorageMode.LOCAL_PRIVATE,
                false, false, false)
                : decision;
        List<AiReadinessIssue> issues = new ArrayList<>();

        if (!safe.liveRollout()) {
            if (safe.storageMode() == SpeakingStorageMode.LOCAL_PRIVATE && safe.singleNodeDeployment()) {
                issues.add(AiReadinessIssue.info(
                        "LOCAL_PRIVATE_ACCEPTED_FOR_DEV_SINGLE_NODE_STAGING",
                        "Local private storage được chấp nhận cho dev/single-node staging, không phải production final.",
                        "8F-D"));
            }
            return new AiReadinessReport("speaking-storage-production-readiness", issues);
        }

        if (safe.storageMode() == SpeakingStorageMode.LOCAL_PRIVATE) {
            issues.add(AiReadinessIssue.blocker(
                    "LOCAL_PRIVATE_STORAGE_NOT_PRODUCTION_FINAL",
                    "Live rollout không được dùng local private storage như production final.",
                    "8F-D"));
        }
        if (!safe.objectStorageProviderSelected()) {
            issues.add(AiReadinessIssue.blocker(
                    "OBJECT_STORAGE_DECISION_MISSING",
                    "Cần chọn và xác nhận object storage provider trước live Speaking AI rollout.",
                    "8F-D"));
        }
        if (!safe.cleanupWorkerEnabled()) {
            issues.add(AiReadinessIssue.blocker(
                    "CLEANUP_WORKER_NOT_READY_FOR_PRODUCTION",
                    "Cleanup worker/topology phải được xác nhận trước live rollout.",
                    "8F-D"));
        }
        if (!safe.singleNodeDeployment() && safe.storageMode() == SpeakingStorageMode.LOCAL_PRIVATE) {
            issues.add(AiReadinessIssue.blocker(
                    "MULTI_NODE_LOCAL_STORAGE_UNSAFE",
                    "Multi-node deployment không được dùng local private storage cho learner media.",
                    "8F-D"));
        }
        if (safe.storageMode() == SpeakingStorageMode.OBJECT_STORAGE
                && safe.objectStorageProviderSelected()
                && safe.cleanupWorkerEnabled()) {
            issues.add(AiReadinessIssue.info(
                    "OBJECT_STORAGE_PATH_READY_FOR_ROLLOUT_GATE",
                    "Storage mode đã sẵn sàng cho rollout gate; vẫn cần phase-gate checklist cuối.",
                    "8F-E"));
        }

        return new AiReadinessReport("speaking-storage-production-readiness", issues);
    }
}
