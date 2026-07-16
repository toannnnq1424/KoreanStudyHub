package com.ksh.features.practice.ai.readiness;

import java.util.List;

public record AiReadinessReport(
        String subject,
        List<AiReadinessIssue> issues
) {
    public AiReadinessReport {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean rolloutAllowed() {
        return blockers().isEmpty();
    }

    public List<AiReadinessIssue> blockers() {
        return issues.stream()
                .filter(issue -> issue.severity() == AiReadinessSeverity.BLOCKER)
                .toList();
    }

    public List<AiReadinessIssue> warnings() {
        return issues.stream()
                .filter(issue -> issue.severity() == AiReadinessSeverity.WARNING)
                .toList();
    }
}
