package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression contract for forms that gate and then re-fire a submit event. */
class DeferredResubmitGuardTest {

    private static final Path JS_DIR = Path.of("src/main/resources/static/js");

    @Test
    void gated_submit_scripts_defer_the_real_submit() throws IOException {
        for (String filename : List.of(
                "lesson-form-type.js",
                "flashcard-deck-form.js")) {
            String source = read(filename);
            assertThat(source)
                    .as(filename + " must leave the current submit dispatch before re-firing")
                    .containsAnyOf("setTimeout", ".then(", "queueMicrotask",
                            "requestAnimationFrame");
        }
        assertThat(read("lesson-form-type.js"))
                .contains("submitForReal")
                .contains("window.setTimeout")
                .contains("form.requestSubmit(submitter || undefined)");
    }

    @Test
    void lesson_form_releases_guard_on_every_gate_abort() throws IOException {
        String source = read("lesson-form-type.js");
        long abortBranches = source.lines()
                .filter(line -> line.contains("if (!ok"))
                .count();
        long releasedBranches = source.lines()
                .filter(line -> line.contains("if (!ok")
                        && line.contains("abortSubmit()"))
                .count();

        assertThat(releasedBranches)
                .isGreaterThan(0)
                .isEqualTo(abortBranches);
    }

    private static String read(String filename) throws IOException {
        return Files.readString(JS_DIR.resolve(filename), StandardCharsets.UTF_8);
    }
}
