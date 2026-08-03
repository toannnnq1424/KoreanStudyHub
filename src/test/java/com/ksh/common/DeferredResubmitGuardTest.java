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
        for (String filename : List.of("flashcard-deck-form.js")) {
            String source = read(filename);
            assertThat(source)
                    .as(filename + " must leave the current submit dispatch before re-firing")
                    .containsAnyOf("setTimeout", ".then(", "queueMicrotask",
                            "requestAnimationFrame");
        }
    }

    private static String read(String filename) throws IOException {
        return Files.readString(JS_DIR.resolve(filename), StandardCharsets.UTF_8);
    }
}
