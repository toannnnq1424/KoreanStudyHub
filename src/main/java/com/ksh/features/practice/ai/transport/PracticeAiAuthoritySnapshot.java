package com.ksh.features.practice.ai.transport;

import java.util.Objects;

public record PracticeAiAuthoritySnapshot(
        String schemaVersion,
        String promptVersion,
        String strategyCode,
        String strategyVersion,
        String authorityIdentity
) {
    public PracticeAiAuthoritySnapshot {
        schemaVersion = required(schemaVersion, "schemaVersion");
        promptVersion = required(promptVersion, "promptVersion");
        strategyCode = textOr(strategyCode, "NONE");
        strategyVersion = textOr(strategyVersion, "NONE");
        authorityIdentity = required(authorityIdentity, "authorityIdentity");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
