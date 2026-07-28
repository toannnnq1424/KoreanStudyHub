package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SpeakingPromptDraftVersionConflictTest {

    @Test
    void dedicatedMutationRequiresTheExactWholeDraftVersion() {
        SpeakingPromptDraftAuthority authority =
                new SpeakingPromptDraftAuthority(
                        mock(PracticeAuthorizationService.class),
                        mock(PracticeDraftRepository.class),
                        new ObjectMapper());
        PracticeDraft draft = new PracticeDraft(
                "Bản nháp",
                "",
                "PRIVATE",
                null,
                "DRAFT",
                81L,
                "{\"sections\":[]}");
        draft.setVersion(4);
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                new SpeakingPromptDraftAuthority.AuthorizedDraft(
                        draft,
                        81L,
                        81L,
                        null,
                        null);

        assertThatCode(() -> authority.requireExpectedVersion(authorized, 4L))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                () -> authority.requireExpectedVersion(authorized, 3L))
                .isInstanceOf(SpeakingPromptAuthoringConflictException.class);
        assertThatThrownBy(
                () -> authority.requireExpectedVersion(authorized, 5L))
                .isInstanceOf(SpeakingPromptAuthoringConflictException.class);
    }

    @Test
    void everySourceTaskBindingAndTranscriptMutationChecksDraftFirst()
            throws Exception {
        String authoring = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/speaking/"
                        + "SpeakingPromptAuthoringService.java"));
        String transcript = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/speaking/"
                        + "SpeakingPromptTranscriptService.java"));

        assertDraftCheckPrecedesSourceAccess(between(
                authoring, "SourceResult saveManualPrompt(", "SourceResult selectAudioMode("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring, "SourceResult selectAudioMode(", "void requireUploadAllowed("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring,
                "void requireUploadAllowed(",
                "EnqueueResult bindVerifiedOriginalUpload("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring,
                "SourceResult markTtsConfigurationChanged(",
                "VerifiedOriginalAudioProof verifyOriginalAudio("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring,
                "EnqueueResult bindOriginalAudioAndEnqueueStt(",
                "EnqueueResult requestTts("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring, "EnqueueResult requestTts(", "RetryResult retryCurrentOperation("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring, "RetryResult retryCurrentOperation(", "SourceResult unlinkCurrentOriginalAudio("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring, "SourceResult unlinkCurrentOriginalAudio(", "boolean cancelCurrentOperation("));
        assertDraftCheckPrecedesSourceAccess(between(
                authoring, "boolean cancelCurrentOperation(", "private EnqueueResult enqueueTask("));
        assertDraftCheckPrecedesSourceAccess(between(
                transcript, "RevisionResult revise(", "RevisionResult confirm("));
        assertDraftCheckPrecedesSourceAccess(between(
                transcript, "RevisionResult confirm(", "public record ReviseTranscript("));

        String uploadBinding = between(
                authoring,
                "EnqueueResult bindOriginalAudioAndEnqueueStt(",
                "EnqueueResult requestTts(");
        assertThatCode(() -> {
            int draftCheck = uploadBinding.indexOf(
                    "draftAuthority.requireExpectedVersion(");
            int sourceCheck = uploadBinding.indexOf(
                    "source.requireExpectedRevision(");
            int binding = uploadBinding.indexOf(
                    "assetService.bindVerifiedOriginalAsset(");
            if (draftCheck < 0
                    || binding < 0
                    || draftCheck >= binding
                    || (sourceCheck >= 0 && sourceCheck >= binding)) {
                throw new AssertionError(
                        "Original binding must follow exact draft/source checks.");
            }
        }).doesNotThrowAnyException();
    }

    private static void assertDraftCheckPrecedesSourceAccess(String method) {
        assertThatCode(() -> {
            int check = method.indexOf("draftAuthority.requireExpectedVersion(");
            int source = method.indexOf("sourceRepository");
            int requiredSource = method.indexOf("requireSourceForUpdate(");
            if (requiredSource >= 0
                    && (source < 0 || requiredSource < source)) {
                source = requiredSource;
            }
            if (check < 0 || source < 0 || check >= source) {
                throw new AssertionError(
                        "Exact draft check must precede source access.");
            }
        }).doesNotThrowAnyException();
    }

    private static String between(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end));
    }
}
