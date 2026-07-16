package com.ksh.features.practice.ai.readiness;

import java.math.BigDecimal;

public record AiCalibrationFixture(
        String fixtureId,
        AiCalibrationSkill skill,
        String taskType,
        String promptVersion,
        String rubricVersion,
        String schemaVersion,
        String model,
        BigDecimal expectedMinPercentage,
        BigDecimal expectedMaxPercentage,
        String qualitativeBand,
        boolean teacherReviewed
) {
    public AiCalibrationFixture {
        fixtureId = required(fixtureId, "fixtureId");
        if (skill == null) {
            throw new IllegalArgumentException("skill is required");
        }
        taskType = required(taskType, "taskType");
        promptVersion = required(promptVersion, "promptVersion");
        rubricVersion = required(rubricVersion, "rubricVersion");
        schemaVersion = required(schemaVersion, "schemaVersion");
        model = required(model, "model");
        if (expectedMinPercentage == null || expectedMaxPercentage == null) {
            throw new IllegalArgumentException("expected range is required");
        }
        qualitativeBand = required(qualitativeBand, "qualitativeBand");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
