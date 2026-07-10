package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeFunctionalUiContractTest {
    private static final Path PRACTICE_TEMPLATES = Path.of("src/main/resources/templates/practice");
    private static final Path PRACTICE_JS = Path.of("src/main/resources/static/js/practice.js");

    @Test
    void setDetailUsesPracticeTestIdsForTestDetailLinks() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("set-detail.html"));

        assertThat(template).contains("th:each=\"test, iter : ${view.tests()}\"");
        assertThat(template).contains("testId=${test.id()}");
        assertThat(template).doesNotContain("testId=${view.set().id()}");
    }

    @Test
    void modeCreateAttemptFormsSubmitSectionId() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("mode.html"));

        assertThat(template).contains("name=\"sectionId\"");
        assertThat(template).contains("th:each=\"sec : ${sections}\"");
        assertThat(template).contains("value=\"practice\"");
        assertThat(template).contains("value=\"exam\"");
    }

    @Test
    void resultBackLinkUsesAttemptTestId() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("result.html"));

        assertThat(template).contains("result.testId()");
        assertThat(template).doesNotContain("testId=${result.set().id()}");
    }

    @Test
    void readingListeningResultBackLinkUsesAttemptTestId() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("rl-result.html"));

        assertThat(template).contains("result.testId()");
        assertThat(template).contains("/practice/sets/{setId}/tests/{testId}");
    }

    @Test
    void playerQuestionBlockSelectorMatchesPracticeJavascript() throws IOException {
        String player = Files.readString(PRACTICE_TEMPLATES.resolve("player.html"));
        String js = Files.readString(PRACTICE_JS);

        assertThat(player).contains("ksh-question-block");
        assertThat(js).contains("querySelectorAll('.ksh-question-block')");
        assertThat(js).doesNotContain("querySelectorAll('.ksh-question-card')");
    }
}
