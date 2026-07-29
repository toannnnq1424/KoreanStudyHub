package com.ksh.features.practice.ai.transport;

public record PracticeModelCapabilityProfile(
        String profileVersion,
        boolean nativeJsonSchema,
        boolean toolCalls,
        boolean plainJsonFallback,
        boolean imageInput,
        boolean streaming
) {
    public static PracticeModelCapabilityProfile openAiAssessmentV1() {
        return new PracticeModelCapabilityProfile(
                "openai-assessment-v1",
                true,
                false,
                false,
                true,
                false);
    }
}
