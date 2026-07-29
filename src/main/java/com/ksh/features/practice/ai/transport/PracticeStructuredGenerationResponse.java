package com.ksh.features.practice.ai.transport;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record PracticeStructuredGenerationResponse(
        JsonNode output,
        String provider,
        String model,
        String finishReason,
        String providerRequestId
) {
    public PracticeStructuredGenerationResponse {
        output = Objects.requireNonNull(output, "output").deepCopy();
        provider = required(provider, "provider");
        model = required(model, "model");
        finishReason = required(finishReason, "finishReason");
        providerRequestId = providerRequestId == null
                ? ""
                : providerRequestId.trim();
    }

    @Override
    public JsonNode output() {
        return output.deepCopy();
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }
}
