package com.ksh.features.practice.ai.readiness;

public record AiReadinessIssue(
        AiReadinessSeverity severity,
        String code,
        String messageVi,
        String routedPhase
) {
    public AiReadinessIssue {
        if (severity == null) {
            throw new IllegalArgumentException("severity is required");
        }
        code = required(code, "code");
        messageVi = required(messageVi, "messageVi");
        routedPhase = required(routedPhase, "routedPhase");
    }

    public static AiReadinessIssue blocker(String code, String messageVi, String routedPhase) {
        return new AiReadinessIssue(AiReadinessSeverity.BLOCKER, code, messageVi, routedPhase);
    }

    public static AiReadinessIssue warning(String code, String messageVi, String routedPhase) {
        return new AiReadinessIssue(AiReadinessSeverity.WARNING, code, messageVi, routedPhase);
    }

    public static AiReadinessIssue info(String code, String messageVi, String routedPhase) {
        return new AiReadinessIssue(AiReadinessSeverity.INFO, code, messageVi, routedPhase);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
