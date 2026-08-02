package com.ksh.features.practice.ai.speaking.transcription;

public interface SpeakingTranscriptionClient {
    SpeakingTranscriptionResult transcribe(SpeakingTranscriptionRequest request);

    default ProviderIdentity identity() {
        return new ProviderIdentity("UNBOUND", "", -1L, -1L, "UNBOUND", false);
    }

    record ProviderIdentity(
            String provider,
            String model,
            long bindingRevision,
            long providerProfileRevision,
            String providerProfileCode,
            boolean available
    ) {
    }
}
