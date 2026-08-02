package com.ksh.features.tests.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LecturerAiQuestionUiContractTest {

    @Test
    void create_screen_offers_save_draft_then_open_ai_flow() throws IOException {
        String template = Files.readString(
                Path.of("src/main/resources/templates/tests/lecturer-form.html"),
                StandardCharsets.UTF_8);
        String formScript = Files.readString(
                Path.of("src/main/resources/static/js/test-lecturer-form.js"),
                StandardCharsets.UTF_8);
        String aiScript = Files.readString(
                Path.of("src/main/resources/static/js/test-lecturer-ai-questions.js"),
                StandardCharsets.UTF_8);

        assertThat(template)
                .contains("id=\"lfSaveForAi\"")
                .contains("Tạo nháp &amp; sinh bằng AI");
        assertThat(formScript)
                .contains("payload.status = 'DRAFT'")
                .contains("/edit?tab=info&openAi=1");
        assertThat(aiScript)
                .contains("get('openAi') === '1'")
                .contains("panel.hidden = false");
    }
}
