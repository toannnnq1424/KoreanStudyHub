package com.ksh.features.practice.ai.transport;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPrimaryCapabilityPropertiesTest {

    @Test
    void oneProviderFamilyRetainsIndependentCapabilitySlots() {
        OpenAiPrimaryCapabilityProperties properties = properties(
                true,
                "secret",
                "assessment-model",
                "batch-stt",
                "",
                "tts-model",
                "");

        assertThat(properties.modelFor(
                PracticeAiCapability.ASSESSMENT_TEXT_VISION))
                .isEqualTo("assessment-model");
        assertThat(properties.modelFor(
                PracticeAiCapability.BATCH_TRANSCRIPTION))
                .isEqualTo("batch-stt");
        assertThat(properties.modelFor(
                PracticeAiCapability.TEXT_TO_SPEECH))
                .isEqualTo("tts-model");
        assertThat(properties.available(
                PracticeAiCapability.REALTIME_TRANSCRIPTION)).isFalse();
        assertThat(properties.available(
                PracticeAiCapability.REALTIME_SPEECH)).isFalse();
    }

    @Test
    void disabledOrCredentiallessConfigurationFailsEverySlotClosed() {
        OpenAiPrimaryCapabilityProperties disabled = properties(
                false,
                "secret",
                "assessment-model",
                "batch-stt",
                "realtime-stt",
                "tts-model",
                "realtime-speech");
        OpenAiPrimaryCapabilityProperties credentialless = properties(
                true,
                "",
                "assessment-model",
                "batch-stt",
                "realtime-stt",
                "tts-model",
                "realtime-speech");

        for (PracticeAiCapability capability
                : PracticeAiCapability.values()) {
            assertThat(disabled.available(capability)).isFalse();
            assertThat(credentialless.available(capability)).isFalse();
        }
    }

    static OpenAiPrimaryCapabilityProperties properties(
            boolean enabled,
            String apiKey,
            String assessment,
            String batchStt,
            String realtimeStt,
            String tts,
            String realtimeSpeech) {
        return new OpenAiPrimaryCapabilityProperties(
                enabled,
                "https://api.openai.com/v1/",
                apiKey,
                assessment,
                batchStt,
                realtimeStt,
                tts,
                realtimeSpeech,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                0,
                65_536);
    }
}
