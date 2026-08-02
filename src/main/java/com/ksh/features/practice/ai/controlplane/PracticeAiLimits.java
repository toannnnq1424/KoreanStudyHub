package com.ksh.features.practice.ai.controlplane;

import java.time.Duration;

public record PracticeAiLimits(
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxRetries,
        int maxRequestBytes,
        int maxResponseBytes
) {
    public PracticeAiLimits {
        if (connectTimeoutMs < 100 || connectTimeoutMs > 30_000
                || readTimeoutMs < 1_000 || readTimeoutMs > 120_000
                || maxRetries < 0 || maxRetries > 3
                || maxRequestBytes < 1 || maxRequestBytes > 26_214_400
                || maxResponseBytes < 16_384 || maxResponseBytes > 8_388_608) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_LIMITS_INCOMPATIBLE", false);
        }
    }

    public Duration connectTimeout() {
        return Duration.ofMillis(connectTimeoutMs);
    }

    public Duration readTimeout() {
        return Duration.ofMillis(readTimeoutMs);
    }
}
