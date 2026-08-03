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

    private static String read(String filename) throws IOException {
        return Files.readString(JS_DIR.resolve(filename), StandardCharsets.UTF_8);
    }
}
