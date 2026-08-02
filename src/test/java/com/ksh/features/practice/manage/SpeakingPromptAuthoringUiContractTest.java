package com.ksh.features.practice.manage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingPromptAuthoringUiContractTest {

    @Test
    void copiedSpeakingQuestionGetsNewUnconfiguredIdentityWithoutPrivateAudio()
            throws Exception {
        String editor = read(
                "src/main/resources/templates/practice/manage/editor.html");
        String copyRegion = between(
                editor,
                "function refreshCopiedQuestionIds(question)",
                "function refreshCopiedGroupIds(group)");

        assertThat(copyRegion).contains(
                "question.clientId = makeClientId('q')",
                "question.audioUrl = ''",
                "question.speakingPromptAudioUrl = ''",
                "inputType: 'manual_text'",
                "deliveryMode: 'text_only'",
                "audioOrigin: 'none'",
                "delete question.speakingPresentation");
    }

    @Test
    void lecturerEditorUsesLockedModesCopyProvenanceAndDedicatedResources()
            throws Exception {
        String editor = read("src/main/resources/templates/practice/manage/editor.html");
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String css = read(
                "src/main/resources/static/css/practice/manage-speaking-prompt-authoring.css");

        assertThat(editor).contains(
                "Tải tệp âm thanh",
                "Nhập nội dung bằng văn bản",
                "Âm thanh đề bài của giảng viên",
                "Ngữ cảnh cho AI — học viên không nhìn thấy",
                "Tạo âm thanh đề bài bằng AI",
                "Câu hỏi chỉ sử dụng văn bản",
                "Thử lại chuyển giọng nói",
                "Tệp âm thanh riêng tư từ Excel đã sẵn sàng",
                "Excel chỉ nhập tệp đã tải lên; không tự gọi STT/TTS hoặc phát sinh chi phí AI.",
                "Xác minh và dùng tệp âm thanh từ Excel",
                "Học viên nghe tệp âm thanh gốc này. Bản chép lời chỉ giúp AI hiểu đề bài; KSH không tạo lại hoặc thay thế âm thanh của giảng viên.",
                "aria-label=\"Chọn tệp âm thanh đề bài\"",
                "aria-label=\"Tiến độ tải tệp âm thanh đề bài\"",
                "/js/practice/manage-speaking-prompt-authoring.js",
                "/css/practice/manage-speaking-prompt-authoring.css");
        assertThat(javascript).contains(
                "new XMLHttpRequest()",
                "xhr.upload.addEventListener('progress'",
                "STATUS_COPY",
                "Đã cũ — cần tạo lại",
                "Audio do AI tạo",
                "Bản cũ",
                "endpointFor(clientId, '/tts')",
                "endpointFor(clientId, '/audio/excel-staging')",
                "endpointFor(clientId, '/transcription/retry')",
                "loadedmetadata",
                "draftVersion",
                "expectedSourceRevision");
        assertThat(javascript)
                .doesNotContain(
                        "openai",
                        "/v1/audio",
                        "providerRequestReference",
                        "fingerprint",
                        "setInterval");
        assertThat(css).contains(
                ".sp-segmented",
                ".sp-state-chip.is-stale",
                ".sp-visually-hidden");
    }

    @Test
    void audioPickerAndDropShareTheClosedAuthoringMediaPolicy()
            throws Exception {
        String editor = read(
                "src/main/resources/templates/practice/manage/editor.html");
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String pickerRegion = between(
                editor,
                "id=\"speaking-prompt-audio-file-input\"",
                "id=\"speaking-prompt-audio-upload-status\"");
        String dropRegion = between(
                javascript,
                "function bindOnce()",
                "async function activate(question)");
        String validationRegion = between(
                javascript,
                "async function validateAuthoringAudioFile(file)",
                "async function upload(file)");
        String uploadRegion = between(
                javascript,
                "async function upload(file)",
                "async function removeOriginal()");

        assertThat(pickerRegion)
                .contains("accept=\".mp3,.wav,.m4a,.ogg,.webm\"")
                .doesNotContain("audio/*");
        assertThat(javascript).contains(
                "'.mp3': Object.freeze(['audio/mpeg'])",
                "'.wav': Object.freeze(['audio/wav', 'audio/x-wav'])",
                "'.m4a': Object.freeze(['audio/mp4', 'audio/x-m4a'])",
                "'.ogg': Object.freeze(['audio/ogg'])",
                "'.webm': Object.freeze(['audio/webm'])");
        assertThat(dropRegion).contains(
                "const file = event.dataTransfer && event.dataTransfer.files[0]",
                "if (file) upload(file)");
        assertThat(editor).contains(
                "window.SpeakingPromptAuthoring.upload(input.files[0])");
        assertThat(validationRegion).contains(
                "AUTHORING_AUDIO_TYPES[extension]",
                "!allowedMimes.includes(declaredMime)",
                "state.maximumUploadBytes",
                "state.maximumUploadSeconds",
                "probeClientAudioDuration(file)",
                "Chỉ chấp nhận file MP3, WAV, M4A, OGG hoặc WebM.",
                "Loại nội dung của file không khớp phần mở rộng.",
                "Tệp audio đang rỗng.",
                "Hãy giảm dung lượng rồi chọn lại.",
                "Hãy cắt ngắn rồi chọn lại.");
        assertThat(uploadRegion.indexOf(
                "await validateAuthoringAudioFile(file)"))
                .isLessThan(uploadRegion.indexOf(
                        "if (localMode !== 'audio_upload')"));
    }

    @Test
    void asyncAuthoringFailuresUseClosedVietnameseCopyWithoutProviderText()
            throws Exception {
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String asyncCopy = between(
                javascript,
                "const ASYNC_ERROR_COPY",
                "const API_ERROR_COPY");
        String apiCopy = between(
                javascript,
                "const API_ERROR_COPY",
                "const POLLABLE");

        assertThat(asyncCopy).contains(
                "INVALID_INPUT:",
                "CONFIGURATION:",
                "RATE_LIMIT:",
                "TIMEOUT:",
                "TRANSPORT:",
                "PROVIDER_REJECTED:",
                "EMPTY_OUTPUT:",
                "MALFORMED_OUTPUT:",
                "STALE_COMPLETION:");
        assertThat(apiCopy).contains(
                "SOURCE_CONFLICT:",
                "FORBIDDEN:",
                "NOT_FOUND:",
                "INVALID_INPUT:",
                "RATE_LIMIT:",
                "AI_UNAVAILABLE:",
                "AI_TEMPORARILY_UNAVAILABLE:",
                "UNPROCESSABLE_AUDIO:",
                "TEMPORARILY_UNAVAILABLE:",
                "RETRY_LIMIT:",
                "NOT_RETRYABLE:");
        assertThat(javascript).contains(
                "function safeRequestErrorMessage(error)",
                "function asyncOperationErrorMessage(operation)",
                "operation.retryable === true",
                "asyncOperationErrorMessage(next.sttOperation)",
                "asyncOperationErrorMessage(next.ttsOperation)")
                .doesNotContain(
                        "payload.message",
                        "showMessage(error.message");
    }

    @Test
    void lowConfidenceBrowserHandoffRequiresConfirmedSaveBeforeAcceptingState()
            throws Exception {
        String editor = read(
                "src/main/resources/templates/practice/manage/editor.html");
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String projectionRegion = between(
                javascript,
                "function renderTranscript(next)",
                "function renderGenerated(next)");
        String saveRegion = between(
                javascript,
                "async function saveTranscript()",
                "async function generateTts()");

        assertThat(editor).contains(
                "Bản chép lời cần giảng viên kiểm tra trước khi dùng làm ngữ cảnh cho AI.",
                "Tôi đã kiểm tra và xác nhận ngữ cảnh này",
                "id=\"speaking-save-transcript\"");
        assertThat(projectionRegion).contains(
                "next.transcriptStatus !== 'needs_review'",
                "next.transcriptConfirmed === true");
        assertThat(saveRegion).contains(
                "Hãy xác nhận ngữ cảnh sau khi kiểm tra.",
                "confirmed: element('speaking-transcript-confirmed')?.checked === true",
                "const accepted = acceptState(clientId, next, request)",
                "transcriptDirty = false",
                "renderTranscript(next)");
        assertThat(saveRegion.indexOf(
                "const accepted = acceptState(clientId, next, request)"))
                .isLessThan(saveRegion.indexOf("transcriptDirty = false"));
    }

    @Test
    void toggleAutosaveGetPreviewAndReloadContainNoProviderCommand()
            throws Exception {
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String saveRegion = between(
                javascript,
                "function queueSave(showErrors)",
                "function saveSnapshot()");
        String pollingRegion = between(
                javascript,
                "function schedulePolling(next)",
                "function stopPolling()");
        String mirrorRegion = between(
                javascript,
                "function syncDraft()",
                "function markGeneratedStaleLocally()");

        assertThat(saveRegion)
                .contains("'PUT'")
                .doesNotContain("endpointFor(clientId, '/tts')", "upload(", "retryTranscription");
        assertThat(pollingRegion)
                .contains("'GET'")
                .doesNotContain("'POST'", "'PUT'", "endpointFor(clientId, '/tts')");
        assertThat(javascript).contains(
                "['speaking-tts-enabled', 'speaking-tts-voice'",
                "element(id)?.addEventListener('change', () => {",
                "scheduleSave();",
                "element('speaking-generate-tts')?.addEventListener('click', generateTts)");
        assertThat(mirrorRegion)
                .contains(
                        "const changed = syncToQuestion(activeQuestion)",
                        "window.onSpeakingPromptDraftMirrored();",
                        "inputType: state.inputType",
                        "ttsEnabled: state.ttsEnabled === true",
                        "question.prompt = state.manualText")
                .doesNotContain("triggerAutosave", "speaking-transcript-context");
    }

    @Test
    void acceptedSpeakingDeliveryMarksWholeDraftDirtyAndRevalidatesEditor()
            throws Exception {
        String editor = read(
                "src/main/resources/templates/practice/manage/editor.html");
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String mirrorRegion = between(
                javascript,
                "function syncDraft()",
                "function markGeneratedStaleLocally()");
        String editorBridge = between(
                editor,
                "window.onSpeakingPromptDraftMirrored = function()",
                "function csrfHeaders()");

        assertThat(mirrorRegion).contains(
                "const changed = syncToQuestion(activeQuestion)",
                "if (changed",
                "typeof window.onSpeakingPromptDraftMirrored === 'function'",
                "window.onSpeakingPromptDraftMirrored();",
                "return changed;");
        assertThat(editorBridge).contains(
                "renderTree();",
                "validateDraft();",
                "triggerAutosave();");
    }

    @Test
    void typedTextOnlyDeliveryCannotBeRehydratedFromLegacyAudio()
            throws Exception {
        String contract = read(
                "src/main/resources/static/js/practice/manage-authoring-contract.js");
        String normalization = between(
                contract,
                "const delivery = canonicalContent.speakingDelivery;",
                "if (q.questionType === 'FILL_BLANK'");

        assertThat(normalization).contains(
                "hasPromptAudioReference",
                "? (delivery.promptAudioReference || '')",
                ": (q.speakingPromptAudioUrl || q.audioUrl || '')",
                "hasPromptPlayLimit",
                "delivery.promptPlayLimit == null",
                "? 0");
    }

    @Test
    void replacedAudioPreviewIsRevisionedAndEditorErrorsAreNonModal()
            throws Exception {
        String editor = read(
                "src/main/resources/templates/practice/manage/editor.html");
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String preview = between(
                javascript,
                "function previewAudioUrl(",
                "function renderLegacyPreview()");
        String toast = between(
                editor,
                "function showEditorToast(",
                "function updateGroupQuestionRange(");

        assertThat(preview).contains(
                "sourceRevision=",
                "encodeURIComponent(",
                "audio.src = previewAudioUrl(asset.contentUrl, sourceRevision)");
        assertThat(editor).contains(
                "id=\"editor-toast-region\"",
                "aria-live=\"assertive\"");
        assertThat(toast).contains(
                "toast.textContent",
                "toast.hidden = false")
                .doesNotContain("alert(");
        assertThat(javascript).contains(
                "AUTH_REQUIRED:",
                "INVALID_RESPONSE:",
                "contentType.includes('application/json')",
                "new URL(response.url, window.location.origin).pathname === '/login'");
    }

    @Test
    void excelAudioAdoptionIsExplicitIdFreeAndSourceLocked()
            throws Exception {
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String adoption = between(
                javascript,
                "async function adoptExcelStaging()",
                "async function retryTranscription()");

        assertThat(adoption)
                .contains(
                        "state.excelStagingAudioAvailable !== true",
                        "await flush()",
                        "beginSourceDestructiveMutation()",
                        "endpointFor(clientId, '/audio/excel-staging')",
                        "expectedSourceRevision: acceptedRevision(clientId)",
                        "expectedDraftVersion: request.expectedDraftVersion",
                        "endSourceDestructiveMutation()")
                .doesNotContain(
                        "assetId",
                        "referenceId",
                        "storageKey",
                        "generateTts",
                        "endpointFor(clientId, '/tts')");
        assertThat(javascript).contains(
                "'speaking-adopt-excel-audio'");
    }

    @Test
    void speakingLifecycleFlushesBeforeEditorNavigationAndRejectsOldAsyncState()
            throws Exception {
        String editor = read("src/main/resources/templates/practice/manage/editor.html");
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String draftService = read(
                "src/main/java/com/ksh/features/practice/manage/service/PracticeDraftService.java");
        String acceptanceRegion = between(
                javascript,
                "function acceptState(",
                "function applyState(");
        String transcriptSaveRegion = between(
                javascript,
                "async function saveTranscript()",
                "async function generateTts()");
        String uploadRegion = between(
                javascript,
                "async function upload(file)",
                "async function removeOriginal()");
        String unlinkRegion = between(
                javascript,
                "async function removeOriginal()",
                "async function retryTranscription()");

        assertThat(editor).contains(
                "window.SpeakingPromptAuthoring.hasPendingChanges()",
                "await window.SpeakingPromptAuthoring.flush()",
                "window.SpeakingPromptAuthoring.deactivate();",
                "async function selectNode(");
        assertThat(javascript).contains(
                "function flush()",
                "function hasPendingChanges()",
                "const acceptedByClient = new Map()",
                "const mutationBaseByClient = new Map()",
                "const pendingMutationSequences = new Map()",
                "return revisionOf(mutationBaseByClient.get(clientId))",
                "!mutationBaseByClient.has(activeClientId)",
                "mutationGeneration !== editGeneration",
                "activeUploadId += 1",
                "progress.removeAttribute('value')");
        assertThat(acceptanceRegion).contains(
                "!request.mutation && activeResponse",
                "request.sequence < lastAppliedSequence",
                "nextDraftVersion !== currentDraftVersion()",
                "!request.initializeMutationBase",
                "nextRevision !== revisionOf(mutationBase)",
                "if (hasPendingMutation(clientId)) return false",
                "nextRevision < knownRevision",
                "nextRevision === knownRevision",
                "const strictlyNewer",
                "request.sequence < lastAppliedSequence",
                "&& !strictlyNewer",
                "Math.max(lastAppliedSequence, request.sequence)");
        assertThat(acceptanceRegion.indexOf("hasUnsavedInput()"))
                .isLessThan(acceptanceRegion.indexOf(
                        "acceptedByClient.set(clientId, next)"));
        assertThat(acceptanceRegion.indexOf(
                "if (request.mutation || request.initializeMutationBase)"))
                .isLessThan(acceptanceRegion.indexOf(
                        "DRAFT_VERSION = Math.max"));
        assertThat(javascript).contains(
                "expectedDraftVersion: request.expectedDraftVersion",
                "'expectedDraftVersion',\n        String(request.expectedDraftVersion)",
                "draftConflict",
                "if (error && error.status === 409)",
                "handleMutationError(error, clientId",
                "transcriptDirty",
                "Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi đổi nguồn đề bài.",
                "Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi gỡ audio.",
                "if (transcriptDirty) return;",
                "Bản nháp đã được chỉnh sửa ở nơi khác.");
        assertThat(javascript).contains(
                "let sourceDestructiveMutationCount = 0",
                "function renderSourceMutationLock()",
                "'speaking-transcript-context'",
                "'speaking-transcript-confirmed'",
                "'speaking-save-transcript'",
                "control.disabled = locked",
                "button.disabled = locked",
                "if (sourceDestructiveMutationPending()) return;");
        assertSourceMutationLockCoversRequest(
                uploadRegion, "const operation = new Promise(");
        assertSourceMutationLockCoversRequest(
                unlinkRegion, "const operation = jsonRequest(");
        assertThat(editor).contains(
                "async function flushSpeakingBeforeStructureMutation()",
                "editorRoot.addEventListener('click', async (e) =>",
                "async function handleAddGroupFromMenu(",
                "async function handleAddQuestionFromMenu(",
                "async function handleDuplicateActive(",
                "async function duplicateSection(",
                "async function duplicateGroup(",
                "async function duplicateQuestion(",
                "async function addTestWrapper(",
                "async function deleteCurrentTest(",
                "async function addSectionBySkill(",
                "async function addSectionWrapper(",
                "async function addGroupWrapper(",
                "async function addQuestionWrapper(",
                "async function addGroupToCurrentSection(",
                "async function addQuestionToCurrentGroup(",
                "async function handleMoveToGroupFromMenu(",
                "async function moveSection(",
                "async function moveGroup(",
                "async function moveQuestion(",
                "async function deleteSection(",
                "async function deleteGroup(",
                "async function deleteQuestion(",
                "await flushSpeakingBeforeStructureMutation()");
        assertGuardPrecedesMutation(
                editor,
                "async function handleMoveToGroupFromMenu(",
                "async function handleDuplicateActive(");
        String duplicateDispatch = between(
                editor,
                "async function handleDuplicateActive(",
                "function handleMoveActive(");
        assertThat(duplicateDispatch).contains(
                "await duplicateSection(sIdx)",
                "await duplicateGroup(sIdx, gIdx)",
                "await duplicateQuestion(sIdx, gIdx, qIdx)");
        assertGuardPrecedesMutation(
                editor,
                "async function duplicateSection(",
                "async function duplicateGroup(");
        assertGuardPrecedesMutation(
                editor,
                "async function duplicateGroup(",
                "async function duplicateQuestion(");
        assertGuardPrecedesMutation(
                editor,
                "async function duplicateQuestion(",
                "async function moveSection(");
        assertGuardPrecedesMutation(
                editor,
                "async function moveSection(",
                "async function moveGroup(");
        assertGuardPrecedesMutation(
                editor,
                "async function moveGroup(",
                "function renderTree(");
        assertGuardPrecedesMutation(
                editor,
                "async function deleteSection(",
                "async function deleteGroup(");
        assertGuardPrecedesMutation(
                editor,
                "async function deleteGroup(",
                "async function deleteQuestion(");
        assertGuardPrecedesMutation(
                editor,
                "async function deleteQuestion(",
                "async function moveQuestion(");
        assertGuardPrecedesMutation(
                editor,
                "async function moveQuestion(",
                "function startRenameSection(");
        String delegatedStructureDispatch = between(
                editor,
                "editorRoot.addEventListener('click', async (e) =>",
                "// Initial nodes expanded.");
        assertThat(delegatedStructureDispatch).contains(
                "await addTestWrapper()",
                "await addSectionBySkill(skill)",
                "await addGroupWrapper()",
                "await addQuestionWrapper()",
                "await deleteCurrentTest(e)");
        String contextAddGroup = between(
                editor,
                "async function handleAddGroupFromMenu(",
                "async function handleAddQuestionFromMenu(");
        String contextAddQuestion = between(
                editor,
                "async function handleAddQuestionFromMenu(",
                "async function flushSpeakingBeforeStructureMutation(");
        assertThat(contextAddGroup).contains(
                "await addGroupWrapper(null, { type: 'section', sIdx: sIdx })");
        assertThat(contextAddQuestion).contains(
                "await addQuestionWrapper(null, { type: 'group', sIdx: sIdx, gIdx: gIdx })");
        assertGuardPrecedesMutation(
                editor,
                "async function addTestWrapper(",
                "async function deleteCurrentTest(");
        assertGuardPrecedesMutation(
                editor,
                "async function deleteCurrentTest(",
                "async function addSectionBySkill(");
        assertGuardPrecedesMutation(
                editor,
                "async function addSectionBySkill(",
                "async function addSectionWrapper(");
        assertGuardPrecedesMutation(
                editor,
                "async function addGroupWrapper(",
                "async function addQuestionWrapper(");
        assertGuardPrecedesMutation(
                editor,
                "async function addQuestionWrapper(",
                "async function addGroupToCurrentSection(");
        String addCompatibilityCallers = between(
                editor,
                "async function addSectionWrapper(",
                "// Node deletion confirmation modal flow");
        assertThat(addCompatibilityCallers).contains(
                "await addSectionBySkill(skill)",
                "await addGroupWrapper(null)",
                "await addQuestionWrapper(null)");
        assertThat(transcriptSaveRegion).contains(
                "const accepted = acceptState(clientId, next, request)",
                "accepted",
                "transcriptDirty = false");
        assertThat(transcriptSaveRegion.indexOf(
                "acceptState(clientId, next, request)"))
                .isLessThan(transcriptSaveRegion.indexOf(
                        "transcriptDirty = false"));
        assertThat(draftService)
                .contains("!Objects.equals(draft.getVersion(), clientVersion)")
                .doesNotContain("draft.getVersion() > clientVersion");
    }

    @Test
    void textOnlyDeliveryOmitsEveryAudioAffordanceWhileAudioBranchesRetainThem()
            throws Exception {
        String editor = read("src/main/resources/templates/practice/manage/editor.html");
        String javascript = read(
                "src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js");
        String preview = read(
                "src/main/resources/static/js/practice/manage-draft-preview.js");
        String previewRegion = between(
                preview,
                "if (item.skill === 'SPEAKING')",
                "const fillBlank = ['FILL_BLANK', 'GAP_FILL']");

        assertThat(editor).contains(
                "id=\"speaking-play-limit-control\"")
                .doesNotContain(
                        "const deliveryMode = delivery.deliveryMode",
                        "deliveryMode === 'text_only'");
        assertThat(previewRegion).contains(
                "const requiresAudio = steps.includes('PROMPT_PLAYBACK')",
                "element('div', 'preview-speaking-audio')",
                "element('span', '', 'Âm thanh đề bài')",
                "if (requiresAudio)",
                "presentation?.promptAudioReference");
        assertThat(javascript).contains(
                "function renderDeliveryControls()",
                "playLimitControl.hidden = textOnly",
                "playLimit.disabled = textOnly");
    }

    @Test
    void transcriptAndLecturerContextNeverAppearInLearnerTemplatesOrPayloadCode()
            throws Exception {
        List<Path> learnerFiles = Files.walk(Path.of("src/main/resources"))
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String value = path.toString().replace('\\', '/');
                    return value.contains("/templates/practice/player")
                            || value.contains("/static/js/practice/player");
                })
                .toList();
        String learnerSurface = learnerFiles.stream()
                .map(SpeakingPromptAuthoringUiContractTest::readUnchecked)
                .reduce("", String::concat);
        String contract = read(
                "src/main/resources/static/js/practice/manage-authoring-contract.js");

        assertThat(learnerSurface).doesNotContain(
                "speaking-transcript-context",
                "speaking-ai-context",
                "lecturerContext",
                "transcriptConfidence",
                "promptContext",
                "prompt_context",
                "artifactId",
                "providerCode",
                "fingerprint",
                "storageKey");
        assertThat(learnerSurface)
                .contains(
                        "deliverySteps",
                        "\"PROMPT_PLAYBACK\"",
                        "showAction(\"Phát đề bài\"")
                .doesNotContain(
                        "deliveryMode ===",
                        "currentQuestion.deliveryMode");
        assertThat(contract)
                .contains(
                        "question-content-v2",
                        "inputType",
                        "deliveryMode",
                        "audioOrigin")
                .doesNotContain("lecturerContext", "transcriptConfidence");
    }

    private static String between(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end));
    }

    private static void assertGuardPrecedesMutation(
            String source,
            String start,
            String end) {
        String region = between(source, start, end);
        assertThat(region).contains(
                "await flushSpeakingBeforeStructureMutation()",
                "DRAFT_DATA");
        assertThat(region.indexOf(
                "await flushSpeakingBeforeStructureMutation()"))
                .isLessThan(region.indexOf("DRAFT_DATA"));
    }

    private static void assertSourceMutationLockCoversRequest(
            String region,
            String requestStart) {
        assertThat(region).contains(
                "beginSourceDestructiveMutation()",
                requestStart,
                "finally {",
                "endSourceDestructiveMutation()");
        assertThat(region.indexOf("beginSourceDestructiveMutation()"))
                .isLessThan(region.indexOf(requestStart));
        assertThat(region.indexOf(requestStart))
                .isLessThan(region.indexOf("finally {"));
        assertThat(region.indexOf("finally {"))
                .isLessThan(region.indexOf("endSourceDestructiveMutation()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
