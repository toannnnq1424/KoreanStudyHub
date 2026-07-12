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

        assertTrue(editor.contains("/js/practice/manage-authoring-contract.js"));
        assertTrue(editor.contains("/js/practice/manage-draft-preview.js"));
        assertFalse(editor.contains("/js/practice/manage-editor.js"));
        for (String type : List.of(
                "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE_NOT_GIVEN",
                "FILL_BLANK", "MATCHING", "ESSAY", "SPEAKING")) {
            assertTrue(editor.contains(type) || contract.contains(type), "Missing editor type " + type);
        }
        assertTrue(contract.contains("question-content-v1"));
        assertTrue(contract.contains("answer-spec-v1"));
        assertTrue(contract.contains("correctOptionIds"));
        assertTrue(contract.contains("matchingPairs"));
        assertTrue(contract.contains("acceptedValues"));
        assertTrue(editor.contains("id=\"q-scoring-policy\""));
        assertTrue(editor.contains("id=\"q-prompt-profile\""));
        assertTrue(editor.contains("id=\"q-rubric-profile\""));
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
        assertTrue(editor.contains("function toggleDotsDropdown(event)"));
        assertTrue(editor.contains("function focusDraftTitle(event)"));
        assertTrue(editor.contains("function confirmDeleteDraft(event)"));
        assertFalse(editor.contains("id=\"practice-editor-data\""));
        assertFalse(editor.contains("th:utext=\"${draftJson}\""));
        assertFalse(editor.contains("[PracticeEditor] state before"));
        assertFalse(editor.contains("[PracticeEditor] state after"));
        assertTrue(contract.contains("question-content-v1"));
        assertFalse(editor.contains("['READING', 'LISTENING'].includes(section.skill)"));
    }

    @Test
    void pdfWorkspaceDefaultsToGuidedModeAndProtectsRawDebugTabs() throws Exception {
        String workspace = read("src/main/resources/templates/practice/manage/import-workspace.html");
        String wizard = read("src/main/resources/templates/practice/manage/import-wizard.html");

        assertTrue(workspace.contains("id=\"mode-guided\""));
        assertTrue(workspace.contains("id=\"mode-advanced\""));
        assertTrue(workspace.contains("FULL_SELECTED_PAGES"));
        assertTrue(workspace.contains("hasAnyRole('HEAD','ADMIN')"));
        assertTrue(workspace.contains("data.privilegedDetails"));
        assertTrue(workspace.contains("renderRegionSpecificFields(type, ann || {}, true)"));
        assertTrue(workspace.contains("id=\"region-destination-summary\""));
        assertTrue(workspace.contains("Cách AI đọc tài liệu"));
        assertTrue(workspace.contains("function openLearnerPreview()"));
        assertFalse(workspace.contains("Hybrid - Khuyên dùng"));
        assertFalse(workspace.contains("📁"));
        assertFalse(workspace.contains("📂"));
        assertFalse(workspace.contains("🎯"));
        assertTrue(wizard.contains("authoringCatalog.templates"));
        assertTrue(wizard.contains("name=\"examTemplateCode\""));
        assertTrue(wizard.contains("id=\"target-section\"")
                || wizard.contains("id=\"target-skill\""));
        assertFalse(wizard.contains("value=\"EXTENDED_PRACTICE\""));
        assertFalse(wizard.contains("value=\"GENERAL_KOREAN\""));
    }

    @Test
    void excelImportSurfaceIncludesRowPreviewAndAutomaticValidRowImport() throws Exception {
        String excel = read("src/main/resources/templates/practice/manage/excel-import.html");
        String dashboard = read("src/main/resources/templates/practice/manage/dashboard.html");

        assertTrue(excel.contains("/practice/manage/excel/templates/"));
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
        assertTrue(excel.contains("templateCode"));
        assertTrue(excel.contains("SELECTED_TEST_NO"));
        assertTrue(excel.contains("SELECTED_LESSON_CODE"));
        assertTrue(excel.contains("row.detail?.groupImageReference"));
        assertTrue(excel.contains("row.correctAnswer"));
        assertTrue(excel.contains("row.importedQuestionNo"));
        assertTrue(excel.contains("position:fixed;inset:0;margin:auto"));
        assertTrue(excel.contains("Nhóm / Bài đọc"));
        assertTrue(excel.contains("Nhóm / Transcript"));
        assertTrue(excel.contains("Option H / Cặp 8"));
        assertTrue(excel.contains("row.detail?.matchingPairs"));
        assertTrue(excel.contains("id=\"excel-media-files\""));
        assertTrue(excel.contains("mediaOverrides"));
        assertTrue(excel.contains("URL.createObjectURL"));
        assertTrue(excel.contains("uploadPendingMedia"));
        assertFalse(dashboard.contains("@{/practice/manage/excel}"));
        assertTrue(dashboard.contains("Nhập Excel nằm trong từng phần kỹ năng"));
    }

    @Test
    void sharedPracticeShellLoadsIdempotentDropdownBehavior() throws Exception {
        String head = read("src/main/resources/templates/fragments/head.html");
        String app = read("src/main/resources/static/js/app.js");

        assertTrue(head.contains("defer th:src=\"@{/js/app.js}\""));
        assertTrue(app.contains("__KSH_SHARED_APP_INITIALIZED__"));
    }

    @Test
    void renderedResourcesRemainUtf8AndAvoidEmojiStyleProductIcons() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/resources/templates"),
                Path.of("src/main/resources/static/js"),
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

    private static boolean containsEmojiStyleIcon(String content) {
        return content.codePoints().anyMatch(codePoint -> codePoint >= 0x1F300 && codePoint <= 0x1FAFF);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
