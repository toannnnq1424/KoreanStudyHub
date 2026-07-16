package com.ksh.features.practice.ai.readiness;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiRolloutReadinessChecklist {

    public AiReadinessReport assessLiveSpeakingRollout(
            List<AiReadinessReport> prerequisiteReports,
            boolean manualUatCompleted,
            boolean phase8GClosedAcceptedOrDeferred,
            boolean phase8HClosedAcceptedOrDeferred
    ) {
        List<AiReadinessIssue> issues = new ArrayList<>();
        if (prerequisiteReports != null) {
            for (AiReadinessReport report : prerequisiteReports) {
                if (report != null) {
                    issues.addAll(report.issues());
                }
            }
        }
        if (!manualUatCompleted) {
            issues.add(AiReadinessIssue.blocker(
                    "MANUAL_UAT_PENDING",
                    "Live rollout cần manual UAT hoặc quyết định accepted debt rõ ràng ở Phase 15.",
                    "Phase 15"));
        }
        if (!phase8GClosedAcceptedOrDeferred) {
            issues.add(AiReadinessIssue.blocker(
                    "PHASE_8G_NOT_CLOSED",
                    "Phase 9/live rollout không được bỏ qua 8G functional integration regression.",
                    "8G"));
        }
        if (!phase8HClosedAcceptedOrDeferred) {
            issues.add(AiReadinessIssue.blocker(
                    "PHASE_8H_NOT_CLOSED",
                    "Phase 9/live rollout không được bỏ qua 8H architecture/security boundary review.",
                    "8H"));
        }
        if (issues.stream().noneMatch(issue -> issue.severity() == AiReadinessSeverity.BLOCKER)) {
            issues.add(AiReadinessIssue.info(
                    "LIVE_SPEAKING_AI_READY_FOR_EXPLICIT_USER_APPROVAL",
                    "Tất cả rollout gates kỹ thuật đã qua; vẫn cần quyết định bật live rõ ràng của user/operator.",
                    "8F-E"));
        }
        return new AiReadinessReport("live-speaking-ai-rollout-checklist", issues);
    }
}
