package com.ksh.features.practice.ai.transport;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PracticeStructuredGenerationRequest(
        String operation,
        PracticeAiCapability capability,
        PracticeAiAuthoritySnapshot authority,
        PracticeModelCapabilityProfile modelCapabilityProfile,
        String systemInstruction,
        String developerInstruction,
        Map<String, Object> input,
        String responseSchemaName,
        Map<String, Object> responseSchema,
        List<ImageEvidence> images,
        int maxOutputTokens,
        String idempotencyKey
) {
    public PracticeStructuredGenerationRequest {
        operation = required(operation, "operation");
        capability = Objects.requireNonNull(capability, "capability");
        authority = Objects.requireNonNull(authority, "authority");
        modelCapabilityProfile = Objects.requireNonNull(
                modelCapabilityProfile,
                "modelCapabilityProfile");
        systemInstruction = required(systemInstruction, "systemInstruction");
        developerInstruction = developerInstruction == null
                ? ""
                : developerInstruction.trim();
        input = Map.copyOf(Objects.requireNonNull(input, "input"));
        responseSchemaName = required(responseSchemaName, "responseSchemaName");
        responseSchema = Map.copyOf(
                Objects.requireNonNull(responseSchema, "responseSchema"));
        images = images == null ? List.of() : List.copyOf(images);
        if (maxOutputTokens < 1 || maxOutputTokens > 16_384) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be between 1 and 16384.");
        }
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }

    public record ImageEvidence(
            String role,
            String sha256,
            String dataUrl,
            String detail
    ) {
        public ImageEvidence {
            role = required(role, "role");
            sha256 = required(sha256, "sha256");
            dataUrl = required(dataUrl, "dataUrl");
            detail = detail == null || detail.isBlank()
                    ? "high"
                    : detail.trim();
        }
    }
}
