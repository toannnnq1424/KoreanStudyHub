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
        String speakingAuthoring = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String preview = read("src/main/resources/static/js/practice/manage-draft-preview.js");
        String editorCss = read(
                "src/main/resources/static/css/practice/manage-editor.css");

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
        assertTrue(speakingAuthoring.contains("question.speakingPromptAudioUrl = currentAudio"));
        assertTrue(editor.contains("Học viên nghe tệp âm thanh gốc này."));
        assertTrue(contract.contains("content.speakingDelivery"));
        assertTrue(preview.contains("const speakingPresentation = question.speakingPresentation"));
        assertTrue(preview.contains("speakingPresentation.preparationSeconds"));
        assertTrue(preview.contains("speakingPresentation.responseSeconds"));
        assertFalse(preview.contains("speakingDelivery.promptAudioReference"));
        assertTrue(preview.contains("section.listeningCheckAudioReference"));
        assertTrue(editor.contains("function isCurrentEditorTarget(type, target)"));
        assertTrue(editor.contains("const target = type === 'group'"));
        assertTrue(editor.contains("group && group.questions[currentNode.qIdx]"));
        assertTrue(editor.contains("const optionId = question && question.options[idx] && question.options[idx].id"));
        assertTrue(editor.contains("isCurrentTarget = () => true"));
        assertTrue(editor.contains("if (isCurrentEditorTarget('question', question)) renderOptionRows(question)"));
        assertTrue(editor.contains("async function readEditorJsonResponse(response, fallbackMessage)"));
        assertTrue(editor.contains("contentType.includes('application/json')"));
        assertTrue(editor.contains("Phiên đăng nhập đã hết hạn."));
        assertTrue(editor.contains("X-Requested-With"));
        assertTrue(editor.contains("editorJsonFetch(`/practice/manage/drafts/${DRAFT_ID}/upload-image`"));
        assertTrue(editor.contains("editorJsonFetch(`/practice/manage/drafts/${DRAFT_ID}/upload-audio`"));
        assertTrue(editor.contains(
                "const payload = await editorJsonFetch(\n"
                        + "        `/practice/manage/drafts/${DRAFT_ID}/preview`"));
        assertTrue(editor.contains("const assets = await editorJsonFetch(url"));
        assertTrue(editor.contains("async function linkEditorImageAsset(assetId)"));
        assertTrue(editor.contains(
                "`/practice/manage/drafts/${DRAFT_ID}/assets`"));
        assertFalse(editor.contains("const payload = await response.json();"));
        assertTrue(editor.contains("function requireEditorUploadPayload(payload)"));
        assertTrue(editor.contains(
                "url.match(/^\\/practice\\/materials\\/([1-9]\\d*)\\/content$/)"));
        assertTrue(editor.contains("Number.isSafeInteger(assetId)"));
        assertTrue(editor.contains("String(assetId) !== match[1]"));
        assertTrue(editor.contains("if (terminalStatus) autosaveBlocked = true"));
        assertTrue(editor.contains("if (draftDirty && !autosaveBlocked"));
        assertTrue(editor.contains(
                "aria-label=\"Kiểm tra chất lượng đề — di chuột hoặc dùng nút Kiểm tra để mở\""));
        assertTrue(editor.contains(
                "aria-label=\"Cấu trúc đề — di chuột hoặc dùng nút menu để mở rộng\""));
        assertTrue(editor.contains("class=\"toolbar-title-block\""));
        assertTrue(editor.contains("class=\"toolbar-actions-scroll\""));
        assertTrue(editor.contains("class=\"toolbar-action-label\""));
        assertTrue(editor.contains("class=\"validation-handle\""));
        assertTrue(editor.contains("id=\"validation-panel-trigger\""));
        assertTrue(editor.contains("onclick=\"toggleValidationPanel(event)\""));
        assertTrue(editor.contains("class=\"validation-close\""));
        assertTrue(editor.contains("id=\"structure-panel-trigger\""));
        assertTrue(editor.contains("onclick=\"toggleStructurePanel(event)\""));
        assertTrue(editor.contains(
                "class=\"tree-row-content\" role=\"button\" tabindex=\"0\""));
        assertTrue(editor.contains("onpointerdown=\"setStructurePanelOpen(true); this.focus()\""));
        assertTrue(editor.contains("onkeydown=\"handleTreeRowKey("));
        assertTrue(editor.contains("class=\"q-summary-prompt\""));
        assertFalse(editor.contains("width: 450px"));
        assertTrue(editor.contains("class=\"opt-card-grid\""));
        assertTrue(editor.contains(
                "id=\"asset-library-drawer\" class=\"asset-library-drawer\""));
        assertTrue(editor.contains("id=\"editor-validation-panel\""));
        assertTrue(editor.contains("aria-controls=\"editor-validation-panel\""));
        assertFalse(editor.contains(
                "<aside class=\"panel-validation\" tabindex=\"0\""));
        assertTrue(editor.contains("role=\"status\""));
        assertTrue(editor.contains("aria-live=\"polite\""));
        assertTrue(editorCss.contains("--editor-toolbar-height: 72px"));
        assertTrue(editorCss.contains("--editor-validation-handle: 18px"));
        assertTrue(editorCss.contains(
                "height: calc(100dvh - var(--editor-toolbar-height))"));
        assertTrue(editorCss.contains("width: clamp(252px, 20vw, 280px)"));
        assertTrue(editorCss.contains("grid-template-rows: 62px 54px"));
        assertTrue(editorCss.contains(".toolbar-actions-scroll"));
        assertTrue(editorCss.contains("gap: 0"));
        assertTrue(editorCss.contains("overflow-x: auto"));
        assertTrue(editorCss.contains("@media (max-width: 900px)"));
        assertTrue(editorCss.contains("@media (max-width: 620px)"));
        assertTrue(editorCss.contains(".panel-structure.is-expanded > .tree-wrapper"));
        assertTrue(editorCss.contains("width: 72px"));
        assertTrue(editorCss.contains("calc(100vw - 72px)"));
        assertTrue(editorCss.contains("width: min(300px, calc(100vw - 72px))"));
        assertTrue(editorCss.contains(".tree-meta-text"));
        assertTrue(editorCss.contains("text-overflow: ellipsis"));
        assertTrue(editorCss.contains(
                ".panel-structure:not(:hover):not(.is-expanded) .tree-text-title"));
        assertTrue(editorCss.contains(".tree-row.active .tree-actions"));
        assertTrue(editorCss.contains("transform: translateX(100%)"));
        assertTrue(editorCss.contains(".validation-handle"));
        assertTrue(editorCss.contains(".validation-handle:focus-visible"));
        assertTrue(editorCss.contains("pointer-events: none"));
        assertTrue(editorCss.contains(".panel-validation.is-open"));
        assertTrue(editorCss.contains(".asset-library-drawer.is-open"));
        assertTrue(editorCss.contains("top: var(--editor-toolbar-height)"));
        assertTrue(editorCss.contains("width: min(350px, 100vw)"));
        assertTrue(editorCss.contains(".opt-card-grid"));
        assertTrue(editorCss.contains("grid-template-columns: auto auto auto minmax(0, 1fr) auto auto"));
    }

    @Test
    void responsiveEditorKeepsTitleAndStructureReadableWithoutChangingValidationOverlay()
            throws Exception {
        String editorCss = read(
                "src/main/resources/static/css/practice/manage-editor.css");
        String breadcrumbRule = between(
                editorCss, ".breadcrumb-text {", ".draft-title-input {");
        String titleRule = between(
                editorCss, ".draft-title-input {", ".draft-title-input:focus {");
        String structureRule = between(
                editorCss, ".panel-structure {", ".panel-header {");
        String treeMetaRule = between(
                editorCss, ".tree-meta-text {", "/* circular badge for question numbers */");
        String mediumRules = between(
                editorCss, "@media (max-width: 900px) {", "@media (max-width: 620px) {");
        String mobileRules = between(
                editorCss, "@media (max-width: 620px) {", "@media (prefers-reduced-motion: reduce) {");
        String validationRule = between(
                editorCss, ".panel-validation {", ".validation-handle {");

        assertTrue(breadcrumbRule.contains("white-space: nowrap"));
        assertTrue(breadcrumbRule.contains("text-overflow: ellipsis"));
        assertTrue(titleRule.contains("display: block"));
        assertTrue(titleRule.contains("width: 100%"));
        assertTrue(titleRule.contains("min-width: 0"));
        assertTrue(structureRule.contains("width: clamp(252px, 20vw, 280px)"));
        assertFalse(editorCss.contains("clamp(190px, 15vw, 232px)"));
        assertFalse(editorCss.contains("clamp(150px, 32vw, 190px)"));
        assertTrue(editorCss.contains("margin-left: 10px"));
        assertTrue(editorCss.contains("margin-left: 14px"));
        assertTrue(editorCss.contains("margin-left: 18px"));
        assertTrue(treeMetaRule.contains("white-space: nowrap"));
        assertTrue(treeMetaRule.contains("overflow: hidden"));
        assertTrue(treeMetaRule.contains("text-overflow: ellipsis"));

        assertTrue(mediumRules.contains(".breadcrumb-text"));
        assertTrue(mediumRules.contains("display: none"));
        assertTrue(mediumRules.contains(".draft-title-input"));
        assertTrue(mediumRules.contains("width: 100%"));
        assertTrue(mobileRules.contains("width: 72px"));
        assertTrue(mobileRules.contains(
                "width: min(300px, calc(100vw - 72px))"));
        assertTrue(mobileRules.contains(".panel-structure.is-expanded > .tree-wrapper"));

        assertTrue(validationRule.contains("position: fixed"));
        assertTrue(validationRule.contains("transform: translateX(100%)"));
        assertTrue(validationRule.contains("pointer-events: none"));
        assertTrue(editorCss.contains(
                ".panel-validation:hover,\n.panel-validation.is-open"));
        assertTrue(editorCss.contains(".validation-handle:focus-visible"));
    }

    @Test
    void constrainedAppHeaderUsesCompleteCompactNavigationInsteadOfPracticeOnly()
            throws Exception {
        String header = read("src/main/resources/templates/fragments/app-header.html");
        String appShellCss = read("src/main/resources/static/css/app-shell.css");
        String practiceCss = read("src/main/resources/static/css/practice-index.css");
        String compactNavigation = between(
                header, "<div class=\"dropdown nav-compact-dropdown\">", "</nav>");
        String wideRules = between(
                appShellCss, "@media (max-width: 1440px) {", "@media (max-width: 1280px) {");
        String compactRules = between(
                appShellCss, "@media (max-width: 1280px) {", "@media (max-width: 720px) {");
        String phoneRules = between(
                appShellCss, "@media (max-width: 560px) {", "@media (max-width: 420px) {");

        assertTrue(header.contains("<nav class=\"nav\" aria-label=\"Điều hướng chính\">"));
        assertTrue(compactNavigation.contains("class=\"nav-compact-trigger\""));
        assertTrue(compactNavigation.contains("data-toggle=\"dropdown\""));
        assertTrue(compactNavigation.contains("aria-haspopup=\"menu\""));
        assertTrue(compactNavigation.contains("aria-expanded=\"false\""));
        assertTrue(compactNavigation.contains("aria-controls=\"app-compact-navigation-menu\""));
        assertTrue(compactNavigation.contains("role=\"menu\""));
        assertTrue(compactNavigation.contains("aria-label=\"Các khu vực của KSH\""));
        for (String route : List.of(
                "@{/}", "@{/profile}", "@{/my/classes}", "@{/my/flashcards}",
                "@{/practice}", "@{/practice/progress}", "@{/practice/manage}",
                "@{/practice/manage/revisions}", "@{/practice/manage/materials}",
                "@{/lecturer/dashboard}", "@{/lecturer/classes}", "@{/lecturer/library}",
                "@{/lecturer/question-bank}", "@{/leader}", "@{/admin/dashboard}")) {
            assertTrue(compactNavigation.contains(route),
                    "Compact navigation is missing route " + route);
        }
        assertTrue(compactNavigation.contains("hasRole('STUDENT')"));
        assertTrue(compactNavigation.contains("hasRole('LECTURER')"));
        assertTrue(compactNavigation.contains("hasAnyRole('LECTURER','LEADER','ADMIN')"));
        assertFalse(compactNavigation.contains("'HEAD'"));
        assertFalse(compactNavigation.contains("@{/head}"));

        assertTrue(wideRules.contains(".user-chip .name"));
        assertTrue(wideRules.contains("display: none"));
        assertTrue(compactRules.contains(".nav > a"));
        assertTrue(compactRules.contains(".nav > .nav-practice-dropdown"));
        assertTrue(compactRules.contains(".nav > .nav-compact-dropdown"));
        assertTrue(compactRules.contains("display: block"));
        assertFalse(appShellCss.contains(".nav { display: none; }"));
        assertTrue(phoneRules.contains(".nav-compact-menu"));
        assertTrue(phoneRules.contains("position: fixed"));
        assertTrue(phoneRules.contains("left: 12px"));
        assertTrue(phoneRules.contains("right: 12px"));

        assertFalse(practiceCss.contains(".pi-body > .header .nav > a"));
        assertFalse(practiceCss.contains(".pi-body > .header .nav-practice-dropdown"));
        assertTrue(header.contains("class=\"user-chip\""));
        assertTrue(header.contains("aria-label=\"Mở menu tài khoản\""));
        assertTrue(header.contains("<form th:action=\"@{/logout}\" method=\"post\">"));
        assertTrue(header.contains("th:name=\"${_csrf.parameterName}\""));
        assertTrue(header.contains("th:value=\"${_csrf.token}\""));
    }

    @Test
    void ordinaryUploadPayloadIsValidatedBeforeEditorStateMutation() throws Exception {
        String editor = read("src/main/resources/templates/practice/manage/editor.html");
        String imageUpload = between(
                editor,
                "function uploadImageFile(file, type)",
                "function updateImagePreview(url, type)");
        String audioUpload = between(
                editor,
                "function uploadDraftAudio(file, statusElementId, onUploaded, isCurrentTarget = () => true)",
                "function uploadAudioFile(file)");
        String optionUpload = between(
                editor,
                "function uploadOptionImage(file, idx)",
                "function removeOptionImage(idx)");

        assertTrue(imageUpload.contains(
                "const upload = requireEditorUploadPayload(data)"));
        assertTrue(imageUpload.indexOf("requireEditorUploadPayload(data)")
                < imageUpload.indexOf("setGroupImageUrl(target, upload.url)"));
        assertTrue(audioUpload.contains(
                "const upload = requireEditorUploadPayload(data)"));
        assertTrue(audioUpload.indexOf("requireEditorUploadPayload(data)")
                < audioUpload.indexOf("onUploaded(upload)"));
        assertTrue(optionUpload.contains(
                "const upload = requireEditorUploadPayload(data)"));
        assertTrue(optionUpload.indexOf("requireEditorUploadPayload(data)")
                < optionUpload.indexOf("option.imageReference = upload.url"));

        String imagePreview = between(
                editor,
                "function updateImagePreview(url, type)",
                "function removeImage(type)");
        String audioPreview = between(
                editor,
                "function updateAudioPreview(url)",
                "function updateSpeakingPromptAudioPreview(url)");
        String assetList = between(
                editor,
                "function renderAssetsList(assets)",
                "async function insertAssetUrlToSelectedField(assetId, url)");
        String assetInsert = between(
                editor,
                "async function insertAssetUrlToSelectedField(assetId, url)",
                "async function deleteLibraryAsset(id)");
        String assetDrop = between(
                editor,
                "function setupDropzoneEvents(dropzone, uploadFn)",
                "function handleAudioSelect(input)");
        String assetApply = between(
                editor,
                "async function attachAndApplyEditorImage(",
                "function performAutosave()");
        assertTrue(editor.contains("function isPrivatePracticeMaterialUrl(value)"));
        assertTrue(editor.contains(
                "/^\\/practice\\/materials\\/[1-9]\\d*\\/content$/.test(value)"));
        assertTrue(editor.contains(
                "function privatePracticeMaterialAssetId(value)"));
        assertTrue(editor.contains(
                "if (isPrivatePracticeMaterialUrl(droppedUrl))"));
        assertTrue(imagePreview.contains(
                "if (!isPrivatePracticeMaterialUrl(url))"));
        assertTrue(imagePreview.contains("container.replaceChildren()"));
        assertTrue(imagePreview.contains("image.src = url"));
        assertTrue(imagePreview.contains("reference.textContent = url"));
        assertFalse(imagePreview.contains("innerHTML"));
        assertTrue(audioPreview.contains(
                "if (!isPrivatePracticeMaterialUrl(url))"));
        assertTrue(audioPreview.contains("container.replaceChildren()"));
        assertTrue(audioPreview.contains("download.href = url"));
        assertTrue(audioPreview.contains("audio.src = url"));
        assertFalse(audioPreview.contains("innerHTML"));
        assertTrue(assetList.contains("if (!Array.isArray(assets))"));
        assertTrue(assetList.contains("Number.isSafeInteger(assetId)"));
        assertTrue(assetList.contains("assetId <= 0"));
        assertTrue(assetList.contains(
                "fileUrl: `/practice/materials/${assetId}/content`"));
        assertTrue(assetList.contains("title.textContent"));
        assertTrue(assetList.contains("addEventListener("));
        assertFalse(assetList.contains("innerHTML"));
        assertFalse(assetList.contains("onclick="));
        assertTrue(assetInsert.contains(
                "url !== expectedUrl"));
        assertTrue(assetInsert.contains(
                "!isPrivatePracticeMaterialUrl(url)"));
        assertTrue(assetInsert.indexOf(
                "await attachAndApplyEditorImage(")
                < assetInsert.indexOf(
                        "alert(\"Đã gắn ảnh vào bản nháp thành công!\")"));
        assertTrue(assetInsert.contains("catch (error)"));
        assertFalse(assetInsert.contains("imgInput.value = url"));
        assertTrue(assetDrop.indexOf(
                "await attachAndApplyEditorImage(")
                < assetDrop.indexOf(
                        "error && error.message"));
        assertTrue(assetApply.indexOf("await linkEditorImageAsset(assetId)")
                < assetApply.indexOf("setGroupImageUrl(target, url)"));
        assertTrue(assetApply.indexOf("await linkEditorImageAsset(assetId)")
                < assetApply.indexOf("target.imageUrl = url"));
        assertTrue(assetApply.contains(
                "if (!isCurrentEditorTarget(targetType, target))"));
        assertFalse(editor.contains("e.target.value = fileUrl"));
        assertTrue(editor.contains("error.textContent = e && e.message"));
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
        assertTrue(workspace.contains("Không gian nhập PDF | KSH"));
        assertTrue(workspace.contains("Xem trước ảnh cắt"));
        assertTrue(workspace.contains("<option value=\"SPEAKING\">Nói</option>"));
        assertTrue(workspace.contains("Nhà cung cấp: chưa gọi"));
        assertTrue(workspace.contains(
                "`Nhà cung cấp: ${opts.provider || 'điểm cuối OpenAI/Gemini'}`"));
        assertTrue(workspace.contains("Mã yêu cầu: -"));
        assertTrue(workspace.contains(
                "`Mã yêu cầu: ${opts.requestId || lastAiRequestId}`"));
        assertTrue(workspace.contains("Đã cắt ảnh"));
        assertTrue(workspace.contains("Ảnh cắt từ PDF"));
        assertTrue(workspace.contains("Đích nhập: Bài kiểm tra"));
        assertFalse(workspace.contains("PDF Import Workspace"));
        assertFalse(workspace.contains("Crop Preview"));
        assertFalse(workspace.contains("Provider: chưa gọi"));
        assertFalse(workspace.contains("Request ID: -"));
        assertFalse(workspace.contains("Đã crop"));
        assertFalse(workspace.contains("Khi crop một vùng IMAGE_ASSET"));
        assertFalse(workspace.contains("`Test ${"));
        assertTrue(wizard.contains("Không gian giảng viên"));
        assertTrue(wizard.contains("Bài kiểm tra và phần kỹ năng"));
        assertTrue(wizard.contains("|Bài kiểm tra ${section.testNo}"));
        assertFalse(wizard.contains("Lecturer Workspace"));
        assertFalse(wizard.contains(">Test và phần kỹ năng<"));
        assertFalse(wizard.contains("|Test ${section.testNo}"));
        assertTrue(workspace.contains("FULL_SELECTED_PAGES"));
        assertFalse(workspace.contains("hasAnyRole('LEADER','ADMIN')"));
        assertFalse(workspace.contains("data.privilegedDetails"));
        assertFalse(workspace.contains("JSON kỹ thuật"));
        assertFalse(workspace.contains("Request JSON"));
        assertTrue(workspace.contains("renderRegionSpecificFields(type, ann || {}, true)"));
        assertTrue(workspace.contains("id=\"region-destination-summary\""));
        assertTrue(workspace.contains("Cách AI đọc tài liệu"));
        assertTrue(workspace.contains("function openLearnerPreview()"));
        assertTrue(workspace.contains("id=\"tool-draw\""));
        assertTrue(workspace.contains("title=\"Khoanh vùng để cắt ảnh (D)\""));
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
        assertTrue(excel.contains("Không gian giảng viên"));
        assertTrue(excel.contains("Tệp XLSX"));
        assertTrue(excel.contains("Với câu Nói"));
        assertTrue(excel.contains("Âm thanh câu hỏi"));
        assertTrue(excel.contains("Tải tệp mẫu"));
        assertTrue(excel.contains("Ảnh/âm thanh đi kèm"));
        assertTrue(excel.contains("Xem trước Excel"));
        assertTrue(excel.contains("Bài kiểm tra / Phần"));
        assertTrue(excel.contains("Chọn tệp khác"));
        assertTrue(excel.contains("`Bài kiểm tra ${"));
        assertFalse(excel.contains("Lecturer workspace"));
        assertFalse(excel.contains("Excel preview"));
        assertFalse(excel.contains("Test / Section"));
        assertFalse(excel.contains("`Test ${"));
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
        assertTrue(dashboard.contains(
                "Bạn có thể nhập Excel trong từng phần kỹ năng."));
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
        assertTrue(dashboard.contains("Luyện tập KSH"));
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
    void selectedRevisionsExposeOnlyServerDerivedReadingListeningRecoveryActions()
            throws Exception {
        String revisions = read(
                "src/main/resources/templates/practice/manage/revisions.html");
        String controller = read(
                "src/main/java/com/ksh/features/practice/manage/controller/PracticeManageController.java");
        String restController = read(
                "src/main/java/com/ksh/features/practice/manage/controller/PracticeExplanationController.java");
        String query = read(
                "src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryService.java");
        String retry = read(
                "src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryService.java");
        String taskRepository = read(
                "src/main/java/com/ksh/features/practice/repository/QuestionExplanationGenerationTaskRepository.java");

        assertTrue(revisions.contains("selectedSetId != null"));
        assertTrue(revisions.contains("explanationRecoveryRows"));
        assertTrue(revisions.contains("row.retryableAction()"));
        assertTrue(revisions.contains(
                "/explanations/{questionVersionId}/retry"));
        assertTrue(revisions.contains("_csrf.parameterName"));
        assertTrue(revisions.contains("row.state().name() == 'RATE_LIMITED'"));
        assertTrue(revisions.contains("disabled"));
        assertTrue(revisions.contains(
                "row.state().name() == 'FAILED_NON_RETRYABLE'"));
        assertTrue(revisions.contains("Sửa nội dung và xuất bản lại"));
        assertTrue(revisions.contains("row.state().name() == 'READY'"));
        assertTrue(revisions.contains("Không cần thao tác"));
        assertTrue(revisions.contains("row.state().name() == 'PENDING'"));
        assertTrue(revisions.contains("Không tạo yêu cầu trùng"));
        assertFalse(revisions.contains("artifactId"));
        assertFalse(revisions.contains("taskId"));
        assertFalse(revisions.contains("provider"));

        assertTrue(controller.contains(
                "explanationRecoveryQueryService.load("));
        assertTrue(controller.contains(
                "explanationRetryService.retryQuestionVersion("));
        assertTrue(controller.contains(
                "return \"redirect:/practice/manage/revisions?setId=\" + setId;"));
        assertTrue(controller.contains(
                "Không thể xử lý yêu cầu thử lại"));

        assertTrue(restController.contains(
                "@PreAuthorize(Roles.PREAUTH_LECTURER)"));
        assertFalse(restController.contains("PREAUTH_LECTURER_OR_ABOVE"));
        assertTrue(restController.contains("TOO_MANY_REQUESTS"));
        assertTrue(restController.contains("HttpHeaders.RETRY_AFTER"));
        assertTrue(restController.contains("HttpStatus.CONFLICT"));
        assertTrue(restController.contains("ResponseEntity.accepted()"));

        assertTrue(query.indexOf("authorizationService.requireSet(")
                < query.indexOf("publishedVersionRepository.findAllById("));
        assertTrue(query.contains(
                "findByPublishedVersionIdInOrderByPublishedVersionIdAscSectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc"));
        assertTrue(query.contains(
                "findByQuestionVersionIdInAndExplanationLanguage"));
        assertTrue(query.contains("artifactRepository.findAllById"));
        assertTrue(query.contains("taskRepository.findByArtifactIdIn"));
        assertTrue(query.contains("questionRepository.findAllById(sourceQuestionIds)"));
        assertTrue(query.contains("sectionRepository.findAllById(sourceSectionIds)"));
        assertTrue(query.contains("sourceBindingsByQuestion"));
        assertTrue(query.contains("validTaskSource"));
        assertTrue(query.contains("RecoveryState state = validBinding"));
        assertFalse(query.contains("validBinding && validTaskSource"));
        assertFalse(query.contains("questionRepository.findById("));
        assertFalse(query.contains("sectionRepository.findById("));
        assertFalse(query.contains("bindingRepository.findByQuestionVersionIdAnd"));
        assertFalse(query.contains(".generate("));
        assertFalse(query.contains("findByIdForUpdate"));

        for (String state : List.of(
                "READY",
                "PENDING",
                "FAILED_RETRYABLE",
                "RATE_LIMITED",
                "FAILED_NON_RETRYABLE")) {
            assertTrue(retry.contains(state), "Missing recovery state " + state);
        }
        assertTrue(retry.contains("Duration.ofMinutes(1)"));
        assertTrue(retry.contains("PracticeAction.PUBLISH"));
        assertTrue(retry.indexOf("authorizationService.requireGlobal(")
                < retry.indexOf(
                        "bindingRepository.findByArtifactIdOrderByIdAsc"));
        assertTrue(retry.indexOf("authorizationService.requireSet(")
                < retry.indexOf("findByArtifactIdForUpdate"));
        int artifactNullGuard = retry.indexOf("if (artifact == null)");
        int readyGuard = retry.indexOf(
                "QuestionExplanationArtifact.STATUS_READY.equals",
                artifactNullGuard);
        int taskSourceGuard = retry.indexOf(
                "if (task == null || !validTaskSource)", readyGuard);
        assertTrue(artifactNullGuard >= 0);
        assertTrue(readyGuard >= 0);
        assertTrue(taskSourceGuard >= 0);
        assertTrue(artifactNullGuard < readyGuard);
        assertTrue(readyGuard < taskSourceGuard);
        int persistenceClear = retry.indexOf("entityManager.clear()");
        int initialSourceCheck = retry.indexOf(
                "hasValidTaskSource(currentArtifact, currentTask)");
        int taskLock = retry.indexOf("findByArtifactIdForUpdate");
        int lockedSourceCheck = retry.indexOf(
                "hasValidTaskSource(currentArtifact, task)", taskLock);
        int artifactLock = retry.indexOf("findByIdForUpdate", taskLock);
        assertTrue(initialSourceCheck >= 0);
        assertTrue(initialSourceCheck < persistenceClear);
        assertTrue(persistenceClear >= 0);
        assertTrue(persistenceClear < taskLock);
        assertTrue(taskLock < lockedSourceCheck);
        assertTrue(lockedSourceCheck < artifactLock);
        assertTrue(retry.contains("validTaskSource("));
        assertTrue(retry.contains("statusCode.length() != 3"));
        assertTrue(retry.contains(
                "character -> character >= '0' && character <= '9'"));
        assertTrue(retry.contains("status >= 500 && status <= 599"));
        assertTrue(taskRepository.contains(
                "findByArtifactIdIn(Collection<Long> artifactIds)"));
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
        String manageController = read("src/main/java/com/ksh/features/practice/manage/controller/PracticeManageController.java");
        String practiceCss = read("src/main/resources/static/css/practice-index.css");
        String appShellCss = read("src/main/resources/static/css/app-shell.css");
        String manageDashboardCss = read("src/main/resources/static/css/practice/manage-dashboard.css");

        assertTrue(index.contains("Kho luyện tập | KSH"));
        assertTrue(index.contains("Bạch Hổ KSH"));
        assertFalse(index.contains("Kho luyện tập TOPIK"));
        assertFalse(progress.contains("TOPIK II Cấp"));
        assertFalse(progress.contains("TOPIK I Cấp"));
        assertTrue(index.contains("sec:authorize=\"hasRole('LECTURER')\""));
        assertFalse(index.contains("hasAnyRole('LECTURER','LEADER','ADMIN')"));
        assertFalse(sidebar.contains("hasAnyRole('LECTURER','LEADER','ADMIN')"));
        assertTrue(sidebar.contains("sec:authorize=\"hasRole('STUDENT')\""));
        assertTrue(sidebar.contains("<span class=\"pi-nav-text\">Luyện tập</span>"));
        assertTrue(sidebar.contains("<span class=\"pi-nav-text\">Quản lý bộ đề</span>"));
        assertFalse(sidebar.contains("<span class=\"pi-nav-text\">Kho đề</span>"));

        assertTrue(header.contains("class=\"dropdown nav-practice-dropdown\""));
        assertTrue(header.contains("class=\"nav-practice-link\" th:href=\"@{/practice}\">Luyện tập</a>"));
        assertTrue(header.contains("sec:authorize=\"hasRole('STUDENT')\" th:href=\"@{/practice/progress}\""));
        assertTrue(header.contains("sec:authorize=\"hasRole('LECTURER')\" th:href=\"@{/practice/manage}\""));
        assertTrue(header.contains("aria-label=\"Mở menu tài khoản\""));
        assertTrue(header.contains("aria-haspopup=\"menu\""));

        assertTrue(roles.contains("PREAUTH_STUDENT = \"hasRole('STUDENT')\""));
        assertTrue(security.contains(".requestMatchers(\"/practice/progress\", \"/practice/profile\").hasRole(Roles.STUDENT)"));
        assertTrue(controller.contains("@GetMapping(PracticeRoutes.PROGRESS)\n    @PreAuthorize(Roles.PREAUTH_STUDENT)"));
        assertTrue(index.contains("class=\"pc-side-section\" sec:authorize=\"hasRole('STUDENT')\""));

        assertTrue(dashboard.contains("Quản lý bộ đề"));
        assertTrue(dashboard.contains("Bộ đề của tôi"));
        assertTrue(dashboard.contains("Bộ đề của giảng viên khác"));
        assertTrue(dashboard.contains("Không gian giảng viên"));
        assertTrue(dashboard.contains("Xem trước"));
        assertFalse(dashboard.contains("Manage Test Sets"));
        assertFalse(dashboard.contains(">Preview<"));
        assertFalse(dashboard.contains("Shared with me"));
        assertFalse(dashboard.contains("Học liệu của tôi"));
        assertTrue(dashboard.contains("preview=true"));
        assertTrue(dashboard.contains("preview=1"));
        assertTrue(dashboard.contains("/css/practice/manage-dashboard.css"));
        assertTrue(dashboard.contains("class=\"pm-action-list\""));
        assertTrue(dashboard.contains("pm-action-btn--primary"));
        assertTrue(dashboard.contains("pm-action-btn--preview"));
        assertTrue(dashboard.contains("pm-action-btn--warning"));
        assertTrue(dashboard.contains("pm-action-btn--danger"));
        assertTrue(manageDashboardCss.contains(".pm-action-btn:focus-visible"));
        assertTrue(manageDashboardCss.contains(".pm-action-collaboration[open]"));
        assertTrue(manageDashboardCss.contains(".pm-dashboard-heading"));
        assertTrue(manageDashboardCss.contains(".pm-dashboard-stats"));
        assertTrue(manageDashboardCss.contains("@media (max-width: 680px)"));
        assertTrue(manageController.contains("@RequestParam(value = \"preview\", defaultValue = \"false\") boolean preview"));
        assertTrue(manageController.contains("return preview ? editorUrl + \"?preview=1\" : editorUrl;"));

        assertTrue(practiceCss.contains("height: auto;"));
        assertTrue(practiceCss.contains(".pi-body > .header"));
        assertTrue(practiceCss.contains(".pi-sidebar:hover ~ .pi-main-wrapper"));
        assertTrue(practiceCss.contains("margin-left: 260px;"));
        assertTrue(practiceCss.contains("@media (min-width: 761px)"));
        assertTrue(practiceCss.contains("@media (max-width: 760px)"));
        assertFalse(practiceCss.contains(
                "@media (min-width: 721px) and (max-width: 1280px)"));
        assertFalse(practiceCss.contains(".pi-body > .header .nav > a"));
        assertFalse(practiceCss.contains(".pi-body > .header .nav-practice-dropdown"));
        assertTrue(practiceCss.contains("height: 68px"));
        assertTrue(practiceCss.contains("flex-direction: row"));
        assertTrue(practiceCss.contains(".pi-sidebar:hover .pi-nav-text"));
        assertTrue(practiceCss.contains("opacity: 1"));
        assertTrue(appShellCss.contains("white-space: nowrap"));
        assertTrue(appShellCss.contains(".user-chip .name"));
        assertTrue(appShellCss.contains(".user-chip .avatar::first-letter"));
        assertTrue(appShellCss.contains("font-size: 0"));
        assertTrue(appShellCss.contains("@media (max-width: 1440px)"));
        assertTrue(appShellCss.contains("@media (max-width: 1280px)"));
        assertTrue(appShellCss.contains("@media (max-width: 560px)"));
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

        int publicAllowlist = security.indexOf("\"/uploads/avatars/**\"");
        int denyAllFallback =
                security.indexOf(".requestMatchers(\"/uploads/**\").denyAll()");
        assertTrue(publicAllowlist >= 0 && publicAllowlist < denyAllFallback);
        String publicRules = security.substring(publicAllowlist, denyAllFallback);
        assertTrue(publicRules.contains("\"/uploads/exams/**\""));
        assertFalse(publicRules.contains("\"/uploads/questions/**\""));
        assertFalse(publicRules.contains("\"/uploads/options/**\""));
        assertTrue(publicRules.contains(").permitAll()"));
        assertFalse(security.contains(
                ".requestMatchers(\"/uploads/**\").permitAll()"));
        assertTrue(draftController.contains("/practice/materials/"));
        assertFalse(draftController.contains("\"url\", \"/uploads/"));
        assertTrue(materialController.contains("PracticeMaterialAccessService"));
        assertTrue(materialController.contains("@PreAuthorize(\"isAuthenticated()\")"));
        assertTrue(materialController.contains(".noStore()"));
        assertTrue(materialController.contains(".mustRevalidate()"));
    }

    private static boolean containsEmojiStyleIcon(String content) {
        return content.codePoints().anyMatch(codePoint -> codePoint >= 0x1F300 && codePoint <= 0x1FAFF);
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, "Missing start marker: " + start);
        assertTrue(endIndex > startIndex, "Missing end marker: " + end);
        return source.substring(startIndex, endIndex);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
