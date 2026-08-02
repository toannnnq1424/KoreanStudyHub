package com.ksh.features.practice.ai.controlplane;

import java.util.Objects;

public record PracticeAiExecutionSnapshot(
        PracticeAiPurpose purpose,
        long bindingRevision,
        long providerProfileRevision,
        String providerFamily,
        String providerProfileCode,
        String model,
        String transportDialect,
        PracticeAiCapabilitySet capabilities,
        PracticeAiLimits limits,
        String capabilityDigest,
        String limitsDigest,
        String retentionCode
) {
    public PracticeAiExecutionSnapshot {
        purpose = Objects.requireNonNull(purpose, "purpose");
        providerFamily = required(providerFamily, "providerFamily");
        providerProfileCode = required(providerProfileCode, "providerProfileCode");
        model = required(model, "model");
        transportDialect = required(transportDialect, "transportDialect");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
        limits = Objects.requireNonNull(limits, "limits");
        capabilityDigest = digest(capabilityDigest, "capabilityDigest");
        limitsDigest = digest(limitsDigest, "limitsDigest");
        retentionCode = required(retentionCode, "retentionCode");
        if (bindingRevision < 0 || providerProfileRevision < 0) {
            throw new IllegalArgumentException("Revisions must not be negative");
        }
    }

    public String revisionIdentity() {
        return purpose.name() + ":" + bindingRevision + ":"
                + providerProfileCode + ":" + providerProfileRevision;
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static String digest(String value, String name) {
        String normalized = required(value, name);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be SHA-256");
        }
        return normalized;
    }
}
