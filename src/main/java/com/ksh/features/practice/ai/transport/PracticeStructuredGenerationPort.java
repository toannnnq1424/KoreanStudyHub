package com.ksh.features.practice.ai.transport;

public interface PracticeStructuredGenerationPort {

    ProviderIdentity identity(PracticeAiCapability capability);

    PracticeStructuredGenerationResponse generate(
            PracticeStructuredGenerationRequest request);

    record ProviderIdentity(
            String provider,
            String model,
            PracticeModelCapabilityProfile capabilityProfile,
            boolean available
    ) {
    }
}
