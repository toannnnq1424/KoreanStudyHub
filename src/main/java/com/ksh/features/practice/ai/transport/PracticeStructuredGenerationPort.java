package com.ksh.features.practice.ai.transport;

import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;

public interface PracticeStructuredGenerationPort {

    ProviderIdentity identity(PracticeAiPurpose purpose);

    PracticeStructuredGenerationResponse generate(
            PracticeStructuredGenerationRequest request);

    record ProviderIdentity(
            String provider,
            String model,
            PracticeModelCapabilityProfile capabilityProfile,
            boolean available,
            long bindingRevision,
            long providerProfileRevision,
            String providerProfileCode
    ) {
        public ProviderIdentity(
                String provider,
                String model,
                PracticeModelCapabilityProfile capabilityProfile,
                boolean available) {
            this(provider, model, capabilityProfile, available, -1L, -1L, "LEGACY_TEST");
        }
    }
}
