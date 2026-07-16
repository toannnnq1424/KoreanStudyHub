package com.ksh.features.practice.ai.readiness;

public record ProviderOperationalRunbook(
        boolean providerOutage,
        boolean rateLimit,
        boolean costSpike,
        boolean badFeedback,
        boolean mediaStorageFailure,
        boolean privacyIncident
) {
    public static ProviderOperationalRunbook complete() {
        return new ProviderOperationalRunbook(true, true, true, true, true, true);
    }
}
