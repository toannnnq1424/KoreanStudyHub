package com.ksh.features.practice.ai.readiness;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProviderOperationalReadinessPolicy {

    public AiReadinessReport assessRunbook(ProviderOperationalRunbook runbook) {
        List<AiReadinessIssue> issues = new ArrayList<>();
        ProviderOperationalRunbook safe = runbook == null
                ? new ProviderOperationalRunbook(false, false, false, false, false, false)
                : runbook;

        require(safe.providerOutage(), "RUNBOOK_PROVIDER_OUTAGE_MISSING",
                "Thiếu runbook xử lý provider outage.", issues);
        require(safe.rateLimit(), "RUNBOOK_RATE_LIMIT_MISSING",
                "Thiếu runbook xử lý rate limit / 429.", issues);
        require(safe.costSpike(), "RUNBOOK_COST_SPIKE_MISSING",
                "Thiếu runbook xử lý cost spike.", issues);
        require(safe.badFeedback(), "RUNBOOK_BAD_FEEDBACK_MISSING",
                "Thiếu runbook xử lý phản hồi AI sai hoặc không phù hợp.", issues);
        require(safe.mediaStorageFailure(), "RUNBOOK_MEDIA_STORAGE_MISSING",
                "Thiếu runbook xử lý lỗi lưu trữ/phát media Speaking.", issues);
        require(safe.privacyIncident(), "RUNBOOK_PRIVACY_INCIDENT_MISSING",
                "Thiếu runbook xử lý sự cố privacy/security.", issues);

        issues.add(AiReadinessIssue.info(
                "BACKEND_OPERATOR_READINESS_ONLY",
                "8F-C chỉ chuẩn bị backend/operator readiness; user-facing retry button và background re-evaluation đang deferred.",
                "Phase 13"));

        return new AiReadinessReport("provider-operational-runbook", issues);
    }

    public List<String> safeMetricDimensions() {
        return List.of("feature", "outcome");
    }

    public List<String> forbiddenOperationalData() {
        return List.of(
                "provider raw request body",
                "provider raw response body",
                "API key",
                "provider secret",
                "learner transcript",
                "learner answer",
                "storage key",
                "local file path",
                "user identity");
    }

    private static void require(
            boolean present,
            String code,
            String messageVi,
            List<AiReadinessIssue> issues
    ) {
        if (!present) {
            issues.add(AiReadinessIssue.blocker(code, messageVi, "8F-C"));
        }
    }
}
