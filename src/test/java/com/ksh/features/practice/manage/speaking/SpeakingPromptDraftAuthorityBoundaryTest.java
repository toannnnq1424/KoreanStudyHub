package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptDraftAuthorityBoundaryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private PracticeAuthorizationService authorization;
    private PracticeDraftRepository drafts;
    private SpeakingPromptDraftAuthority authority;

    @BeforeEach
    void setUp() {
        authorization = mock(PracticeAuthorizationService.class);
        drafts = mock(PracticeDraftRepository.class);
        authority = new SpeakingPromptDraftAuthority(authorization, drafts, mapper);
    }

    @Test
    void lockedAuthorizationSupportsPromptAndAuthoringModeTransitions() throws Exception {
        PracticeDraft draft = speakingDraft(10L, "q-1", "기존 질문");
        when(authorization.requireDraft(41L, 10L, PracticeAction.EDIT))
                .thenReturn(new PracticeAuthorizationService.Decision(10L, false));
        when(drafts.findByIdForUpdate(41L)).thenReturn(Optional.of(draft));

        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                authority.authorizeAndLock(41L, "q-1", 10L, PracticeAction.EDIT);
        authority.replacePromptAndAuthoringOptions(
                authorized, "새 질문", true, "ko-KR-voice", new BigDecimal("1.10"), "mp3");

        SpeakingPromptDraftAuthority.DraftAuthoringOptions options =
                authority.authoringOptions(authorized);
        JsonNode persisted = mapper.readTree(draft.getDraftJson());
        JsonNode question = persisted.path("sections").get(0).path("groups").get(0)
                .path("questions").get(0);
        assertThat(question.path("prompt").asText()).isEqualTo("새 질문");
        assertThat(options.inputType()).isEqualTo(SpeakingPromptSource.INPUT_MANUAL_TEXT);
        assertThat(options.ttsEnabled()).isTrue();
        assertThat(options.voiceCode()).isEqualTo("ko-KR-voice");
        assertThat(options.speed()).isEqualByComparingTo("1.10");
        assertThat(options.outputFormat()).isEqualTo("mp3");

        authority.selectAudioAuthoringMode(authorized);
        assertThat(authority.authoringOptions(authorized).inputType())
                .isEqualTo(SpeakingPromptSource.INPUT_AUDIO_UPLOAD);
        verify(drafts).findByIdForUpdate(41L);
    }

    @Test
    void missingOptionsHaveSafeDefaultsAndMalformedSpeedIsUnavailable() {
        PracticeDraft draft = speakingDraft(10L, "q-1", "질문");
        when(authorization.requireDraft(41L, 10L, PracticeAction.READ))
                .thenReturn(new PracticeAuthorizationService.Decision(10L, false));
        when(drafts.findById(41L)).thenReturn(Optional.of(draft));
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                authority.authorize(41L, "q-1", 10L, PracticeAction.READ);

        SpeakingPromptDraftAuthority.DraftAuthoringOptions defaults =
                authority.authoringOptions(authorized);
        assertThat(defaults.inputType()).isEqualTo("audio_upload");
        assertThat(defaults.ttsEnabled()).isFalse();
        assertThat(defaults.voiceCode()).isEmpty();
        assertThat(defaults.speed()).isNull();
        assertThat(defaults.outputFormat()).isEmpty();

        authorized.question().putObject("speakingPromptAuthoring").put("speed", "not-a-number");
        assertThat(authority.authoringOptions(authorized).speed()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankPromptIsRejected(String prompt) {
        assertThatThrownBy(() -> authority.replacePromptText(authorized(), prompt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("từ 1 đến");
    }

    @Test
    void promptLengthAcceptsDeclaredMaximumAndRejectsMaximumPlusOne() {
        String maximum = "가".repeat(SpeakingPromptAiContract.MAX_PROMPT_TEXT_CHARS);
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized = authorized();
        assertThatCode(() -> authority.replacePromptText(authorized, maximum))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authority.replacePromptText(authorized, maximum + "가"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleNegativeAndNullVersionBoundariesFailClosed() {
        PracticeDraft draft = speakingDraft(10L, "q-1", "질문");
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized = authorized(draft);
        draft.setVersion(null);
        assertThatCode(() -> authority.requireExpectedVersion(authorized, 0L))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authority.requireExpectedVersion(authorized, -1L))
                .isInstanceOf(SpeakingPromptAuthoringConflictException.class);
        assertThatThrownBy(() -> authority.requireExpectedVersion(null, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ownerMismatchAndMissingDraftAreBounded() {
        PracticeDraft draft = speakingDraft(10L, "q-1", "질문");
        when(authorization.requireDraft(41L, 99L, PracticeAction.EDIT))
                .thenReturn(new PracticeAuthorizationService.Decision(12L, false));
        when(drafts.findByIdForUpdate(41L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> authority.authorizeAndLock(
                41L, "q-1", 99L, PracticeAction.EDIT))
                .isInstanceOf(AccessDeniedException.class);

        when(drafts.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authority.loadCurrent(404L, "q-1", 10L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void invalidQuestionClientIdIsNotFound(String clientId) {
        PracticeDraft draft = speakingDraft(10L, "q-1", "질문");
        when(drafts.findById(41L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> authority.loadCurrent(41L, clientId, 10L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void malformedDuplicateAndWrongTypeDraftsFailClosed() {
        assertLocateFailure("not-json", IllegalStateException.class);
        assertLocateFailure("[]", IllegalStateException.class);
        assertLocateFailure(document("q-1", "MCQ", false), EntityNotFoundException.class);
        assertLocateFailure(document("q-1", "SPEAKING", true), IllegalStateException.class);
        assertLocateFailure(document("another", "SPEAKING", false), EntityNotFoundException.class);
    }

    @Test
    void loadAndLockRequireExpectedOwnerAndExposeBoundedPrompt() {
        PracticeDraft draft = speakingDraft(10L, "q-1", "질문");
        when(drafts.findById(41L)).thenReturn(Optional.of(draft));
        when(drafts.findByIdForUpdate(41L)).thenReturn(Optional.of(draft));

        SpeakingPromptDraftAuthority.DraftPrompt current =
                authority.loadCurrent(41L, "q-1", 10L);
        assertThat(current.promptText()).isEqualTo("질문");
        SpeakingPromptDraftAuthority.LockedDraft locked = authority.lockDraft(41L, 10L);
        assertThat(authority.locateInLockedDraft(locked, "q-1").promptText())
                .isEqualTo("질문");
        assertThatThrownBy(() -> authority.loadCurrent(41L, "q-1", 11L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authority.lockDraft(41L, 11L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void assertLocateFailure(String json, Class<? extends Throwable> type) {
        PracticeDraft draft = draft(10L, json);
        when(drafts.findById(41L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> authority.loadCurrent(41L, "q-1", 10L))
                .isInstanceOf(type);
    }

    private SpeakingPromptDraftAuthority.AuthorizedDraft authorized() {
        return authorized(speakingDraft(10L, "q-1", "질문"));
    }

    private SpeakingPromptDraftAuthority.AuthorizedDraft authorized(PracticeDraft draft) {
        try {
            var root = (com.fasterxml.jackson.databind.node.ObjectNode)
                    mapper.readTree(draft.getDraftJson());
            var question = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("sections")
                    .get(0).path("groups").get(0).path("questions").get(0);
            return new SpeakingPromptDraftAuthority.AuthorizedDraft(
                    draft, 10L, 10L, root, question);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private PracticeDraft speakingDraft(Long ownerId, String clientId, String prompt) {
        return draft(ownerId, document(clientId, "SPEAKING", false)
                .replace("__PROMPT__", prompt));
    }

    private PracticeDraft draft(Long ownerId, String json) {
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "PRIVATE", null, "DRAFT", ownerId, json);
        ReflectionTestUtils.setField(draft, "id", 41L);
        return draft;
    }

    private static String document(String clientId, String questionType, boolean duplicate) {
        String question = "{\"clientId\":\"" + clientId
                + "\",\"questionType\":\"" + questionType
                + "\",\"prompt\":\"__PROMPT__\"}";
        return "{\"sections\":[{\"skill\":\"SPEAKING\",\"groups\":[{\"questions\":["
                + question + (duplicate ? "," + question : "") + "]}]}]}";
    }
}
