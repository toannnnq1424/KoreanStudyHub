package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Learner-safe Speaking prompt presentation shared by lecturer preview and the
 * immutable learner player. Authoring source, transcript, artifact, provider,
 * fingerprint and storage identities deliberately do not belong here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpeakingPromptDelivery(
        String schemaVersion,
        String contentSchemaVersion,
        String deliveryMode,
        String promptText,
        String promptAudioReference,
        boolean promptVisibleBeforePlayback,
        boolean promptVisibleAfterPlayback,
        Integer promptPlayLimit,
        Integer preparationSeconds,
        Integer responseSeconds,
        List<Step> steps
) {
    public static final String SCHEMA_VERSION = "speaking-prompt-delivery-v1";

    public SpeakingPromptDelivery {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public enum Step {
        PROMPT_PLAYBACK,
        PREPARATION,
        RECORDING
    }
}
