package com.ksh.features.practice;

import com.ksh.features.practice.web.PracticeFormFields;
import com.ksh.features.practice.web.PracticeMediaRoutes;
import com.ksh.features.practice.web.PracticeModelAttributes;
import com.ksh.features.practice.web.PracticeRoutes;
import com.ksh.features.practice.web.PracticeViews;
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
    void libraryCardsRenderRealPerSetTestProgress() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("index.html"));

        assertThat(template).contains("setTestProgress.get(set.id())");
        assertThat(template).contains("progress.completedTests()");
        assertThat(template).contains("progress.totalTests()");
        assertThat(template).doesNotContain("set.skill() == 'READING' ? 40");
        assertThat(template).doesNotContain("set.skill() == 'LISTENING' ? 20");
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

    @Test
    void practiceWebBoundaryConstantsCoverHighRiskContracts() {
        assertThat(PracticeRoutes.BASE).isEqualTo("/practice");
        assertThat(PracticeRoutes.redirectToSetDetail(11L)).isEqualTo("redirect:/practice/sets/11");
        assertThat(PracticeRoutes.redirectToTestDetail(11L, 22L))
                .isEqualTo("redirect:/practice/sets/11/tests/22");
        assertThat(PracticeRoutes.redirectToAttempt(33L, "exam"))
                .isEqualTo("redirect:/practice/attempts/33?mode=exam");
        assertThat(PracticeRoutes.redirectToResultDetail(44L, 55L))
                .isEqualTo("redirect:/practice/attempts/44/result/detail?questionId=55");

        assertThat(PracticeViews.RESULT_DETAIL).isEqualTo("practice/result-detail");
        assertThat(PracticeModelAttributes.SPEAKING_MEDIA_PLAYBACK_ENABLED)
                .isEqualTo("speakingMediaPlaybackEnabled");
        assertThat(PracticeFormFields.answerKey(66L)).isEqualTo("answer_66");
        assertThat(PracticeFormFields.isAnswerField("answer_66")).isTrue();
        assertThat(PracticeFormFields.questionIdFromAnswerField("answer_66")).isEqualTo("66");
        assertThat(PracticeMediaRoutes.playbackPath(1L, 2L, 3L))
                .isEqualTo("/practice/attempts/1/questions/2/speaking-media/3/content");
    }
}
