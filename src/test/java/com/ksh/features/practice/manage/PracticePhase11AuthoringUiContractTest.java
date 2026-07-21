package com.ksh.features.practice.manage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticePhase11AuthoringUiContractTest {

    @Test
    void manualEditorLoadsCanonicalModulesAndCoversEveryMvpQuestionType() throws Exception {
        String editor = read("src/main/resources/templates/practice/manage/editor.html");
        String contract = read("src/main/resources/static/js/practice/manage-authoring-contract.js");
        String preview = read("src/main/resources/static/js/practice/manage-draft-preview.js");

        assertTrue(editor.contains("/js/practice/manage-authoring-contract.js"));
        assertTrue(editor.contains("/js/practice/manage-draft-preview.js"));
        assertFalse(editor.contains("/js/practice/manage-editor.js"));
        for (String type : List.of(
                "SINGLE_CHOICE", "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "ESSAY", "SPEAKING")) {
            assertTrue(editor.contains(type) || contract.contains(type), "Missing editor type " + type);
        }
        assertFalse(editor.contains("MULTIPLE_CHOICE"));
        assertFalse(editor.contains("MATCHING"));
        assertTrue(contract.contains("question-content-v1"));
        assertTrue(contract.contains("answer-spec-v1"));
        assertTrue(contract.contains("correctOptionIds"));
        assertFalse(contract.contains("matchingPairs"));
        assertTrue(contract.contains("acceptedValues"));
        assertFalse(editor.contains("id=\"q-scoring-policy\""));
        assertFalse(editor.contains("id=\"q-prompt-profile\""));
        assertFalse(editor.contains("id=\"q-rubric-profile\""));
        assertTrue(editor.contains("/preview`"));
        assertTrue(editor.contains("id=\"editor-test-card\""));
        assertTrue(editor.contains("lessonCodeForSkill"));
        assertTrue(editor.contains("groupCode"));
        assertTrue(editor.contains("reNumberSectionQuestions(sIdx)"));
        assertTrue(editor.contains("id=\"excel-import-action\""));
        assertTrue(editor.contains("id=\"pdf-import-action\""));
        assertTrue(editor.contains("`/practice/manage/import?draftId=${encodeURIComponent(DRAFT_ID)}`"));
        assertTrue(editor.contains("id=\"q-source-trace\""));
        assertTrue(editor.contains("get('preview') === '1'"));
        assertTrue(editor.contains("onclick=\"handleEditorToolNavigation(event)\""));
        assertTrue(editor.contains("async function flushDraftBeforeNavigation()"));
        assertTrue(editor.contains("onsubmit=\"return handlePublishSubmit(event);\""));
        assertTrue(editor.contains("async function handlePublishSubmit(event)"));
        assertTrue(editor.contains("function syncDraftDocumentTitle()"));
        assertTrue(editor.contains("DRAFT_DATA.document.title = title"));
        assertTrue(editor.contains("if (syncDraftDocumentTitle()) triggerAutosave();"));
        assertTrue(editor.contains("Không thể xem trước vì bản nháp chưa lưu thành công."));
        assertTrue(editor.contains("function toggleDotsDropdown(event)"));
        assertTrue(editor.contains("function focusDraftTitle(event)"));
        assertTrue(editor.contains("function confirmDeleteDraft(event)"));
        assertFalse(editor.contains("id=\"practice-editor-data\""));
        assertFalse(editor.contains("th:utext=\"${draftJson}\""));
        assertFalse(editor.contains("[PracticeEditor] state before"));
        assertFalse(editor.contains("[PracticeEditor] state after"));
        assertTrue(contract.contains("question-content-v1"));
        assertFalse(editor.contains("['READING', 'LISTENING'].includes(section.skill)"));
        assertTrue(editor.contains("id=\"speaking-prompt-audio-dropzone\""));
        assertTrue(editor.contains("id=\"q-speak-play-limit\""));
        assertTrue(editor.contains("function uploadSpeakingPromptAudio(file)"));
        assertTrue(editor.contains("function updateSpeakingPromptAudioPreview(url)"));
        assertTrue(editor.contains("question.speakingPromptAudioUrl = data.url"));
        assertTrue(editor.contains("Player Speaking sẽ tự phát audio này"));
        assertTrue(contract.contains("content.speakingDelivery"));
        assertTrue(preview.contains("speakingDelivery.promptAudioReference"));
        assertTrue(preview.contains("speakingDelivery.preparationSeconds"));
        assertTrue(preview.contains("speakingDelivery.responseSeconds"));
        assertTrue(preview.contains("section.listeningCheckAudioReference"));
        assertTrue(editor.contains("function isCurrentEditorTarget(type, target)"));
        assertTrue(editor.contains("const target = type === 'group'"));
        assertTrue(editor.contains("group && group.questions[currentNode.qIdx]"));
        assertTrue(editor.contains("const optionId = question && question.options[idx] && question.options[idx].id"));
        assertTrue(editor.contains("isCurrentTarget = () => true"));
        assertTrue(editor.contains("if (isCurrentEditorTarget('question', question)) renderOptionRows(question)"));
    }

    @Test
    void fillBlankEditorAndWritingPreviewMirrorDedicatedLearnerPlayers() throws Exception {
        String editor = read("src/main/resources/templates/practice/manage/editor.html");
        String player = read("src/main/resources/templates/practice/player.html");
        String playerJs = read("src/main/resources/static/js/practice/player-exam.js");
        String playerCss = read("src/main/resources/static/css/practice/player.css");
        String editorCss = read("src/main/resources/static/css/practice/manage-editor.css");
        String authoringContract = read("src/main/resources/static/js/practice/manage-authoring-contract.js");

        String typeChange = editor.substring(editor.indexOf("function handleQuestionTypeChange()"),
                editor.indexOf("function getCircledNumber"));
        assertTrue(typeChange.indexOf("normalizeQuestion(q, makeClientId)")
                < typeChange.indexOf("renderFillBlanks(q)"));
        assertTrue(typeChange.contains("if (previousType !== type)"));
        assertTrue(typeChange.contains("if (type === 'FILL_BLANK') q.fillBlanks = []"));
        assertTrue(editor.contains("id=\"fill-prompt-composer\""));
        assertTrue(editor.contains("contenteditable=\"true\""));
        assertTrue(editor.contains("function renderFillPromptComposer(q)"));
        assertTrue(editor.contains("function serializeFillPromptComposer()"));
        assertTrue(editor.contains("function placeFillBlank(index)"));
        assertTrue(editor.contains("const unplacedIndex = insertInPrompt"));
        assertTrue(editor.contains("invalid.textContent = 'Ô trống không hợp lệ'"));
        assertTrue(editor.contains("split(token).join('')"));
        assertFalse(editor.contains("class=\"fill-token-button\""));
        assertFalse(editor.contains("title=\"Chèn token"));
        assertFalse(editor.contains("${escapeHtml(token)}"));

        assertTrue(player.contains("data-blank-number=${blankStat.count}"));
        assertTrue(playerJs.contains("exam-inline-blank-number"));
        assertTrue(playerCss.contains(".exam-inline-blank-number"));
        assertTrue(editor.contains("id=\"preview-writing-prompts\""));
        assertTrue(editor.contains("preview-writing-answer-card"));
        assertTrue(editor.contains("preview-speaking-state"));
        assertTrue(editor.contains("preview-speaking-panel"));
        assertTrue(editor.contains("preview-fill-slot"));
        assertTrue(authoringContract.contains("const canonicalBlanks = Array.isArray(canonicalContent.blanks)"));
        assertTrue(authoringContract.contains("candidate.blankId === blank.id"));
        assertTrue(authoringContract.contains("Array.from(answer.acceptedValues)"));
        assertTrue(authoringContract.contains("Array.isArray(canonicalContent.options) ? canonicalContent.options : []"));
        assertTrue(authoringContract.contains("Array.isArray(previousSpec.correctOptionIds)"));
        assertTrue(authoringContract.contains("previousSpec.correctValue || ''"));
        assertTrue(authoringContract.contains("q.answer = { type: 'SINGLE', value: legacyValue }"));
        assertTrue(authoringContract.contains("q.answer = { type: 'TFNG', value: answer.correctValue || '' }"));
        assertTrue(editor.contains("q.questionContent && Array.isArray(q.questionContent.options)"));
        assertTrue(editorCss.contains(".preview-writing-answer-card"));
        assertTrue(editorCss.contains(".preview-speaking-state"));
        assertTrue(editorCss.contains(".preview-fill-slot > span"));
        assertTrue(editorCss.contains(".fill-prompt-composer"));
        assertTrue(editorCss.contains(".fill-composer-slot-line"));
    }

    @Test
    void pdfWorkspaceDefaultsToGuidedModeAndProtectsRawDebugTabs() throws Exception {
        String workspace = read("src/main/resources/templates/practice/manage/import-workspace.html");
        String wizard = read("src/main/resources/templates/practice/manage/import-wizard.html");

        assertTrue(workspace.contains("id=\"mode-guided\""));
        assertTrue(workspace.contains("id=\"mode-advanced\""));
        assertTrue(workspace.contains("FULL_SELECTED_PAGES"));
        assertFalse(workspace.contains("hasAnyRole('HEAD','ADMIN')"));
        assertFalse(workspace.contains("data.privilegedDetails"));
        assertFalse(workspace.contains("JSON kỹ thuật"));
        assertFalse(workspace.contains("Request JSON"));
        assertTrue(workspace.contains("renderRegionSpecificFields(type, ann || {}, true)"));
        assertTrue(workspace.contains("id=\"region-destination-summary\""));
        assertTrue(workspace.contains("Cách AI đọc tài liệu"));
        assertTrue(workspace.contains("function openLearnerPreview()"));
        assertTrue(workspace.contains("id=\"tool-draw\""));
        assertTrue(workspace.contains("title=\"Khoanh vùng để crop ảnh (D)\""));
        assertFalse(workspace.contains("advanced-only\" id=\"tool-draw\""));
        assertTrue(workspace.contains("{ s: 'select', d: 'draw', h: 'pan' }"));
        assertTrue(workspace.contains("input, textarea, select, [contenteditable=\"true\"]"));
        assertTrue(workspace.contains("function findCurrentRegionCropAsset(assets, annotation)"));
        assertTrue(workspace.contains("sameCoordinate(asset.cropX, annotation.xRatio)"));
        assertFalse(workspace.contains("assets.find(a => a.sourceRegionId ==="));
        assertFalse(workspace.contains("Hybrid - Khuyên dùng"));
        assertFalse(workspace.contains("📁"));
        assertFalse(workspace.contains("📂"));
        assertFalse(workspace.contains("🎯"));
        assertFalse(wizard.contains("authoringCatalog.templates"));
        assertFalse(wizard.contains("name=\"examTemplateCode\""));
        assertTrue(wizard.contains("@{/practice/manage/create}"));
        assertFalse(wizard.contains("@{/practice/manage/manual}"));
        assertTrue(wizard.contains("id=\"target-section\"")
                || wizard.contains("id=\"target-skill\""));
        assertFalse(wizard.contains("value=\"EXTENDED_PRACTICE\""));
        assertFalse(wizard.contains("value=\"GENERAL_KOREAN\""));
    }

    @Test
    void excelImportSurfaceIncludesRowPreviewAndAutomaticValidRowImport() throws Exception {
        String excel = read("src/main/resources/templates/practice/manage/excel-import.html");
        String dashboard = read("src/main/resources/templates/practice/manage/dashboard.html");

        assertTrue(excel.contains("/practice/manage/excel/template"));
        assertTrue(excel.contains("/practice/manage/excel/${action}"));
        assertTrue(excel.contains("id=\"excel-preview-modal\""));
        assertTrue(excel.contains("id=\"preview-rows\""));
        assertTrue(excel.contains("id=\"excel-compact-preview\""));
        assertTrue(excel.contains("data-view=\"DETAIL\""));
        assertTrue(excel.contains("function renderIssuePanel(result)"));
        assertTrue(excel.contains("sẽ tự động bị bỏ khi xác nhận"));
        assertTrue(excel.contains("data-filter=\"ERROR\""));
        assertTrue(excel.contains("importableQuestionCount"));
        assertTrue(excel.contains("result.canImport"));
        assertTrue(excel.contains("câu hợp lệ"));
        assertFalse(excel.contains("templateCode"));
        assertTrue(excel.contains("SELECTED_TEST_NO"));
        assertTrue(excel.contains("SELECTED_LESSON_CODE"));
        assertTrue(excel.contains("row.detail?.groupImageReference"));
        assertTrue(excel.contains("row.correctAnswer"));
        assertTrue(excel.contains("row.importedQuestionNo"));
        assertTrue(excel.contains("position:fixed;inset:0;margin:auto"));
        assertTrue(excel.contains("Nhóm / Bài đọc"));
        assertTrue(excel.contains("Nhóm / Transcript"));
        assertTrue(excel.contains("Phương án H"));
        assertFalse(excel.contains("matchingPairs"));
        assertTrue(excel.contains("id=\"excel-media-files\""));
        assertTrue(excel.contains("mediaOverrides"));
        assertTrue(excel.contains("URL.createObjectURL"));
        assertTrue(excel.contains("uploadPendingMedia"));
        assertFalse(dashboard.contains("@{/practice/manage/excel}"));
        assertTrue(dashboard.contains("Excel import is available inside each skill section."));
    }

    @Test
    void sharedPracticeShellLoadsIdempotentDropdownBehavior() throws Exception {
        String head = read("src/main/resources/templates/fragments/head.html");
        String app = read("src/main/resources/static/js/app.js");

        assertTrue(head.contains("defer th:src=\"@{/js/app.js}\""));
        assertTrue(app.contains("__KSH_SHARED_APP_INITIALIZED__"));
    }

    @Test
    void singleScopeUiRemovesAssessmentGovernanceAndKeepsImmutableHistoryFlows()
            throws Exception {
        String dashboard = read("src/main/resources/templates/practice/manage/dashboard.html");
        String revisions = read("src/main/resources/templates/practice/manage/revisions.html");
        String controller = read("src/main/java/com/ksh/features/practice/manage/controller/PracticeManageController.java");

        assertTrue(revisions.contains("Mỗi lần xuất bản là một phiên bản bất biến"));
        assertTrue(revisions.contains("/versions/{versionId}/restore"));
        assertFalse(revisions.contains("assessmentProgramCode"));
        assertFalse(revisions.contains("examTemplateCode"));
        assertTrue(revisions.contains("#lists.size(versions)"));
        assertTrue(revisions.contains("row.version.status"));
        assertTrue(revisions.contains("xuất bản 10 lần tạo v1-v10"));
        assertTrue(revisions.contains("khôi phục v3 sẽ tạo v11"));
        assertTrue(revisions.contains("Autosave bản nháp không tự tạo published revision"));
        assertTrue(dashboard.contains("KSH Practice"));
        assertFalse(dashboard.contains("Program / Kịch bản"));
        assertFalse(dashboard.contains("assessmentProgramCode"));
        assertFalse(dashboard.contains("examTemplateCode"));
        assertTrue(controller.contains("redirect:/practice/manage/revisions?setId="));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/templates/practice/manage/assessment-governance.html")));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/static/js/practice-assessment-governance.js")));
        assertFalse(controller.contains("/revisions/{logId}/restore"));
    }

    @Test
    void practiceAuthoringRoutesAreLecturerOnlyAndExposeNoOverridePath()
            throws Exception {
        List<String> controllers = List.of(
                "PracticeManageController.java",
                "PracticeDraftController.java",
                "PracticeAssessmentExcelController.java",
                "PracticeImportController.java",
                "PracticePdfImportApiController.java",
                "PracticeMaterialLibraryPageController.java");
        for (String filename : controllers) {
            String source = read(
                    "src/main/java/com/ksh/features/practice/manage/controller/" + filename);
            assertTrue(source.contains("@PreAuthorize(Roles.PREAUTH_LECTURER)"),
                    "Missing exact lecturer boundary in " + filename);
            assertFalse(source.contains("PREAUTH_LECTURER_OR_ABOVE"));
            assertFalse(source.contains("overrideReason"));
        }

        String security = read("src/main/java/com/ksh/config/SecurityConfig.java");
        String dashboard = read("src/main/resources/templates/practice/manage/dashboard.html");
        assertTrue(security.contains(
                ".requestMatchers(\"/practice/manage/**\").hasRole(Roles.LECTURER)"));
        assertFalse(dashboard.contains("Can thiệp khẩn cấp"));
        assertFalse(dashboard.contains("name=\"canEdit\""));
        assertFalse(dashboard.contains("name=\"canPublish\""));
        assertFalse(dashboard.contains("name=\"canRestore\""));
        assertFalse(dashboard.contains("name=\"canManageMaterial\""));
        assertTrue(dashboard.contains("Được cộng tác toàn bộ nội dung"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/ksh/features/practice/manage/controller/PracticeCollaborationController.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/ksh/features/practice/manage/service/PracticeOverrideContextService.java")));
    }

    @Test
    void phase12MaterialLibrarySeparatesMineAndSharedWithoutRawStorageLinks()
            throws Exception {
        String page = read("src/main/resources/templates/practice/manage/material-library.html");
        String service = read("src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialLibraryService.java");
        String sidebar = read("src/main/resources/templates/fragments/practice-sidebar.html");

        assertTrue(page.contains("Của tôi"));
        assertTrue(page.contains("Được chia sẻ"));
        assertTrue(page.contains("item.contentUrl"));
        assertTrue(page.contains("item.referenceCount"));
        assertTrue(page.contains("/practice/manage/materials/{id}/delete"));
        assertFalse(page.contains("/uploads/"));
        assertFalse(page.contains("storageKey"));
        assertTrue(service.contains("/practice/materials/"));
        assertFalse(service.contains("getStorageKey"));
        assertTrue(sidebar.contains("/practice/manage/materials"));
    }

    @Test
    void singleScopeLearnerUiDoesNotInventCertificateLevelsAndManageLinksMatchSecurity() throws Exception {
        String index = read("src/main/resources/templates/practice/index.html");
        String progress = read("src/main/resources/templates/practice/progress.html");
        String sidebar = read("src/main/resources/templates/fragments/practice-sidebar.html");
        String header = read("src/main/resources/templates/fragments/app-header.html");
        String security = read("src/main/java/com/ksh/config/SecurityConfig.java");
        String controller = read("src/main/java/com/ksh/features/practice/controller/PracticeController.java");
        String roles = read("src/main/java/com/ksh/security/Roles.java");
        String dashboard = read("src/main/resources/templates/practice/manage/dashboard.html");

        assertTrue(index.contains("Kho luyện tập | KSH"));
        assertTrue(index.contains("Bạch Hổ KSH"));
        assertFalse(index.contains("Kho luyện tập TOPIK"));
        assertFalse(progress.contains("TOPIK II Cấp"));
        assertFalse(progress.contains("TOPIK I Cấp"));
        assertTrue(index.contains("sec:authorize=\"hasRole('LECTURER')\""));
        assertFalse(index.contains("hasAnyRole('LECTURER','HEAD','ADMIN')"));
        assertFalse(sidebar.contains("hasAnyRole('LECTURER','HEAD','ADMIN')"));
        assertTrue(sidebar.contains("sec:authorize=\"hasRole('STUDENT')\""));
        assertTrue(sidebar.contains("<span class=\"pi-nav-text\">Luyện tập</span>"));
        assertTrue(sidebar.contains("<span class=\"pi-nav-text\">Manage Test Sets</span>"));
        assertFalse(sidebar.contains("<span class=\"pi-nav-text\">Kho đề</span>"));

        assertTrue(header.contains("class=\"dropdown nav-practice-dropdown\""));
        assertTrue(header.contains("class=\"nav-practice-link\" th:href=\"@{/practice}\">Luyện tập</a>"));
        assertTrue(header.contains("sec:authorize=\"hasRole('STUDENT')\" th:href=\"@{/practice/progress}\""));
        assertTrue(header.contains("sec:authorize=\"hasRole('LECTURER')\" th:href=\"@{/practice/manage}\""));

        assertTrue(roles.contains("PREAUTH_STUDENT = \"hasRole('STUDENT')\""));
        assertTrue(security.contains(".requestMatchers(\"/practice/progress\", \"/practice/profile\").hasRole(Roles.STUDENT)"));
        assertTrue(controller.contains("@GetMapping(PracticeRoutes.PROGRESS)\n    @PreAuthorize(Roles.PREAUTH_STUDENT)"));
        assertTrue(index.contains("class=\"pc-side-section\" sec:authorize=\"hasRole('STUDENT')\""));

        assertTrue(dashboard.contains("Manage Test Sets"));
        assertTrue(dashboard.contains("My Test Sets"));
        assertTrue(dashboard.contains("Other Lecturers' Test Sets"));
        assertFalse(dashboard.contains("Học liệu của tôi"));
    }

    @Test
    void renderedResourcesRemainUtf8AndAvoidEmojiStyleProductIcons() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/resources/templates"),
                Path.of("src/main/resources/static/js"),
                Path.of("src/main/resources/static/css"),
                Path.of("src/main/resources/db/migration"));
        List<String> mojibakeMarkers = List.of("Cáº", "Ä", "Pháº", "Viáº", "â€", "ðŸ");

        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String content = Files.readString(path);
                    for (String marker : mojibakeMarkers) {
                        assertFalse(content.contains(marker), "Mojibake marker " + marker + " in " + path);
                    }
                    assertFalse(containsEmojiStyleIcon(content), "Emoji-style product icon in " + path);
                }
            }
        }
    }

    @Test
    void privatePracticeUploadsStayBehindAuthorizedMaterialControllers() throws Exception {
        String security = read("src/main/java/com/ksh/config/SecurityConfig.java");
        String draftController = read(
                "src/main/java/com/ksh/features/practice/manage/controller/PracticeDraftController.java");
        String materialController = read(
                "src/main/java/com/ksh/features/practice/controller/PracticeMaterialController.java");

        int privateRule = security.indexOf("/uploads/practice-audio/**");
        int publicRule = security.indexOf(".requestMatchers(\"/uploads/**\").permitAll()");
        assertTrue(privateRule >= 0 && privateRule < publicRule);
        assertTrue(security.contains("/uploads/practice-images/**"));
        assertTrue(security.contains("/uploads/lecturer-assets/**"));
        assertTrue(security.substring(privateRule, publicRule).contains(".denyAll()"));
        assertTrue(draftController.contains("/practice/materials/"));
        assertFalse(draftController.contains("\"url\", \"/uploads/"));
        assertTrue(materialController.contains("PracticeMaterialAccessService"));
        assertTrue(materialController.contains("@PreAuthorize(\"isAuthenticated()\")"));
    }

    private static boolean containsEmojiStyleIcon(String content) {
        return content.codePoints().anyMatch(codePoint -> codePoint >= 0x1F300 && codePoint <= 0x1FAFF);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
