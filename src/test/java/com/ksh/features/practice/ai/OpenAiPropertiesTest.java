package com.ksh.features.practice.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
}
