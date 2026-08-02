package com.ksh.features.practice.ai.controlplane;

import java.net.URI;
import java.util.Objects;

public record PracticeAiResolvedBinding(
        PracticeAiExecutionSnapshot snapshot,
        URI baseUrl,
        String credentialSecret
) {
    public PracticeAiResolvedBinding {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        credentialSecret = Objects.requireNonNull(credentialSecret, "credentialSecret");
        if (credentialSecret.isBlank()) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_PURPOSE_UNAVAILABLE", false);
        }
    }

    @Override
    public String toString() {
        return "PracticeAiResolvedBinding{snapshot=" + snapshot
                + ", baseUrl=" + baseUrl
                + ", credentialSecretPresent=true}";
    }
}
