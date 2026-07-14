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
    private static final Path PRACTICE_CATALOG_JS =
            Path.of("src/main/resources/static/js/practice/practice-catalog.js");
    private static final Path PRACTICE_CATALOG_CSS =
            Path.of("src/main/resources/static/css/practice-catalog.css");
    private static final Path SHARED_HEAD =
            Path.of("src/main/resources/templates/fragments/head.html");
    private static final Path LOGIN_TEMPLATE =
            Path.of("src/main/resources/templates/auth/login.html");
    private static final Path SHARED_APP_JS =
            Path.of("src/main/resources/static/js/app.js");
    private static final Path SHARED_MAIN_CSS =
            Path.of("src/main/resources/static/css/main.css");
    private static final Path PRACTICE_INDEX_CSS =
            Path.of("src/main/resources/static/css/practice-index.css");
    private static final Path PRACTICE_PROGRESS_JS =
            Path.of("src/main/resources/static/js/practice-progress.js");
    private static final Path PRACTICE_TEST_DETAIL_JS =
            Path.of("src/main/resources/static/js/practice/practice-test-detail.js");
    private static final Path PRACTICE_DETAIL_CSS =
            Path.of("src/main/resources/static/css/practice-detail.css");
    private static final Path PRACTICE_ROUTES =
            Path.of("src/main/java/com/ksh/features/practice/web/PracticeRoutes.java");

    @Test
    void detailPagesUsePerTestAndPerSkillContracts() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("set-detail.html"));
        String testDetail = Files.readString(PRACTICE_TEMPLATES.resolve("test-detail.html"));

        assertThat(template).contains("th:each=\"test, iter : ${testCards}\"");
        assertThat(template).contains("testId=${test.id()}");
        assertThat(template).doesNotContain("testId=${view.set().id()}");
        assertThat(testDetail).contains("th:each=\"card : ${skillCards}\"");
        assertThat(testDetail).contains("card.completedAttempts()", "data-attempt-toggle");
        assertThat(testDetail).doesNotContain("submissions", "inProgressAttempts", "Overall");
    }

    @Test
    void speakingPreflightChecksOutputMicrophonePermissionAndPrivateUploadReadiness() throws IOException {
        String testDetail = Files.readString(PRACTICE_TEMPLATES.resolve("test-detail.html"));
        String detailJs = Files.readString(PRACTICE_TEST_DETAIL_JS);
        String detailCss = Files.readString(PRACTICE_DETAIL_CSS);

        assertThat(testDetail).contains(
                "data-speaking-preflight=${card.skill() == 'SPEAKING'}",
                "id=\"speaking-preflight\"",
                "data-upload-enabled=${speakingMediaUploadEnabled}",
                "data-test-speaker",
                "data-speaker-confirm",
                "data-test-microphone",
                "Âm thử và tín hiệu micro chỉ dùng để kiểm tra thiết bị, không được lưu thành bài làm.");
        assertThat(detailJs).contains(
                "window.MediaRecorder",
                "navigator.mediaDevices.getUserMedia",
                "window.AudioContext || window.webkitAudioContext",
                "gain.connect(context.destination)",
                "track.readyState !== \"live\"",
                "activeStream.getTracks().forEach",
                "action.form.requestSubmit()");
        assertThat(detailJs).doesNotContain("fetch(\"http", "fetch('http");
        assertThat(detailCss).contains(".pd-preflight", ".pd-device-checks", ".pd-mic-meter");
    }

    @Test
    void supersededModeScreenIsRemovedFromCanonicalLearnerRoute() throws IOException {
        String routes = Files.readString(PRACTICE_ROUTES);

        assertThat(Files.exists(PRACTICE_TEMPLATES.resolve("mode.html"))).isFalse();
        assertThat(routes).doesNotContain("TEST_MODE");
    }

    @Test
    void libraryCardsRenderRealCatalogDataAndLazyLoadBatches() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("index.html"));
        String cards = Files.readString(
                PRACTICE_TEMPLATES.resolve("fragments/catalog-cards.html"));
        String catalogJs = Files.readString(PRACTICE_CATALOG_JS);
        String catalogCss = Files.readString(PRACTICE_CATALOG_CSS);

        assertThat(template).contains("catalog.totalElements()");
        assertThat(template).contains("catalog.resumeCard()");
        assertThat(template).contains("pc-resume-banner");
        assertThat(template).contains("pc-skill-pill");
        assertThat(template).contains("data-endpoint=@{/practice/catalog}");
        assertThat(cards).contains("card.testCount()");
        assertThat(cards).contains("card.completedTests()");
        assertThat(cards).contains("card.coverSkill()");
        assertThat(cards).contains("card.coverLabel()");
        assertThat(cards).contains("data-skill-cycle=${card.skillCodes()}");
        assertThat(cards).contains("pc-card-skill-icons");
        assertThat(cards).contains("card.hasSkill('LISTENING')");
        assertThat(cards).contains("card.hasSkill('READING')");
        assertThat(cards).contains("card.hasSkill('WRITING')");
        assertThat(cards).contains("card.hasSkill('SPEAKING')");
        assertThat(cards).doesNotContain("card.questionCount()");
        assertThat(cards).contains("pc-card-book");
        assertThat(cards).contains("bài test");
        assertThat(catalogCss).contains("pc-skill-mixed");
        assertThat(catalogCss).contains("pc-card-skill-icons");
        assertThat(catalogCss).contains("grid-template-columns: repeat(4, minmax(0, 1fr))");
        assertThat(catalogJs).contains("IntersectionObserver");
        assertThat(catalogJs).contains("params.set('batch'");
        assertThat(catalogJs).contains("card.dataset.skillCycle");
        assertThat(catalogJs).contains("window.setInterval");
        assertThat(catalogJs).contains("}, 2000)");
        assertThat(catalogJs).contains("prefers-reduced-motion: reduce");
        assertThat(template).doesNotContain("setTestProgress");
        assertThat(template).doesNotContain("set.skill() == 'READING' ? 40");
        assertThat(template).doesNotContain("set.skill() == 'LISTENING' ? 20");
        assertThat(template).doesNotContain("AI quota");
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
    void navigationShellDoesNotWaitForRemoteDecorativeDependencies() throws IOException {
        String head = Files.readString(SHARED_HEAD);
        String login = Files.readString(LOGIN_TEMPLATE);
        String appJs = Files.readString(SHARED_APP_JS);
        String mainCss = Files.readString(SHARED_MAIN_CSS);
        String practiceIndexCss = Files.readString(PRACTICE_INDEX_CSS);
        String progress = Files.readString(PRACTICE_TEMPLATES.resolve("progress.html"));
        String progressJs = Files.readString(PRACTICE_PROGRESS_JS);

        assertThat(head).doesNotContain("fonts.googleapis.com", "iziToast", "cdn.jsdelivr.net");
        assertThat(login).doesNotContain("fonts.googleapis.com", "iziToast", "cdn.jsdelivr.net");
        assertThat(practiceIndexCss).doesNotContain("@import url(");
        assertThat(appJs).contains("kshToastStack", "ksh-toast-message");
        assertThat(appJs).doesNotContain("window.iziToast");
        assertThat(mainCss).contains(".ksh-toast-stack", ".ksh-toast-close");
        assertThat(progress).doesNotContain("src=\"https://cdn.jsdelivr.net/npm/chart.js");
        assertThat(progressJs).contains("window.addEventListener('load'");
        assertThat(progressJs).contains("requestIdleCallback");
    }

    @Test
    void practiceWebBoundaryConstantsCoverHighRiskContracts() {
        assertThat(PracticeRoutes.BASE).isEqualTo("/practice");
        assertThat(PracticeRoutes.CATALOG_BATCH).isEqualTo("/catalog");
        assertThat(PracticeRoutes.redirectToSetDetail(11L)).isEqualTo("redirect:/practice/sets/11");
        assertThat(PracticeRoutes.redirectToTestDetail(11L, 22L))
                .isEqualTo("redirect:/practice/sets/11/tests/22");
        assertThat(PracticeRoutes.redirectToAttempt(33L, "exam"))
                .isEqualTo("redirect:/practice/attempts/33?mode=exam");
        assertThat(PracticeRoutes.redirectToResultDetail(44L, 55L))
                .isEqualTo("redirect:/practice/attempts/44/result/detail?questionId=55");

        assertThat(PracticeViews.RESULT_DETAIL).isEqualTo("practice/result-detail");
        assertThat(PracticeViews.CATALOG_CARDS)
                .isEqualTo("practice/fragments/catalog-cards :: cards");
        assertThat(PracticeModelAttributes.CATALOG).isEqualTo("catalog");
        assertThat(PracticeModelAttributes.SPEAKING_MEDIA_PLAYBACK_ENABLED)
                .isEqualTo("speakingMediaPlaybackEnabled");
        assertThat(PracticeFormFields.answerKey(66L)).isEqualTo("answer_66");
        assertThat(PracticeFormFields.isAnswerField("answer_66")).isTrue();
        assertThat(PracticeFormFields.questionIdFromAnswerField("answer_66")).isEqualTo("66");
        assertThat(PracticeMediaRoutes.playbackPath(1L, 2L, 3L))
                .isEqualTo("/practice/attempts/1/questions/2/speaking-media/3/content");
    }
}
