package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Source-level regression contracts for asynchronous non-Practice UI mutations. */
class FrontendMutationGuardContractTest {

    private static final Path JS_DIR = Path.of("src/main/resources/static/js");

    @Test
    void library_attach_wizard_rejects_responses_from_an_old_target_generation()
            throws IOException {
        String source = read("library-attach-wizard.js");

        assertThat(source)
                .contains("requestGeneration: 0")
                .contains("var generation = ++state.requestGeneration;")
                .contains("generation !== state.requestGeneration")
                .contains("String(classId) !== String(state.classId)")
                .contains("String(sectionId) !== String(state.sectionId)")
                .contains("String(lessonId) !== String(state.lessonId)")
                .contains("state.checking = true;")
                .contains("if (state.binding || state.checking) return;");
    }

    @Test
    void flashcard_save_is_single_flight_and_recovers_after_failure() throws IOException {
        String source = read("flashcard-deck-form.js");

        assertThat(source)
                .contains("var saving = false;")
                .contains("if (saving) return;")
                .contains("saving = true;")
                .contains("setSubmitDisabled(true);")
                .contains("saving = false;")
                .contains("setSubmitDisabled(false);");

        assertThat(source.indexOf("saving = true;"))
                .isLessThan(source.indexOf("window.FcCommon.postJson(cardsUrl"));
    }

    @Test
    void lesson_form_gates_are_single_flight_and_release_on_abort() throws IOException {
        String source = read("lesson-form-type.js");

        assertThat(source)
                .contains("var submitting = false;")
                .contains("if (submitting) return;")
                .contains("submitting = true;")
                .contains("setSubmitControlsDisabled(true);")
                .contains("submitting = false;")
                .contains("setSubmitControlsDisabled(false);");

        assertThat(source.indexOf("submitting = true;"))
                .isLessThan(source.indexOf("confirmTypeSwitch(function (okType)"));
    }

    private static String read(String filename) throws IOException {
        return Files.readString(JS_DIR.resolve(filename), StandardCharsets.UTF_8);
    }
}
