package com.ksh.features.lessons.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LessonUiMutationContractTest {

    private static final Path COMMENTS_SCRIPT =
            Path.of("src/main/resources/static/js/lesson-comments.js");
    private static final Path SECTIONS_SCRIPT =
            Path.of("src/main/resources/static/js/sections.js");

    @Test
    void root_comment_submit_is_guarded_and_preserves_the_draft_on_failure() throws IOException {
        String script = Files.readString(COMMENTS_SCRIPT);

        assertThat(script)
                .contains("if (composerSubmitting) return;")
                .contains("composerSubmitting = true;")
                .contains("composerSubmit.disabled = true;")
                .contains("composerInput.value = '';")
                .contains("composerSubmitting = false;")
                .contains("composerSubmit.disabled = false;");

        int successClear = script.indexOf("composerInput.value = '';");
        int failureHandler = script.indexOf("mutate() already displayed the error");
        assertThat(failureHandler).isGreaterThan(successClear);
    }

    @Test
    void deleting_the_selected_section_invalidates_state_and_reloads_a_valid_view() throws IOException {
        String script = Files.readString(SECTIONS_SCRIPT);

        assertThat(script)
                .contains("if (String(state.selectedSectionId) === String(sectionId))")
                .contains("state.selectedSectionId = null;")
                .contains("window.location.assign(state.baseUrl);");
    }
}
