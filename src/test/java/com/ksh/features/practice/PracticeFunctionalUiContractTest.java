package com.ksh.features.practice;

import com.ksh.features.practice.web.PracticeFormFields;
import com.ksh.features.practice.web.PracticeMediaRoutes;
import com.ksh.features.practice.web.PracticeModelAttributes;
import com.ksh.features.practice.web.PracticeRoutes;
import com.ksh.features.practice.web.PracticeViews;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
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
    private static final Path PRACTICE_RESULT_PREP_CSS =
            Path.of("src/main/resources/static/css/practice-result-prep.css");
    private static final Path PRACTICE_RESULT_JS =
            Path.of("src/main/resources/static/js/practice-result.js");
    private static final Path WRITING_RESULT_PRESENTER =
            Path.of("src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java");
    private static final Path PRACTICE_CONTROLLER =
            Path.of("src/main/java/com/ksh/features/practice/controller/PracticeController.java");
    private static final Path PRACTICE_SERVICE =
            Path.of("src/main/java/com/ksh/features/practice/service/PracticeService.java");
    private static final Path PRACTICE_ATTEMPT_REPOSITORY =
            Path.of("src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java");

    @Test
    void typedObjectivePlayerAndAttemptScopedPinsAreFunctional() throws IOException {
        String player = Files.readString(PRACTICE_TEMPLATES.resolve("player.html"));
        String playerJs = Files.readString(EXAM_PLAYER_JS);
        String playerCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice/player.css"));

        assertThat(player).contains(
                "data-multiple-question",
                "data-multiple-option",
                "data-multiple-answer",
                "data-matching-question",
                "data-matching-target",
                "data-matching-picker",
                "data-matching-picker-list",
                "aria-haspopup=\"listbox\"",
                "data-matching-answer",
                "data-material-pin",
                "aria-pressed=\"false\"",
                "Ghim câu hỏi");
        assertThat(playerJs).contains(
                "questionType: 'MULTIPLE_ANSWER'",
                "selectedOptionIds:",
                "questionType: 'MATCHING'",
                "blankAnswers: blankAnswers",
                "initializeMatchingPickers()",
                "target.setAttribute('aria-expanded', 'true')",
                "option.setAttribute(",
                "target.dispatchEvent(new Event('change', { bubbles: true }))",
                "syncTypedObjectiveAnswers()",
                "ksh:practice-player:${attemptId}:pinned-questions",
                "ksh:practice-player:${attemptId}:pinned-material-groups",
                "window.localStorage.setItem",
                "button.setAttribute('aria-pressed', String(pinned))",
                "pane.scrollTop = 0",
                "document.documentElement.scrollTop = 0",
                "document.body.scrollTop = 0");
        assertThat(playerCss).contains(
                ".exam-matching-candidates",
                ".exam-matching-targets",
                ".exam-matching-picker-list",
                ".exam-matching-picker.is-open",
                ".exam-workspace.has-pinned-material .exam-source-pane",
                ".exam-source-pin:focus-visible",
                "overflow: clip",
                "height: calc(100dvh - 224px)",
                "max-height: none");
        assertThat(playerCss).doesNotContain("max-height: 42dvh");
        assertThat(playerJs).doesNotContain(
                "behavior: reducedMotion.matches ? 'auto' : 'smooth'");
    }

    @Test
    void objectiveResultDetailUsesPrepStatesSquareGridsPinAndLocalHelp() throws IOException {
        String detail = Files.readString(
                PRACTICE_TEMPLATES.resolve("result-detail-objective.html"));
        String resultJs = Files.readString(PRACTICE_RESULT_JS);
        String detailCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice-result-detail-prep.css"));

        assertThat(detail).contains(
                "data-objective-material-pin",
                "data-objective-helper-toggle",
                "data-objective-helper",
                "aria-modal=\"true\"",
                "data-objective-helper-backdrop",
                "data-objective-splitter",
                "role=\"separator\"",
                "aria-valuemin=\"32\"",
                "aria-valuemax=\"68\"",
                "'MATCHING_MATRIX'",
                "M18 6 6 18M6 6l12 12",
                "m5 12 4 4L19 6");
        assertThat(detail).doesNotContain(
                "target với nhãn authoritative",
                "data-objective-question-link",
                "prd-objective-nav-list");
        assertThat(resultJs).contains(
                "ksh:practice-result-detail:${objectiveAttemptId}:pinned-material-groups",
                "ksh:practice-result-detail:${objectiveAttemptId}:split-ratio",
                "[data-objective-splitter]",
                "--prd-objective-split",
                "splitter.setPointerCapture(event.pointerId)",
                "window.localStorage.setItem",
                "button.setAttribute('aria-pressed', String(pinned))",
                "helper.removeAttribute('inert')",
                "helper.setAttribute('inert', '')",
                "event.key === 'Escape'",
                "event.key !== 'Tab'");
        assertThat(detailCss).contains(
                ".prd-objective-option.is-correct",
                ".prd-objective-option.is-selected_incorrect",
                ".prd-objective-option.is-user_selected_pending",
                "border-collapse: collapse",
                ".prd-objective-splitter",
                "grid-template-columns: minmax(0, var(--prd-objective-split)) 12px minmax(0, 1fr)",
                "--prd-objective-bottom-nav: 62px",
                ".prd-objective-group-panel.is-material-pinned",
                ".prd-objective-helper.is-open",
                "@media (prefers-reduced-motion: reduce)");
    }

    @Test
    void detailPagesUsePerTestAndPerSkillContracts() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("set-detail.html"));
        String testDetail = Files.readString(PRACTICE_TEMPLATES.resolve("test-detail.html"));

        assertThat(template).contains("th:each=\"test, iter : ${testCards}\"");
        assertThat(template).contains("testId=${test.id()}");
        assertThat(template).doesNotContain("testId=${view.set().id()}");
        assertThat(testDetail).contains("th:each=\"card : ${skillCards}\"");
        assertThat(testDetail).contains(
                "card.completedAttempts()",
                "attempt.resultEligible()",
                "snapshot tương thích",
                "data-attempt-toggle");
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
                "Dịch vụ lưu bản ghi phần Nói đang tắt");
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
                "Âm thanh thử loa trước phần Nghe");
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
        String flowCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice-flow-polish.css"));
        String mainRule = catalogCss.substring(
                catalogCss.indexOf(".pc-main {"),
                catalogCss.indexOf(".pc-layout {"));

        assertThat(template).contains("catalog.totalElements()");
        assertThat(template).contains(
                "catalog.totalElements() == 1",
                "pc-card-grid",
                "is-single");
        assertThat(template).contains(
                "catalog.globalResume()",
                "resume.attemptId()",
                "resume.setTitle()",
                "resume.testTitle()",
                "resume.skillLabel()");
        assertThat(template).doesNotContain("catalog.resumeCard()");
        assertThat(template).contains("pc-resume-banner");
        assertThat(template).contains("pc-skill-pill");
        assertThat(template).contains(
                "name=\"writingTask\"",
                "catalog.writingTask()",
                "writingTask='Q51'",
                "writingTask='Q52'",
                "writingTask='Q53'",
                "writingTask='Q54'",
                "Câu 51 · 51번",
                "Câu 54 · 54번");
        assertThat(template).doesNotContain(
                "name=\"classId\"",
                "classId=",
                "Mọi lớp học",
                "Lớp đang tham gia");
        assertThat(template).doesNotContain(
                "data-endpoint=@{/practice/catalog}");
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
        assertThat(cards).doesNotContain("pc-resume-banner", "globalResume()");
        assertThat(cards).contains("pc-card-book");
        assertThat(cards).contains("bài test");
        assertThat(catalogCss).contains("pc-skill-mixed");
        assertThat(catalogCss).contains("pc-card-skill-icons");
        assertThat(catalogCss).contains("grid-template-columns: repeat(4, minmax(0, 1fr))");
        assertThat(catalogCss).contains(
                "@media (max-width: 1180px)",
                "grid-template-columns: repeat(3, minmax(0, 1fr))",
                "@media (max-width: 760px)",
                "grid-template-columns: repeat(2, minmax(0, 1fr))",
                "@media (max-width: 560px)",
                "grid-template-columns: minmax(0, 1fr)");
        assertThat(flowCss).contains(
                ".practice-flow-home .pc-card-grid.is-single",
                "grid-template-columns: minmax(280px, calc((100% - 16px) / 2))",
                "justify-content: start");
        assertThat(mainRule)
                .contains("min-width: 0")
                .doesNotContain("width: 100%", "margin-left");
        assertThat(catalogJs).doesNotContain(
                "IntersectionObserver",
                "params.set('batch'",
                "grid.appendChild");
        assertThat(template).contains(
                "pc-pagination",
                "catalog.previousBatch()",
                "catalog.nextBatch()",
                "rel=\"prev\"",
                "rel=\"next\"");
        assertThat(template).doesNotContain(
                "pc-catalog-loader",
                "Bật JavaScript để tự động tải thêm");
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
    void globalResumeAndReEvaluationGateStayOutsideCardAndProviderPaths()
            throws IOException {
        String service = Files.readString(PRACTICE_SERVICE);
        String repository = Files.readString(PRACTICE_ATTEMPT_REPOSITORY);
        int globalCatalogResumeStart = repository.indexOf(
                "Resume candidate for the standalone public Practice catalogue");
        int legacyClassAwareResumeStart = repository.indexOf(
                "Legacy class-aware resume lookup retained", globalCatalogResumeStart);
        String globalCatalogResume = repository.substring(
                globalCatalogResumeStart, legacyClassAwareResumeStart);
        int reEvaluateStart = service.indexOf(
                "public Long reEvaluate(Long attemptId, Long userId)");
        int questionEntry = service.indexOf(
                "public Long reEvaluateQuestion", reEvaluateStart);
        int commonGate = service.indexOf(
                "private PracticeAttempt requireReEvaluationAttempt",
                questionEntry);
        String fullCommand = service.substring(reEvaluateStart, questionEntry);
        String questionCommand = service.substring(questionEntry, commonGate);
        int transactionFallback = service.indexOf(
                "private Long reEvaluateInTransaction", commonGate);
        String gate = service.substring(commonGate, transactionFallback);

        assertThat(fullCommand).contains(
                "requireReEvaluationAttempt",
                "loadWritingReEvaluationSnapshot",
                "loadNonWritingEssayReEvaluationSnapshot");
        assertThat(fullCommand.indexOf("requireReEvaluationAttempt"))
                .isLessThan(
                        fullCommand.indexOf(
                                "loadWritingReEvaluationSnapshot"));
        assertThat(fullCommand).doesNotContain(
                "loadSpeakingReEvaluationSnapshot",
                "gradeSpeakingSnapshot");
        assertThat(questionCommand).contains(
                "requireReEvaluationAttempt",
                "loadWritingQuestionReEvaluationSnapshot");
        assertThat(questionCommand.indexOf("requireReEvaluationAttempt"))
                .isLessThan(questionCommand.indexOf(
                        "loadWritingQuestionReEvaluationSnapshot"));
        assertThat(gate).contains(
                "findByIdAndUserId",
                "reEvaluationEligibility",
                "hasCoherentAttemptIdentity",
                "loadPublished");
        assertThat(gate.indexOf("findByIdAndUserId"))
                .isLessThan(gate.indexOf("reEvaluationEligibility"));
        assertThat(gate.indexOf("reEvaluationEligibility"))
                .isLessThan(gate.indexOf("hasCoherentAttemptIdentity"));
        assertThat(gate.indexOf("hasCoherentAttemptIdentity"))
                .isLessThan(gate.indexOf("loadPublished"));
        assertThat(gate).doesNotContain(
                "loadQuestionSnapshots",
                "evaluateQuestion",
                "gradeWritingSnapshot",
                "saveAndFlush");
        assertThat(globalCatalogResume).contains(
                "findGlobalCatalogResumeCandidates",
                "a.user_id = :userId",
                "s.status = 'PUBLISHED'",
                "s.is_deleted = 0",
                "s.scope = 'GLOBAL'",
                "COALESCE(a.submitted_at, a.updated_at, a.created_at) DESC",
                "a.id DESC")
                .doesNotContain(
                        "activeClassIds",
                        "s.class_id",
                        "s.created_by");
        assertThat(repository).contains(
                "Legacy class-aware resume lookup retained",
                "findGlobalResumeCandidates");
    }

    @Test
    void canonicalResultUsesOneShellAndExactlyThreeSkillPresenters() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("result.html"));
        String objective = Files.readString(PRACTICE_TEMPLATES.resolve("result/objective.html"));
        String writing = Files.readString(PRACTICE_TEMPLATES.resolve("result/writing.html"));
        String speaking = Files.readString(PRACTICE_TEMPLATES.resolve("result/speaking.html"));
        String writingPresenter = Files.readString(WRITING_RESULT_PRESENTER);
        String css = Files.readString(PRACTICE_RESULT_CSS);
        String prepCss = Files.readString(PRACTICE_RESULT_PREP_CSS);
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
                "phần có hồ sơ ngôn ngữ",
                "pr-summary-writing-breakdown",
                "pr-notebook-divider",
                "Phạm vi điểm",
                "không phải điểm hoặc chứng chỉ TOPIK chính thức",
                "data-result-celebration-eligible",
                "result.state().code() == 'GRADED' or result.state().code() == 'SUBMITTED'",
                "data-result-celebration-key",
                "data-result-celebration",
                "pr-sky-particles")
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
                "task.evaluated()",
                "th:case=\"'PENDING'\"",
                "th:case=\"'FAILED'\"",
                "th:case=\"'UNAVAILABLE'\"",
                "th:tabindex=\"${status.first ? 0 : -1}\"",
                "th:hidden=\"${!taskStatus.first}\"",
                "pr-task-prompt-full",
                "task.criterionGroups()",
                "criterion.performanceCssClass()",
                "criterion.performanceLabel()",
                "questionId=${task.questionId()}",
                "th:if=\"${task.detailAvailable()}\"",
                "th:unless=\"${task.detailAvailable()}\"",
                "Chưa có trang chi tiết cho nhiệm vụ này")
                .doesNotContain(
                        "Task Response", "Lexical Resource", "IELTS", "Band descriptors",
                        "criterion.band()", "lens.band()", "pr-scale",
                        "Thành phần tính điểm", "<details class=\"pr-task-prompt\"",
                        "pr-task-prompt-preview", "Xem toàn bộ đề bài", "pr-task-type",
                        "Phân bổ điểm theo từng ô trống", "pr-task-summary",
                        "pr-writing-lenses", "writing-lens-tab-",
                        "Chẩn đoán để luyện tiếp",
                        "Đánh giá luyện tập KSH",
                        "không phải điểm hoặc chứng chỉ TOPIK chính thức");
        assertThat(speaking).contains(
                "Phạm vi và độ tin cậy",
                "Hồ sơ ngôn ngữ dựa trên bản chép lời",
                "Kết quả Nói tổng hợp",
                "Hồ sơ ngôn ngữ từ bản chép lời",
                "pr-speaking-criteria-dashboard",
                "pr-speaking-radar-value",
                "result.payload().radarPolygonPoints()",
                "axis.percentage()",
                "criterion.scored()",
                "criterion.notScorable()",
                "criterion.performanceCssClass()",
                "criterion.performanceLabel()",
                "#lists.size(result.payload().strengths())",
                "#lists.size(result.payload().needsImprovement())",
                "actionStatus.index &lt; 3",
                "data-result-tabs=\"speaking-overview-criteria\"",
                "KSH không suy luận độ lưu loát, phát âm hoặc thể hiện từ bản chép lời",
                "Kế hoạch luyện tập tiếp theo",
                "Xem bản chép lời và bằng chứng chi tiết")
                .doesNotContain(
                        "근거 범위 및 출처",
                        "평가를 제공할 수 없습니다",
                        "개 기준",
                        "Câu 1", "IELTS", "Band descriptors",
                        "criterion.percentage()", "pr-scale");
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
                        ".pr-band-chip", ".pr-scale");
        assertThat(js).contains(
                "[data-result-tabs]", "aria-selected", "hidden",
                "ArrowRight", "ArrowLeft", "Home", "End",
                "runResultCelebration",
                "celebration.dataset.played === 'true'",
                "celebration.dataset.played = 'true'",
                "__KSH_DISABLE_RESULT_MOTION__",
                "document.documentElement.dataset.practiceMotion === 'off'",
                "reducedMotion.matches",
                "window.requestAnimationFrame",
                "celebration.replaceChildren()");
        assertThat(prepCss).contains(
                ".pr-result-celebration-piece.is-dot",
                ".pr-result-celebration-piece.is-star",
                ".pr-result-celebration-piece.is-dash",
                "@keyframes pr-result-confetti-burst",
                "@keyframes pr-result-cloud-bob",
                "@keyframes pr-sky-particle-pop",
                "@keyframes pr-baekho-sprite",
                "var(--celebration-delay) 1 both",
                "@media (prefers-reduced-motion: reduce)",
                "html[data-practice-motion=\"off\"]")
                .doesNotContain("pr-baekho-celebrate");
    }

    @Test
    void canonicalResultBackLinkUsesImmutableAttemptIdentity() throws IOException {
        String template = Files.readString(PRACTICE_TEMPLATES.resolve("result.html"));

        assertThat(template).contains("result.identity().testId()");
        assertThat(template).contains("/practice/sets/{setId}/tests/{testId}");
    }

    @Test
    void speakingRecoveryOpensCanonicalOtherAttemptPreflightWithoutChangingScreens()
            throws IOException {
        String overview = Files.readString(
                PRACTICE_TEMPLATES.resolve("result/speaking.html"));
        String resultDetail = Files.readString(
                PRACTICE_TEMPLATES.resolve("result-detail-speaking.html"));

        assertThat(overview)
                .contains(
                        "result.feedback().state() == 'FAILED' or result.feedback().state() == 'UNAVAILABLE'",
                        "Lần nộp này được lưu bất biến và không thể đánh giá lại",
                        "“Luyện lại” mở bước chuẩn bị cho một lượt khác",
                        "Nếu đã có một lượt khác đang làm dở và còn hợp lệ",
                        "/practice/sets/{setId}/tests/{testId}",
                        "result.identity().setId()",
                        "result.identity().testId()",
                        "Luyện lại",
                        "/practice/attempts/{id}/result/detail")
                .doesNotContain(
                        "<form",
                        "Luyện lại · 다시 연습",
                        "/re-evaluate",
                        "retry provider",
                        "sẽ bắt đầu một lần làm bài mới");
        assertThat(resultDetail)
                .contains("data-result-detail-kind=\"SPEAKING_DETAIL\"")
                .doesNotContain(
                        "Luyện lại · 다시 연습",
                        "/re-evaluate",
                        "pr-speaking-recovery");
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
        String objectivePrepCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice-result-detail-prep.css"));
        String tabsJs = Files.readString(PRACTICE_RESULT_JS);

        assertThat(objective).contains(
                "data-result-detail-kind=\"OBJECTIVE_DETAIL\"",
                "resultDetail.payload().summary().breakdown()",
                "resultDetail.payload().groups()",
                "group.questions()",
                "group.source()",
                "data-objective-group-panel",
                "data-objective-group-key",
                "data-objective-question-type",
                "Bản chép lời đã được phê duyệt",
                "không thay thế dữ liệu âm thanh",
                "data-option-state",
                "option.statusLabelVi()",
                "Các phương án và trạng thái kết quả",
                "Lời giải đáp án",
                "Kết quả từng ô trống",
                "Câu trả lời của bạn",
                "DẤU HIỆU ĐIỀN TỪ",
                "prd-objective-fill-evidence",
                "evidenceTranslations()",
                "strategyLabelVi()",
                "strategyDescriptionVi()",
                "EXACT_EVIDENCE_ONLY",
                "FULL_SOURCE_INLINE_HIGHLIGHT",
                "QUESTION_EVIDENCE_TRANSLATION_TABLE",
                "MCQ_OPTION_ELIMINATION",
                "EVIDENCE_AND_ELIMINATION",
                "TFNG_CONTRADICTION_TABLE",
                "NOT_GIVEN_BOUNDARY",
                "FILL_SLOT_GRAMMAR_ANALYSIS",
                "KEYWORD_PARAPHRASE_BRIDGE",
                "BILINGUAL_STEP_BY_STEP",
                "tabindex=\"-1\"")
                .containsOnlyOnce(
                        "class=\"prd-objective-explanation is-ready\"")
                .doesNotContain(
                        "groupsJson", "JSON.parse", "questionsJson",
                        "Chiến lược đã được giảng viên duyệt",
                        "Phương án, trạng thái người học và lý do loại chọn",
                        "Người học đã chọn", "Không chọn", "Vì sao loại",
                        "blankId()", "normalizationPolicy()",
                        "Đối chiếu toàn bộ bằng chứng đã kiểm chứng",
                        "Mã dẫn chứng", "Digest tài sản",
                        "data-strategy-code", "data-strategy-renderer",
                        "aiArtifactProvenance()", "learnerAnswerProvenance()",
                        "legacyFallback()", "constructRegistryNote()",
                        "prd-objective-inline-evidence",
                        "prd-objective-all-evidence",
                        "prd-objective-tfng-proof",
                        "prd-objective-explanation-link",
                        "Xem lời giải",
                        "IELTS", "Band", "Task Response", "Lexical Resource",
                        "th:utext", "pageIndex()");
        assertThat(detailCss).contains(
                ".prd-objective-layout",
                ".prd-objective-nav-list a:focus-visible",
                ".prd-objective-question:focus",
                ".prd-objective-group-panel",
                ".prd-objective-option.is-user_selected_pending",
                ".prd-objective-option.is-correct",
                ".prd-objective-option.is-selected_incorrect",
                ".prd-objective-group-nav",
                "max-width: 100%",
                "@media (max-width: 980px)",
                "@media (max-width: 640px)");
        assertThat(objectivePrepCss).contains(
                ".prd-objective-nav-list {",
                "overflow-x: auto",
                "right: calc(49.3% + 6px)",
                "--prd-objective-bottom-nav: 104px",
                ".prd-objective-question + .prd-objective-question",
                "position: fixed",
                "bottom: 0",
                ".prd-objective-fill-evidence",
                ".prd-objective-typed-strategy",
                ".prd-objective-typed-table");
        assertThat(writing).contains(
                "prd-body prd-writing-body",
                "data-result-detail-kind=\"WRITING_DETAIL\"",
                "data-writing-task-type",
                "data-writing-splitter",
                "prd-header-actions",
                "prd-header-reevaluate",
                "role=\"separator\"",
                "aria-valuemin=\"35\"",
                "aria-valuemax=\"65\"",
                "resultDetail.payload().scoreCriteria()",
                "resultDetail.payload().diagnosticGroups()",
                "resultDetail.payload().learnerAnswerSegments()",
                "prd-writing-inline-annotation",
                "segment.explanationVi()",
                "segment.correctionKo()",
                "group.strengthChips()",
                "group.needsImprovementChips()",
                "chip.labelVi()",
                "chip.count()",
                "resultDetail.payload().scoreProfileId()",
                "task.score().pointsDisplay()",
                "task.feedback().label()",
                "data-writing-diagnostic-filter",
                "data-writing-upgrade-filter",
                "prd-writing-upgrade-criterion",
                "Tiêu chí được xử lý trong bài nâng cấp",
                "data-writing-feature",
                "data-writing-operation",
                "finding.operation()",
                "finding.descriptorId()",
                "data-writing-features=${segment.featureIds()}",
                "data-writing-finding-ids=${segment.findingIds()}",
                "data-writing-number-feature=${membership.descriptorId()}",
                "data-writing-occurrence=${finding.findingId()}",
                "data-writing-occurrence-trigger",
                "prd-occurrence-detail",
                "data-writing-zero-chip-empty",
                "practice/fragments/icons :: overview",
                "practice/fragments/icons :: strength",
                "practice/fragments/icons :: improvement",
                "practice/fragments/icons :: upgrade",
                "practice/fragments/icons :: sample",
                "data-writing-filter-status",
                "aria-pressed",
                "data-result-tabs",
                "role=\"tabpanel\"",
                "aria-controls",
                "aria-labelledby",
                "aria-selected",
                "Tổng quan",
                "Điểm mạnh",
                "Cần cải thiện",
                "Bài nâng cấp",
                "<span>Mẫu</span>",
                "resultDetail.payload().teacherSample().available()",
                "Bài mẫu do giáo viên soạn",
                "data-writing-teacher-sample",
                "Không có đề bài bất biến khả dụng",
                "Người học chưa nộp câu trả lời",
                "/js/practice-result.js",
                "name=\"questionId\"",
                "_csrf.parameterName")
                .doesNotContain(
                        "questionsJson", "JSON.parse", "Content", "Coherence",
                        "<span lang=\"ko\">개요</span>",
                        "<span lang=\"ko\">강점</span>",
                        "<span lang=\"ko\">개선 필요</span>",
                        "task.feedback().stateLabelKo()",
                        "chip.labelKo()",
                        "data-tab=\"sample\"", "th:utext",
                        "teacherReference()", "Bài tham khảo của giáo viên",
                        "교사 참고 답안");
        assertThat(writing.split("role=\"tab\"", -1)).hasSize(6);
        assertThat(writing.indexOf("resultDetail.payload().scoreCriteria()"))
                .isBetween(
                        writing.indexOf("th:id=\"${'writing-panel-overview-"),
                        writing.indexOf("th:id=\"${'writing-panel-strengths-"));
        assertThat(detailCss).contains(
                ".prd-writing-review-layout",
                "--writing-split: 52%",
                ".prd-writing-splitter",
                ".prd-writing-feedback,",
                ".prd-speaking-feedback {",
                "container-type: inline-size",
                "overflow-x: hidden",
                "overscroll-behavior-x: contain",
                ".prd-writing-splitter span,",
                ".prd-speaking-splitter span {",
                "@container (max-width: 580px)",
                "margin-top: 72px",
                "margin-top: 8px",
                ".prd-writing-upgrade-criterion-grid",
                ".prd-writing-inline-annotation.is-selected.is-upgrade",
                ".prd-writing-annotated-answer",
                "white-space: normal",
                ".prd-writing-tabs",
                ".prd-writing-tab.is-active",
                ".prd-writing-tab:focus-visible",
                ".prd-writing-panel[hidden]",
                ".prd-writing-inline-tooltip",
                ".prd-inline-floating-tooltip",
                ".prd-writing-score-card progress",
                "grid-template-columns: minmax(140px, 1fr) minmax(72px, 0.32fr) minmax(160px, 0.9fr)",
                ".prd-speaking-score-card > span",
                "overflow-wrap: break-word",
                "@media (max-width: 940px)");
        assertThat(tabsJs).contains(
                "[data-result-tabs]",
                "aria-selected",
                "const activeQuestionLink = questionLinks.find",
                "inline: 'center'",
                "ArrowRight",
                "ArrowLeft",
                "Home",
                "End",
                "nextTab.focus()",
                "[data-writing-diagnostic-filter]",
                "[data-writing-upgrade-filter]",
                "[data-writing-occurrence-trigger]",
                "data-writing-finding-ids",
                "data-writing-number-feature",
                "data-writing-zero-chip-empty",
                "activateOccurrence",
                "[data-writing-splitter]",
                "--writing-split",
                "aria-pressed",
                "finding.hidden",
                "resetDiagnosticState",
                "positionAnnotationTooltip",
                "cloneNode(true)",
                "[data-speaking-diagnostic-filter]",
                "dataset.speakingFeature")
                .doesNotContain(
                        "JSON.parse", "JSON.stringify",
                        "firstMatch.focus", "firstMatch.scrollIntoView");
        assertThat(speaking).contains(
                "class=\"prd-speaking-root\"",
                "data-result-detail-kind=\"SPEAKING_DETAIL\"",
                "data-speaking-active-question",
                "data-speaking-evidence-mode",
                "data-speaking-recording-state",
                "data-speaking-acoustic-state",
                "resultDetail.payload().scoreCriteria()",
                "resultDetail.payload().diagnosticGroups()",
                "resultDetail.payload().transcriptSegments()",
                "prd-speaking-inline-annotation",
                "th:if=\"${segment.annotated()}\"",
                "th:text=\"${segment.text()}\"",
                "segment.explanationVi()",
                "segment.correctionKo()",
                "data-speaking-features=${segment.featureIds()}",
                "data-speaking-finding-ids=${segment.findingIds()}",
                "data-speaking-number-feature=${membership.descriptorId()}",
                "data-speaking-descriptor",
                "data-speaking-subcriterion=${segment.featureId()}",
                "data-speaking-kind",
                "aria-describedby",
                "role=\"tooltip\"",
                "Câu trả lời văn bản cũ — không phải bản chép lời có thẩm quyền",
                "!resultDetail.payload().evidence().transcriptAvailable()",
                "resultDetail.payload().upgrade().learnerDerivedUpgrade()",
                "prd-speaking-topbar",
                "data-speaking-splitter",
                "role=\"separator\"",
                "aria-valuemin=\"35\"",
                "aria-valuemax=\"65\"",
                "data-speaking-diagnostic-filter",
                "data-speaking-filter-status",
                "data-speaking-occurrence=${finding.findingId()}",
                "data-speaking-occurrence-trigger",
                "prd-occurrence-detail",
                "data-speaking-zero-chip-empty",
                "data-speaking-audio-alignment",
                "audioStartMs()",
                "audioEndMs()",
                "practice/fragments/icons :: overview",
                "practice/fragments/icons :: strength",
                "practice/fragments/icons :: improvement",
                "practice/fragments/icons :: upgrade",
                "practice/fragments/icons :: sample",
                "Chọn một chip để mở phản hồi và đánh dấu đúng đoạn trong bản chép lời.",
                "prd-speaking-upgrade-criteria",
                "data-speaking-upgrade-criterion",
                "data-speaking-upgrade-filter",
                "Tiêu chí được xử lý trong bài nâng cấp",
                "prd-header-actions",
                "prd-speaking-result-info",
                "prd-speaking-result-info-panel",
                "Thông tin kết quả",
                "Phạm vi và nguồn bằng chứng",
                "Thông tin nguồn kỹ thuật",
                "Tổng quan",
                "Điểm mạnh",
                "Cần cải thiện",
                "Bài nâng cấp",
                "<span>Mẫu</span>",
                "resultDetail.payload().teacherSample().available()",
                "Câu trả lời mẫu do giáo viên soạn",
                "data-speaking-teacher-sample",
                "không chứng minh bộ đánh giá đã nghe âm thanh")
                .doesNotContain(
                        "questionsJson", "JSON.parse", "Content", "Coherence",
                        "<span lang=\"ko\">개요</span>",
                        "<span lang=\"ko\">강점</span>",
                        "<span lang=\"ko\">개선 필요</span>",
                        "task.evaluationStateLabelKo()",
                        "criterion.labelKo()",
                        "group.labelKo()",
                        "chip.labelKo()",
                        "prd-speaking-source-technical",
                        "prd-speaking-trust-details",
                        "holistic", "subtotal", "AUDIO_SUBMITTED",
                        "S_FLUENCY_", "S_PRONUNCIATION_", "th:utext",
                        "resultDetail.payload().evidence().transcriptText()",
                        "data-speaking-feature=${segment.featureId()}");
        assertThat(speaking.split("role=\"tab\"", -1)).hasSize(6);
        assertThat(speaking.split("data-speaking-filter-status", -1)).hasSize(3);
        assertThat(detailCss).contains(
                "html.prd-speaking-root",
                ".prd-speaking-tabs",
                ".prd-speaking-panel[hidden]",
                ".prd-speaking-chip[aria-pressed=\"true\"]",
                ".prd-speaking-recording audio",
                ".prd-speaking-result-info-panel",
                ".prd-speaking-upgrade-criterion-grid",
                ".prd-speaking-splitter",
                ".prd-speaking-inline-annotation.is-selected.is-strength",
                ".prd-speaking-inline-annotation.is-selected.is-need",
                ".prd-speaking-inline-annotation.is-selected.is-upgrade",
                ".prd-header-actions",
                "@media (max-width: 980px)");
        assertThat(tabsJs).contains(
                "[data-speaking-upgrade-filter]",
                "[data-speaking-splitter]",
                "--speaking-split",
                "setPointerCapture",
                "ArrowLeft",
                "ArrowRight",
                "is-upgrade",
                "[data-speaking-occurrence-trigger]",
                "data-speaking-finding-ids",
                "data-speaking-number-feature",
                "data-speaking-zero-chip-empty",
                "activateOccurrence",
                "prd-speaking-inline-annotation')",
                "!annotation.classList.contains('is-selected')");
    }

    @Test
    void dedicatedExamPlayersShareNavigationSafetyAndAdaptiveReadingContracts() throws IOException {
        String player = Files.readString(PRACTICE_TEMPLATES.resolve("player.html"));
        String testDetail = Files.readString(PRACTICE_TEMPLATES.resolve("test-detail.html"));
        String writingPlayer = Files.readString(PRACTICE_TEMPLATES.resolve("player-writing.html"));
        String js = Files.readString(EXAM_PLAYER_JS);

        assertThat(player).contains(
                "data-question-stage",
                "data-has-source",
                "data-long-source",
                "data-exit-link",
                "data-selection-highlight",
                "data-selection-note",
                ">Đúng</span>",
                ">Sai</span>",
                ">Không có thông tin</span>",
                "'Ô trống ' + blankStat.count");
        assertThat(player).doesNotContain(
                ">True</span>",
                ">False</span>",
                ">Not Given</span>");
        assertThat(testDetail).contains(
                "selectedTest.displayOrder() + 1");
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
                "workspace.classList.add(hasSource ? 'layout-split' : 'layout-focus');",
                "source.matchAll(/_{2,}/g)",
                "player.dataset.deadlineEpochMs",
                "player.dataset.serverNowEpochMs",
                "deadlineSubmission = true",
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
    void progressSerializationFailureKeepsTypedUnavailableState() throws IOException {
        String controller = Files.readString(PRACTICE_CONTROLLER);
        String progress = Files.readString(PRACTICE_TEMPLATES.resolve("progress.html"));
        String facts = Files.readString(
                PRACTICE_TEMPLATES.resolve("fragments/progress-facts.html"));
        String progressJs = Files.readString(PRACTICE_PROGRESS_JS);

        assertThat(controller).contains(
                "PracticeProgressService progressService",
                "ProgressExclusionReason",
                ".SERIALIZATION_UNAVAILABLE",
                "SAFE_PROGRESS_JSON_MAPPER",
                "serializeProgressFallback(unavailable.overview())",
                "serializeProgressFallback(unavailable.analytics())",
                "PracticeModelAttributes.PROGRESS_STATE");
        assertThat(controller).doesNotContain(
                "SAFE_PROGRESS_OVERVIEW_JSON",
                "SAFE_PROGRESS_ANALYTICS_JSON",
                "PracticeModelAttributes.OVERVIEW_JSON, \"{}\"",
                "PracticeModelAttributes.ANALYTICS_JSON, \"{}\"");
        assertThat(progress).contains(
                "id=\"progress-unavailable-state\"",
                "progressState.availability().name() == 'UNAVAILABLE'",
                "progressState.reason().name() == 'PAGE_DATA_UNAVAILABLE'",
                "progressState.reason().name() == 'SERIALIZATION_UNAVAILABLE'",
                "progressState.retryHint() == 'RELOAD'",
                "Chưa thể hiển thị dữ liệu tiến độ",
                "Không có biểu đồ cũ nào được giữ lại.",
                "Tải lại với bộ lọc hiện tại",
                "overview.totalPracticeMinutes() == null",
                "'Chưa khả dụng'",
                "FILTER_NO_DATA",
                "NO_ACTIVITY",
                "CHART_ENHANCEMENT_UNAVAILABLE",
                "hidden data-chart-visual",
                "Bộ lọc tác vụ/hồ sơ Viết chỉ áp dụng",
                "không có điểm Nói tổng hợp",
                "row.scoreFact().renderableValue()",
                "row.score() != null",
                "row.totalPoints() != null",
                "row.score() + ' / ' + row.totalPoints()",
                "Điểm chưa khả dụng",
                "Điểm Viết theo tác vụ và nhóm có thể so sánh",
                "Q51-Q54, hồ sơ/gói chính sách và mức tối đa khác nhau luôn ở các nhóm riêng",
                "/practice(skill='WRITING',writingTask=${task.taskType()})");
        assertThat(facts).contains(
                "Quy mô mẫu:",
                "dữ kiện đủ điều kiện / ",
                "Độ phủ nguồn:",
                "Khoảng quan sát:",
                "Chưa có mốc bắt đầu",
                "Chưa có mốc kết thúc",
                "Chốt dữ liệu:",
                "Nguồn giới hạn:",
                "trả về ",
                "đã cắt bớt nguồn",
                "Độ tin cậy nguồn:",
                "data-chart-failure-message",
                "Thử tải biểu đồ lại",
                "profile=${filter.profileId()}",
                "chỉ tóm tắt quy mô mẫu, độ mới và độ phủ",
                "Dữ liệu cũ chưa xác minh",
                "Thiếu khóa phiên bản bất biến",
                "Nói chỉ có hoạt động/hồ sơ/độ phủ; không có điểm tổng hợp")
                .doesNotContainPattern("[가-힣]");
        assertThat(progress).doesNotContain(
                "currentLevel() == 'Chưa có dữ liệu điểm'",
                "Lượt làm bài đã nộp",
                "bài tuần này",
                "Xét 20 bài gần nhất",
                "Lịch sử luyện tập đầy đủ",
                "tối đa 30 lượt làm bài gần nhất",
                "Lịch sử hoạt động luyện tập trong 12 tuần gần đây",
                ": row.status())",
                "Phản hồi thông minh",
                "Phân tích bứt phá",
                "Điểm trung bình kỹ năng Viết");
        assertThat(progressJs).contains(
                "cell.totalMinutes === null",
                "thời lượng chưa khả dụng",
                "canvas.hidden = true",
                "Bảng chuẩn vẫn dùng được",
                "function enhanceHeatmap()",
                "status.querySelector('[data-chart-failure-message]')",
                "metric.skill === 'READING' || metric.skill === 'LISTENING'",
                "metric.scoreFact.value");
        assertThat(progressJs).doesNotContain(
                "Math.round((value / totalCount) * 100)",
                "m.skill !== 'SPEAKING'");
    }

    @Test
    void progressRendersPartialObjectiveFactsWithEvidenceInFallbackAndCharts()
            throws IOException {
        String dto = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/dto/PracticeDtos.java"));
        String progress = Files.readString(PRACTICE_TEMPLATES.resolve("progress.html"));
        String facts = Files.readString(
                PRACTICE_TEMPLATES.resolve("fragments/progress-facts.html"));
        String progressJs = Files.readString(PRACTICE_PROGRESS_JS);

        assertThat(dto).contains(
                "public boolean renderableValue()",
                "value != null",
                "availability == ProgressAvailability.AVAILABLE",
                "availability == ProgressAvailability.PARTIAL",
                "public boolean partialCoverage()");
        assertThat(progress).contains(
                "overview.recentScoreFact().renderableValue()",
                "metric.scoreFact().renderableValue()",
                "metric.scoreFact().partialCoverage()",
                "point.scoreFact().renderableValue()",
                "point.scoreFact().partialCoverage()",
                "metric.scoreFact().numerator()",
                "metric.scoreFact().denominator()",
                "sourceFact(${metric.scoreFact()})",
                "sourceFact(${point.scoreFact()})",
                "· độ phủ một phần");
        assertThat(facts).contains(
                "fact.partialCoverage()",
                "Độ phủ một phần:",
                "hoạt động bị loại không được đổi thành 0",
                "Điểm đạt / điểm có thể đạt:",
                "fact.numerator() != null && fact.denominator() != null",
                "fact.numerator() + ' / ' + fact.denominator()",
                "Độ phủ nguồn:",
                "Khoảng quan sát:")
                .doesNotContainPattern("[가-힣]");
        assertThat(progressJs).contains(
                "function renderableNumericFact(fact)",
                "fact.availability === 'AVAILABLE' || fact.availability === 'PARTIAL'",
                "fact.value !== null",
                "renderableNumericFact(metric.scoreFact)",
                "renderableNumericFact(point.scoreFact)");
        assertThat(progressJs).doesNotContain(
                "function availableNumericFact(fact)",
                "Number(fact.value || 0)");
    }

    @Test
    void scoreTrendSourceFactResolvesPointBeforeFragmentReplacement() throws IOException {
        String progress = Files.readString(PRACTICE_TEMPLATES.resolve("progress.html"));

        var pointScopes = Jsoup.parse(progress)
                .getElementsByTag("th:block")
                .stream()
                .filter(element -> "point : ${analytics.scoreTrend()}"
                        .equals(element.attr("th:each")))
                .toList();

        assertThat(pointScopes).hasSize(1);
        Element pointScope = pointScopes.get(0);
        assertThat(pointScope.hasAttr("th:replace")).isFalse();
        assertThat(pointScope.children()).hasSize(1);
        Element sourceFactReplacement = pointScope.child(0);
        assertThat(sourceFactReplacement.tagName()).isEqualTo("th:block");
        assertThat(sourceFactReplacement.attr("th:replace"))
                .contains("sourceFact(${point.scoreFact()})");
    }

    @Test
    void scoreTrendKeepsRepeatedSameSkillTimestampAsDistinctEventSlots() throws IOException {
        String progressJs = Files.readString(PRACTICE_PROGRESS_JS);

        assertThat(progressJs).contains(
                "function buildScoreTrendEventSlots(trend)",
                "const occurrenceKey = `${point.date}::${point.skill}`;",
                "const occurrence = occurrenceByDateAndSkill.get(occurrenceKey) || 0;",
                "occurrenceByDateAndSkill.set(occurrenceKey, occurrence + 1);",
                "const eventKey = `${point.date}::${occurrence}`;",
                "eventSlots.push(slot);",
                "const slots = buildScoreTrendEventSlots(trend);",
                "slot.pointsBySkill[skill]?.score ?? null",
                "slot.pointsBySkill[context.dataset.skill]");
        assertThat(progressJs).doesNotContain(
                "const uniqueDates = [];",
                "pointsMap[pt.date][pt.skill] =");
    }

    @Test
    void phase13gStaticAccessibilityResponsiveAndMotionContractsArePresent()
            throws IOException {
        String player = Files.readString(
                PRACTICE_TEMPLATES.resolve("player.html"));
        String playerWriting = Files.readString(
                PRACTICE_TEMPLATES.resolve("player-writing.html"));
        String playerJs = Files.readString(EXAM_PLAYER_JS);
        String playerCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice/player.css"));
        String editor = Files.readString(
                PRACTICE_TEMPLATES.resolve("manage/editor.html"));
        String editorCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice/manage-editor.css"));
        String importWorkspace = Files.readString(
                PRACTICE_TEMPLATES.resolve("manage/import-workspace.html"));
        String importWizard = Files.readString(
                PRACTICE_TEMPLATES.resolve("manage/import-wizard.html"));
        String excel = Files.readString(
                PRACTICE_TEMPLATES.resolve("manage/excel-import.html"));
        String detailObjective = Files.readString(
                PRACTICE_TEMPLATES.resolve("result-detail-objective.html"));
        String detailCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice-result-detail.css"));
        String resultJs = Files.readString(PRACTICE_RESULT_JS);

        assertThat(player).contains(
                "data-timer-announcement",
                "aria-live=\"off\"",
                "aria-controls=\"exam-notes-drawer\"",
                "aria-expanded=\"false\"",
                "aria-hidden=\"true\" inert");
        assertThat(playerWriting).contains(
                "th:for=\"'writing-answer-'",
                "th:id=\"'writing-count-'",
                "aria-describedby");
        assertThat(playerJs).contains(
                "notesDrawer.removeAttribute('inert')",
                "notesDrawer.setAttribute('inert', '')",
                "event.key === 'Escape'");
        assertThat(playerCss).contains(
                "@media (prefers-reduced-motion: reduce)");
        assertThat(editor).contains(
                "activateUploadZone(event",
                "role=\"dialog\"",
                "aria-modal=\"true\"",
                "trapEditorSurfaceFocus",
                "aria-pressed=\"true\"",
                "role=\"status\" aria-live=\"polite\"");
        assertThat(editorCss).contains(
                ".audio-upload-box:focus-visible",
                "@media (prefers-reduced-motion: reduce)")
                .doesNotContain("color: transparent !important");
        assertThat(importWorkspace).contains(
                "createKeyboardRegion()",
                "openFocusOverlay(",
                "closeFocusOverlay(",
                "aria-modal=\"true\"",
                "role=\"tabpanel\"",
                "id=\"ai-status-update\" role=\"status\"",
                "aria-live=\"polite\" aria-atomic=\"true\"",
                "function announceAiStatusUpdate(message)",
                "id=\"ai-status-announcement\" role=\"alert\"",
                "function announceAiStatus(message)",
                "announceAiStatusUpdate(\n"
                        + "      'Phân tích AI đã bắt đầu.",
                "announceAiStatusUpdate(\n"
                        + "        'Phân tích AI đã hoàn tất.",
                "announceAiStatus(`Phân tích AI thất bại.",
                "openAiStatusPopover({ focus: true })",
                "const workspaceReducedMotion = window.matchMedia(",
                "'(prefers-reduced-motion: reduce)'",
                "behavior: workspaceReducedMotion.matches ? 'auto' : 'smooth'")
                .doesNotContain(
                        "box.scrollIntoView({ behavior: 'smooth'");
        assertThat(importWizard).contains(
                "background: #3B57D4; color: #fff;",
                "font-weight:700; color:#3B57D4; text-decoration:none;",
                "background:rgba(79,110,247,0.1); color:#3B57D4;",
                "background:rgba(217,144,0,0.1); color:#8A5700;",
                "background:rgba(34,160,107,0.1); color:#087A4F;")
                .doesNotContain(
                        "background: #4F6EF7; color: #fff;",
                        "font-weight:700; color:#4F6EF7; text-decoration:none;",
                        "background:rgba(79,110,247,0.1); color:#4F6EF7;",
                        "background:rgba(217,144,0,0.1); color:#D99000;",
                        "background:rgba(34,160,107,0.1); color:#22A06B;");
        assertThat(excel).contains(
                "aria-live=\"assertive\" tabindex=\"-1\"",
                "scope=\"col\"",
                "ArrowLeft",
                "aria-pressed",
                "prefers-reduced-motion:reduce");
        assertThat(detailObjective).contains(
                "data-option-state",
                "aria-label=${'Phương án '",
                "data-label=\"Bài làm\"",
                "data-label=\"Kết quả\"");
        assertThat(resultJs)
                .contains(
                        "panel.hidden = panel.dataset.objectiveGroupKey !== groupKey;",
                        ".replace(/-explanation$/, '');")
                .doesNotContain(
                        "document.querySelectorAll('.prd-objective-explanation-link')");
        assertThat(detailCss).contains(
                "overflow-wrap: anywhere",
                "@media (prefers-reduced-motion: reduce)");
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

    @Test
    void post13hPlayerAndAuthoringIntegrityBoundariesAreExplicit()
            throws IOException {
        String player = Files.readString(
                PRACTICE_TEMPLATES.resolve("player.html"));
        String writing = Files.readString(
                PRACTICE_TEMPLATES.resolve(
                        "player-writing.html"));
        String playerJs = Files.readString(EXAM_PLAYER_JS);
        String editor = Files.readString(
                PRACTICE_TEMPLATES.resolve(
                        "manage/editor.html"));
        String importWorkspace = Files.readString(
                PRACTICE_TEMPLATES.resolve(
                        "manage/import-workspace.html"));

        assertThat(player).contains(
                "data-deadline-epoch-ms",
                "data-server-now-epoch-ms",
                "data-room-timer",
                "data-attempt-lock-version",
                "savedAnswers.get");
        assertThat(writing).contains(
                "data-deadline-epoch-ms",
                "data-room-timer",
                "data-attempt-lock-version",
                "savedAnswers.get");
        assertThat(playerJs).contains(
                "method: 'PUT'",
                "/answers",
                "expectedLockVersion",
                "response.status === 409",
                "response.status === 410",
                "autosaveGeneration",
                "autosavePersistedGeneration",
                "maxAutosaveRetries",
                "autosaveRetryExhausted",
                "autosaveSubmitDrain",
                "drainLatestAnswers",
                "submissionPending",
                "exitPending",
                "flushLatestAnswers",
                "window.location.assign(link.href)",
                "player.dataset.deadlineEpochMs")
                .doesNotContain("ksh-exam-timer");
        assertThat(editor).contains(
                "content.textContent = String(msg.content || '')")
                .doesNotContain("<div>${msg.content}</div>");
        assertThat(importWorkspace).contains(
                "title.textContent = String(a.title",
                "title.title = String(a.title",
                "attach.addEventListener",
                "remove.addEventListener")
                .doesNotContain(
                        "title=\"${a.title}\">${a.title}</div>",
                        "onclick=\"associateAssetToSelectedRegion(${a.id})\"");
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
