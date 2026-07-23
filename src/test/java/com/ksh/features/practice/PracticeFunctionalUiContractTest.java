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
    private static final Path SPEAKING_PREFLIGHT_JS =
            Path.of("src/main/resources/static/js/practice/speaking-preflight.js");
    private static final Path SPEAKING_PREFLIGHT_CSS =
            Path.of("src/main/resources/static/css/practice/speaking-preflight.css");
    private static final Path LISTENING_PREFLIGHT_JS =
            Path.of("src/main/resources/static/js/practice/listening-preflight.js");
    private static final Path EXAM_PLAYER_JS =
            Path.of("src/main/resources/static/js/practice/player-exam.js");
    private static final Path PRACTICE_ROUTES =
            Path.of("src/main/java/com/ksh/features/practice/web/PracticeRoutes.java");
    private static final Path PRACTICE_RESULT_CSS =
            Path.of("src/main/resources/static/css/practice-result.css");
    private static final Path PRACTICE_RESULT_JS =
            Path.of("src/main/resources/static/js/practice-result.js");
    private static final Path WRITING_RESULT_PRESENTER =
            Path.of("src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java");

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
        String preflight = Files.readString(PRACTICE_TEMPLATES.resolve("speaking-preflight.html"));
        String preflightJs = Files.readString(SPEAKING_PREFLIGHT_JS);
        String preflightCss = Files.readString(SPEAKING_PREFLIGHT_CSS);

        assertThat(testDetail).contains(
                "card.skill() == 'SPEAKING'",
                "/speaking-check",
                "Kiểm tra thiết bị để bắt đầu",
                "Kiểm tra micro để tiếp tục");
        assertThat(preflight).contains(
                "data-upload-enabled=${speakingMediaUploadEnabled}",
                "data-test-speaker",
                "data-record-sample",
                "data-record-label",
                "data-heard-confirm",
                "data-start-speaking",
                "data-service-notice",
                "Mẫu ghi âm chỉ dùng để kiểm tra trên thiết bị này và không được tải lên KSH.");
        int uploadDisabledBranchStart = preflightJs.indexOf("} else if (!uploadEnabled) {");
        int uploadDisabledBranchEnd = preflightJs.indexOf("} else {", uploadDisabledBranchStart);
        assertThat(uploadDisabledBranchStart).isGreaterThanOrEqualTo(0);
        assertThat(uploadDisabledBranchEnd).isGreaterThan(uploadDisabledBranchStart);
        assertThat(preflightJs.substring(uploadDisabledBranchStart, uploadDisabledBranchEnd))
                .contains("Bạn vẫn có thể phát âm thử và ghi âm mẫu trên thiết bị này.")
                .doesNotContain("recordButton.disabled = true");
        assertThat(preflightJs).contains(
                "window.MediaRecorder",
                "navigator.mediaDevices.getUserMedia",
                "window.AudioContext || window.webkitAudioContext",
                "gain.connect(context.destination)",
                "stream.getTracks().forEach",
                "blob.size > 0",
                "Dịch vụ lưu bản ghi Speaking đang tắt");
        assertThat(preflightJs).doesNotContain("fetch(\"http", "fetch('http");
        assertThat(preflightCss).contains(".spc-page", ".spc-panel", ".spc-meter", ".spc-mascot");
    }

    @Test
    void listeningPreflightRequiresSuccessfulPlaybackAndExplicitSpeakerConfirmation() throws IOException {
        String testDetail = Files.readString(PRACTICE_TEMPLATES.resolve("test-detail.html"));
        String preflight = Files.readString(PRACTICE_TEMPLATES.resolve("listening-preflight.html"));
        String preflightJs = Files.readString(LISTENING_PREFLIGHT_JS);
        String editor = Files.readString(PRACTICE_TEMPLATES.resolve("manage/editor.html"));

        assertThat(preflight).contains(
                "listeningCheckAudioReference",
                "data-confirm disabled",
                "data-continue disabled",
                "aria-disabled=\"true\"",
                "Bắt đầu phần Nghe");
        assertThat(testDetail).contains(
                "card.skill() == 'LISTENING'",
                "/listening-check",
                "Kiểm tra loa để bắt đầu",
                "Kiểm tra loa để tiếp tục");
        assertThat(preflightJs).contains(
                "audio.addEventListener('playing'",
                "playbackVerified = true",
                "!playbackVerified || !Number.isFinite(audio.duration)",
                "submit.disabled = !(playbackVerified && confirm.checked)",
                "['ArrowLeft', 'ArrowRight', 'Home', 'End']");
        assertThat(preflightJs).doesNotContain("completed = true", "nghe hết audio mẫu");
        assertThat(testDetail).contains(
                "th:if=\"${error}\"",
                "pd-feedback-error",
                "role=\"alert\"");
        assertThat(editor).contains(
                "listening-check-audio-area",
                "handleListeningCheckAudioSelect",
                "syncSectionContract(section)",
                "Audio thử loa trước phần Nghe");
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
    void canonicalResultUsesOneShellAndExactlyThreeSkillPresenters() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("result.html"));
        String objective = Files.readString(PRACTICE_TEMPLATES.resolve("result/objective.html"));
        String writing = Files.readString(PRACTICE_TEMPLATES.resolve("result/writing.html"));
        String speaking = Files.readString(PRACTICE_TEMPLATES.resolve("result/speaking.html"));
        String writingPresenter = Files.readString(WRITING_RESULT_PRESENTER);
        String css = Files.readString(PRACTICE_RESULT_CSS);
        String js = Files.readString(PRACTICE_RESULT_JS);

        assertThat(template).contains(
                "result.identity().testId()",
                "practice/result/objective :: panel",
                "practice/result/writing :: panel",
                "practice/result/speaking :: panel",
                "result.answers().scoredLabel()",
                "result.identity().skill() != 'SPEAKING'",
                "result.identity().skill() == 'SPEAKING'",
                "Phạm vi hồ sơ",
                "phần có hồ sơ ngôn ngữ")
                .doesNotContain("result.celebratory()", "pr-skill-mark");
        assertThat(Files.exists(PRACTICE_TEMPLATES.resolve("rl-result.html"))).isFalse();
        assertThat(Files.exists(PRACTICE_TEMPLATES.resolve("result/reading.html"))).isFalse();
        assertThat(Files.exists(PRACTICE_TEMPLATES.resolve("result/listening.html"))).isFalse();
        assertThat(objective).contains(
                "Đúng một phần",
                "Không thể chấm",
                "Điểm đạt được",
                "Tỷ lệ điểm",
                "row.pointsDisplay()",
                "row.scoreRateDisplay()",
                "th:case=\"'READY'\"",
                "th:case=\"'PARTIAL'\"",
                "th:case=\"'PENDING'\"",
                "th:case=\"'FAILED'\"",
                "th:case=\"'UNAVAILABLE'\"",
                "Xem đáp án và giải thích hiện có",
                "Xem đáp án")
                .doesNotContain("Độ chính xác", "row.accuracyDisplay()");
        assertThat(writingPresenter).contains(
                "Nhiệm vụ và Nội dung",
                "Cấu trúc và mạch lạc",
                "Từ vựng và Diễn đạt",
                "Ngữ pháp và Độ chính xác",
                "expectedMaxScore",
                "visibleCriteria")
                .doesNotContain("ResultEvaluationBand.fromPercentage");
        assertThat(writing).contains(
                "Đánh giá luyện tập KSH",
                "không phải điểm hoặc chứng chỉ TOPIK chính thức",
                "Thành phần tính điểm",
                "Phân bổ điểm theo từng ô trống",
                "không có điểm riêng và không cộng vào tổng điểm",
                "Không tính điểm",
                "task.evaluated()",
                "th:case=\"'PENDING'\"",
                "th:case=\"'FAILED'\"",
                "th:case=\"'UNAVAILABLE'\"",
                "th:tabindex=\"${status.first ? 0 : -1}\"",
                "th:tabindex=\"${lensStatus.first ? 0 : -1}\"",
                "<details class=\"pr-task-prompt\"",
                "pr-task-prompt-preview",
                "pr-task-prompt-full",
                "questionId=${task.questionId()}",
                "th:if=\"${task.detailAvailable()}\"",
                "th:unless=\"${task.detailAvailable()}\"",
                "Chi tiết riêng chưa khả dụng cho định dạng Writing lịch sử này",
                "Chưa có trang chi tiết cho nhiệm vụ này")
                .doesNotContain(
                        "Task Response", "Lexical Resource", "IELTS", "Band descriptors",
                        "criterion.band()", "lens.band()", "pr-scale", "th:hidden");
        assertThat(speaking).contains(
                "Phạm vi và độ tin cậy",
                "Hồ sơ ngôn ngữ dựa trên bản chép lời",
                "Kết quả Nói tổng hợp",
                "Không có điểm số",
                "criterion.scored()",
                "criterion.notScorable()",
                "Kế hoạch luyện tập tiếp theo",
                "Xem bản chép lời và bằng chứng chi tiết")
                .doesNotContain(
                        "Câu 1", "data-result-tabs=\"speaking", "IELTS", "radar",
                        "criterion.band()", "criterion.percentage()", "pr-scale");
        assertThat(css).contains(
                "--pr-blue:", "--pr-green:", "--pr-amber:", "--pr-red:", "--pr-gray:",
                ".pr-speaking-action-plan", ".pr-next-action", ".pr-table td::before",
                ".pr-writing-scorecard", ".pr-writing-lenses", ".pr-writing-state",
                ".pr-task-action-unavailable",
                ".pr-speaking-profile-state", ".pr-speaking-provenance",
                ".pr-speaking-criterion.is-not-scorable",
                ".pr-task-prompt-preview", "-webkit-line-clamp: 3", ".pr-task-actions",
                ".practice-result-page summary:focus-visible", "@media (max-width: 720px)")
                .doesNotContain(
                        "linear-gradient", "radial-gradient", "min-width: 800px",
                        ".pr-band-chip", ".pr-scale", "radar");
        assertThat(js).contains(
                "[data-result-tabs]", "aria-selected", "hidden",
                "ArrowRight", "ArrowLeft", "Home", "End");
    }

    @Test
    void canonicalResultBackLinkUsesImmutableAttemptIdentity() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("result.html"));

        assertThat(template).contains("result.identity().testId()");
        assertThat(template).contains("/practice/sets/{setId}/tests/{testId}");
    }

    @Test
    void resultDetailUsesThreeTypedServerRenderedScreenBoundaries() throws IOException {
        String objective = Files.readString(
                PRACTICE_TEMPLATES.resolve("result-detail-objective.html"));
        String writing = Files.readString(
                PRACTICE_TEMPLATES.resolve("result-detail-writing.html"));
        String speaking = Files.readString(
                PRACTICE_TEMPLATES.resolve("result-detail-speaking.html"));
        String detailCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice-result-detail.css"));
        String tabsJs = Files.readString(PRACTICE_RESULT_JS);

        assertThat(objective).contains(
                "data-result-detail-kind=\"OBJECTIVE_DETAIL\"",
                "resultDetail.payload().summary().breakdown()",
                "resultDetail.payload().sourceGroups()",
                "resultDetail.payload().questions()",
                "data-objective-question-type",
                "Nguồn và bằng chứng gốc",
                "Đáp án người học",
                "Đáp án chính thức",
                "Giải thích của giáo viên",
                "Lời giải AI",
                "Dịch đoạn liên quan",
                "Bản chép lời đã được phê duyệt",
                "Phương án, trạng thái người học và lý do loại chọn",
                "Giá trị từng ô trống và đáp án được chấp nhận",
                "Vì sao không phải",
                "Digest tài sản",
                "evidenceTranslations()",
                "Gắn với bằng chứng",
                "tabindex=\"-1\"")
                .doesNotContain(
                        "groupsJson", "JSON.parse", "questionsJson",
                        "IELTS", "Band", "Task Response", "Lexical Resource",
                        "th:utext", "pageIndex()");
        assertThat(detailCss).contains(
                ".prd-objective-layout",
                ".prd-objective-nav-list a:focus-visible",
                ".prd-objective-question:focus",
                "@media (max-width: 980px)",
                "@media (max-width: 640px)");
        assertThat(writing).contains(
                "data-result-detail-kind=\"WRITING_DETAIL\"",
                "resultDetail.payload().scoreCriteria()",
                "resultDetail.payload().diagnosticGroups()",
                "group.strengthChips()",
                "group.needsImprovementChips()",
                "chip.labelVi()",
                "chip.labelKo()",
                "chip.count()",
                "resultDetail.payload().scoreProfileId()",
                "task.score().pointsDisplay()",
                "task.feedback().label()",
                "task.feedback().stateLabelKo()",
                "data-writing-diagnostic-filter",
                "data-writing-feature",
                "aria-pressed",
                "data-result-tabs",
                "role=\"tabpanel\"",
                "aria-controls",
                "aria-labelledby",
                "aria-selected",
                "Tổng quan",
                "개요",
                "Điểm mạnh",
                "강점",
                "Cần cải thiện",
                "개선 필요",
                "Bài nâng cấp",
                "개선된 답안",
                "Không có đề bài bất biến khả dụng",
                "Người học chưa nộp câu trả lời",
                "/js/practice-result.js",
                "name=\"questionId\"",
                "_csrf.parameterName")
                .doesNotContain(
                        "questionsJson", "JSON.parse", "Content", "Coherence",
                        "Bài mẫu", "data-tab=\"sample\"", "th:utext",
                        "teacherReference()", "Bài tham khảo của giáo viên",
                        "교사 참고 답안");
        assertThat(writing.split("role=\"tab\"", -1)).hasSize(5);
        assertThat(writing.indexOf("resultDetail.payload().scoreCriteria()"))
                .isBetween(
                        writing.indexOf("th:id=\"${'writing-panel-overview-"),
                        writing.indexOf("th:id=\"${'writing-panel-strengths-"));
        assertThat(detailCss).contains(
                ".prd-writing-review-layout",
                ".prd-writing-tabs",
                ".prd-writing-tab.is-active",
                ".prd-writing-tab:focus-visible",
                ".prd-writing-panel[hidden]",
                "@media (max-width: 940px)");
        assertThat(tabsJs).contains(
                "[data-result-tabs]",
                "aria-selected",
                "ArrowRight",
                "ArrowLeft",
                "Home",
                "End",
                "nextTab.focus()",
                "[data-writing-diagnostic-filter]",
                "aria-pressed",
                "finding.hidden",
                "firstMatch.focus",
                "firstMatch.scrollIntoView",
                "[data-speaking-diagnostic-filter]",
                "dataset.speakingFeature")
                .doesNotContain("JSON.parse", "JSON.stringify");
        assertThat(speaking).contains(
                "data-result-detail-kind=\"SPEAKING_DETAIL\"",
                "data-speaking-active-question",
                "data-speaking-evidence-mode",
                "data-speaking-recording-state",
                "data-speaking-acoustic-state",
                "resultDetail.payload().scoreCriteria()",
                "resultDetail.payload().diagnosticGroups()",
                "resultDetail.payload().evidence().transcriptText()",
                "resultDetail.payload().upgrade().learnerDerivedUpgrade()",
                "data-speaking-diagnostic-filter",
                "Tổng quan",
                "개요",
                "Điểm mạnh",
                "강점",
                "Cần cải thiện",
                "개선 필요",
                "Bài nâng cấp",
                "개선된 답변",
                "không chứng minh bộ đánh giá đã nghe âm thanh")
                .doesNotContain(
                        "questionsJson", "JSON.parse", "Content", "Coherence",
                        "holistic", "subtotal", "AUDIO_SUBMITTED",
                        "S_FLUENCY_", "S_PRONUNCIATION_");
        assertThat(speaking.split("role=\"tab\"", -1)).hasSize(5);
        assertThat(detailCss).contains(
                ".prd-speaking-tabs",
                ".prd-speaking-panel[hidden]",
                ".prd-speaking-chip[aria-pressed=\"true\"]",
                ".prd-speaking-recording audio",
                "@media (max-width: 980px)");
    }

    @Test
    void dedicatedExamPlayersShareNavigationSafetyAndAdaptiveReadingContracts() throws IOException {
        String player = Files.readString(PRACTICE_TEMPLATES.resolve("player.html"));
        String writingPlayer = Files.readString(PRACTICE_TEMPLATES.resolve("player-writing.html"));
        String js = Files.readString(EXAM_PLAYER_JS);

        assertThat(player).contains(
                "data-question-stage",
                "data-has-source",
                "data-long-source",
                "data-exit-link",
                "data-selection-highlight",
                "data-selection-note");
        assertThat(writingPlayer).contains(
                "player-writing.css",
                "data-question-stage",
                "data-writing-answer",
                "data-exit-link");
        assertThat(js).contains(
                "[data-question-stage]",
                "layout-focus",
                "layout-stacked",
                "layout-split",
                "configured <= 0",
                "ksh-exam-timer:v2:${attemptId}",
                "storedValue === null ? Number.NaN",
                "[data-exit-link]",
                "'contextmenu', 'copy', 'cut', 'paste'",
                "startRegion === endRegion");
    }

    @Test
    void playerRendersPublishedMediaReferencesFromAuthoring() throws IOException {
        String player = Files.readString(PRACTICE_TEMPLATES.resolve("player.html"));

        assertThat(player).contains(
                "g.imageUrl()",
                "g.passageText()",
                "q.imageReference()",
                "q.audioReference()",
                "q.optionRows()",
                "option.imageReference()");
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

        assertThat(PracticeViews.RESULT_DETAIL_OBJECTIVE)
                .isEqualTo("practice/result-detail-objective");
        assertThat(PracticeViews.RESULT_DETAIL_WRITING)
                .isEqualTo("practice/result-detail-writing");
        assertThat(PracticeViews.RESULT_DETAIL_SPEAKING)
                .isEqualTo("practice/result-detail-speaking");
        assertThat(PracticeViews.CATALOG_CARDS)
                .isEqualTo("practice/fragments/catalog-cards :: cards");
        assertThat(PracticeModelAttributes.CATALOG).isEqualTo("catalog");
        assertThat(PracticeModelAttributes.RESULT_DETAIL).isEqualTo("resultDetail");
        assertThat(PracticeModelAttributes.SPEAKING_MEDIA_PLAYBACK_ENABLED)
                .isEqualTo("speakingMediaPlaybackEnabled");
        assertThat(PracticeFormFields.answerKey(66L)).isEqualTo("answer_66");
        assertThat(PracticeFormFields.isAnswerField("answer_66")).isTrue();
        assertThat(PracticeFormFields.questionIdFromAnswerField("answer_66")).isEqualTo("66");
        assertThat(PracticeMediaRoutes.playbackPath(1L, 2L, 3L))
                .isEqualTo("/practice/attempts/1/questions/2/speaking-media/3/content");
    }
}
