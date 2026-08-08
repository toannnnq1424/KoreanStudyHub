package com.ksh.features.practice.ai.controlplane;

import java.util.LinkedHashSet;
import java.util.Set;

public record PracticeAiCapabilitySet(
        boolean strictJsonSchema,
        boolean imageInput,
        boolean transcriptTextInput,
        boolean batchTranscription,
        boolean speechSynthesis,
        boolean directAudioInput
) {
    public PracticeAiCapabilitySet(
            boolean strictJsonSchema,
            boolean imageInput,
            boolean transcriptTextInput,
            boolean batchTranscription,
            boolean speechSynthesis) {
        this(strictJsonSchema, imageInput, transcriptTextInput,
                batchTranscription, speechSynthesis, false);
    }

    public Set<String> enabledCodes() {
        Set<String> result = new LinkedHashSet<>();
        if (strictJsonSchema) {
            result.add("STRICT_JSON_SCHEMA");
        }
        if (imageInput) {
            result.add("IMAGE_INPUT");
        }
        if (transcriptTextInput) {
            result.add("TRANSCRIPT_TEXT_INPUT");
        }
        if (batchTranscription) {
            result.add("BATCH_TRANSCRIPTION");
        }
        if (speechSynthesis) {
            result.add("SPEECH_SYNTHESIS");
        }
        if (directAudioInput) {
            result.add("DIRECT_AUDIO_INPUT");
        }
        return Set.copyOf(result);
    }

    public boolean supportsAll(Set<String> required) {
        return enabledCodes().containsAll(required);
    }
}
