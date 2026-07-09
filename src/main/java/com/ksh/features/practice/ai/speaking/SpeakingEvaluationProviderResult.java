package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;

public record SpeakingEvaluationProviderResult(
        boolean success,
        JsonNode evaluationJson,
        SpeakingEvaluationStatus failureStatus,
        String provider,
        String model,
        String errorCategory,
        boolean retryable,
        Long latencyMs
) {
    public static SpeakingEvaluationProviderResult success(
            JsonNode evaluationJson,
            String provider,
            String model,
            Long latencyMs
    ) {
        return new SpeakingEvaluationProviderResult(true, evaluationJson, null, provider, model, null, false, latencyMs);
    }

    public static SpeakingEvaluationProviderResult failure(
            SpeakingEvaluationStatus status,
            String provider,
            String model,
            String errorCategory,
            boolean retryable,
            Long latencyMs
    ) {
        return new SpeakingEvaluationProviderResult(false, null, status, provider, model, errorCategory, retryable, latencyMs);
    }

    @Override
    public String toString() {
        return "SpeakingEvaluationProviderResult{"
                + "success=" + success
                + ", evaluationJsonPresent=" + (evaluationJson != null)
                + ", failureStatus=" + failureStatus
                + ", provider='" + provider + '\''
                + ", model='" + model + '\''
                + ", errorCategory='" + errorCategory + '\''
                + ", retryable=" + retryable
                + ", latencyMs=" + latencyMs
                + '}';
    }
}
