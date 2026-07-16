package com.ksh.features.practice.ai.readinglistening;

public record ExplanationFingerprint(
        String fingerprint,
        String questionHash,
        String stimulusHash,
        String answerSpecHash,
        String mediaBundleHash,
        String assessmentSchemaVersion,
        String providerModel,
        String promptVersion,
        String responseSchemaVersion,
        String explanationLanguage,
        String inputContractJson
) {
}
