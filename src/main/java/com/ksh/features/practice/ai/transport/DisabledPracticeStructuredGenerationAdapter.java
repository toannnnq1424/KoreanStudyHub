package com.ksh.features.practice.ai.transport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "app.practice.ai.openai-primary",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DisabledPracticeStructuredGenerationAdapter
        implements PracticeStructuredGenerationPort {

    private final OpenAiPrimaryCapabilityProperties properties;

    public DisabledPracticeStructuredGenerationAdapter(
            OpenAiPrimaryCapabilityProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderIdentity identity(PracticeAiCapability capability) {
        return new ProviderIdentity(
                "openai-primary",
                properties.modelFor(capability),
                profileFor(capability),
                false);
    }

    @Override
    public PracticeStructuredGenerationResponse generate(
            PracticeStructuredGenerationRequest request) {
        throw new PracticeAiContractException(
                "PROVIDER_DISABLED",
                false);
    }

    static PracticeModelCapabilityProfile profileFor(
            PracticeAiCapability capability) {
        if (capability == PracticeAiCapability.ASSESSMENT_TEXT_VISION) {
            return PracticeModelCapabilityProfile.openAiAssessmentV1();
        }
        return new PracticeModelCapabilityProfile(
                "openai-capability-slot-v1",
                false,
                false,
                false,
                false,
                false);
    }
}
