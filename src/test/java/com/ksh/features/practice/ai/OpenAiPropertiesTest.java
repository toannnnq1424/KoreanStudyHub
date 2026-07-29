package com.ksh.features.practice.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiPropertiesTest {

    @Test
    void writingAndPdfTransportTimeoutsAreBounded() {
        OpenAiProperties tooWide = new OpenAiProperties(
                "",
                "model",
                "transcription",
                "https://provider.invalid",
                Duration.ofMinutes(5),
                Duration.ofHours(1));
        OpenAiProperties tooNarrow = new OpenAiProperties(
                "",
                "model",
                "transcription",
                "https://provider.invalid",
                Duration.ZERO,
                Duration.ZERO);

        assertThat(tooWide.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(tooWide.readTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(tooNarrow.connectTimeout()).isEqualTo(Duration.ofMillis(100));
        assertThat(tooNarrow.readTimeout()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void evaluatorRequestCapabilitiesAreBoundedAndExplicit() {
        OpenAiProperties properties = new OpenAiProperties(
                "runtime-only",
                "model",
                "transcription",
                "https://provider.invalid",
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                100_000,
                false,
                true,
                " LOW ");

        assertThat(properties.evaluatorMaxOutputTokens())
                .isEqualTo(32_768);
        assertThat(properties.evaluatorStructuredOutputEnabled())
                .isFalse();
        assertThat(properties.evaluatorReasoningEffortEnabled())
                .isTrue();
        assertThat(properties.evaluatorReasoningEffort())
                .isEqualTo("low");
        assertThat(properties.hasEvaluatorCredential()).isTrue();
    }

    @Test
    void missingCredentialAndUnsupportedReasoningFailSafely() {
        OpenAiProperties missing = new OpenAiProperties(
                " ",
                "model",
                "transcription",
                "https://provider.invalid",
                Duration.ofSeconds(5),
                Duration.ofSeconds(60));

        assertThat(missing.hasEvaluatorCredential()).isFalse();
        assertThatThrownBy(() -> new OpenAiProperties(
                "",
                "model",
                "transcription",
                "https://provider.invalid",
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                4096,
                true,
                true,
                "provider-secret-mode"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "openai.evaluator-reasoning-effort");
    }
}
