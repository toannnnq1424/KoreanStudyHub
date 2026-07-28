package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lecturer-authoring authority for a stable draft question client ID.
 * Learner attempts, learner media and learner transcription are deliberately
 * not consulted here.
 */
@Component
public class SpeakingPromptDraftAuthority {

    private final PracticeAuthorizationService authorizationService;
    private final PracticeDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    public SpeakingPromptDraftAuthority(
            PracticeAuthorizationService authorizationService,
            PracticeDraftRepository draftRepository,
            ObjectMapper objectMapper) {
        this.authorizationService = authorizationService;
        this.draftRepository = draftRepository;
        this.objectMapper = objectMapper;
    }

    AuthorizedDraft authorizeAndLock(
            Long draftId,
            String questionClientId,
            Long actorId,
            PracticeAction action) {
        return authorize(draftId, questionClientId, actorId, action, true);
    }

    AuthorizedDraft authorize(
            Long draftId,
            String questionClientId,
            Long actorId,
            PracticeAction action) {
        return authorize(draftId, questionClientId, actorId, action, false);
    }

    void requireExpectedVersion(
            AuthorizedDraft authorized,
            long expectedDraftVersion) {
        Integer current = Objects.requireNonNull(authorized, "authorized")
                .draft()
                .getVersion();
        long currentVersion = current == null ? 0L : current.longValue();
        if (expectedDraftVersion < 0L
                || currentVersion != expectedDraftVersion) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Practice draft version is stale.");
        }
    }

    private AuthorizedDraft authorize(
            Long draftId,
            String questionClientId,
            Long actorId,
            PracticeAction action,
            boolean lock) {
        PracticeAuthorizationService.Decision decision =
                authorizationService.requireDraft(draftId, actorId, action);
        PracticeDraft draft = (lock
                ? draftRepository.findByIdForUpdate(draftId)
                : draftRepository.findById(draftId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bản nháp không tồn tại."));
        if (!Objects.equals(decision.ownerId(), draft.getOwnerId())) {
            throw new AccessDeniedException(
                    "Chủ sở hữu bản nháp không khớp quyền đã xác thực.");
        }
        LocatedQuestion located = locate(draft, questionClientId);
        return new AuthorizedDraft(
                draft, decision.ownerId(), actorId, located.root(), located.question());
    }

    DraftPrompt loadCurrent(
            Long draftId,
            String questionClientId,
            Long expectedOwnerId) {
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bản nháp không tồn tại."));
        if (!Objects.equals(expectedOwnerId, draft.getOwnerId())) {
            throw new AccessDeniedException(
                    "Bản nháp không thuộc chủ sở hữu của tác vụ.");
        }
        LocatedQuestion located = locate(draft, questionClientId);
        return new DraftPrompt(
                draft.getId(),
                draft.getOwnerId(),
                questionClientId,
                located.question().path("prompt").asText(""));
    }

    LockedDraft lockDraft(Long draftId, Long expectedOwnerId) {
        PracticeDraft draft = draftRepository.findByIdForUpdate(draftId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bản nháp không tồn tại."));
        if (!Objects.equals(expectedOwnerId, draft.getOwnerId())) {
            throw new AccessDeniedException(
                    "Bản nháp không thuộc chủ sở hữu của tác vụ.");
        }
        return new LockedDraft(draft);
    }

    DraftPrompt locateInLockedDraft(
            LockedDraft locked,
            String questionClientId) {
        PracticeDraft draft = Objects.requireNonNull(locked, "locked").draft();
        LocatedQuestion located = locate(draft, questionClientId);
        return new DraftPrompt(
                draft.getId(),
                draft.getOwnerId(),
                questionClientId,
                located.question().path("prompt").asText(""));
    }

    void replacePromptText(AuthorizedDraft authorized, String promptText) {
        if (promptText == null
                || promptText.isBlank()
                || promptText.length() > SpeakingPromptAiContract.MAX_PROMPT_TEXT_CHARS) {
            throw new IllegalArgumentException(
                    "Nội dung đề Nói phải có từ 1 đến "
                            + SpeakingPromptAiContract.MAX_PROMPT_TEXT_CHARS
                            + " ký tự.");
        }
        authorized.question().put("prompt", promptText);
        authorized.draft().setDraftJson(authorized.root().toString());
    }

    DraftAuthoringOptions authoringOptions(AuthorizedDraft authorized) {
        JsonNode options = authorized.question().path("speakingPromptAuthoring");
        return new DraftAuthoringOptions(
                options.path("inputType").asText("audio_upload"),
                options.path("ttsEnabled").asBoolean(false),
                options.path("voiceCode").asText(""),
                decimal(options.path("speed").asText("")),
                options.path("outputFormat").asText(""));
    }

    void replacePromptAndAuthoringOptions(
            AuthorizedDraft authorized,
            String promptText,
            boolean ttsEnabled,
            String voiceCode,
            BigDecimal speed,
            String outputFormat) {
        replacePromptText(authorized, promptText);
        ObjectNode options = authoringObject(authorized.question());
        options.put("inputType", SpeakingPromptSource.INPUT_MANUAL_TEXT);
        options.put("ttsEnabled", ttsEnabled);
        options.put("voiceCode", voiceCode);
        options.put("speed", speed);
        options.put("outputFormat", outputFormat);
        options.put("contractVersion", SpeakingPromptAiContract.CONTRACT_VERSION);
        authorized.draft().setDraftJson(authorized.root().toString());
    }

    void selectAudioAuthoringMode(AuthorizedDraft authorized) {
        ObjectNode options = authoringObject(authorized.question());
        options.put("inputType", SpeakingPromptSource.INPUT_AUDIO_UPLOAD);
        authorized.draft().setDraftJson(authorized.root().toString());
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static ObjectNode authoringObject(ObjectNode question) {
        JsonNode existing = question.get("speakingPromptAuthoring");
        return existing instanceof ObjectNode object
                ? object
                : question.putObject("speakingPromptAuthoring");
    }

    private LocatedQuestion locate(PracticeDraft draft, String questionClientId) {
        if (questionClientId == null
                || questionClientId.isBlank()
                || questionClientId.length() > 100) {
            throw new EntityNotFoundException("Câu hỏi Nói không tồn tại.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(draft.getDraftJson());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể đọc cấu trúc bản nháp hiện tại.", exception);
        }
        if (!(root instanceof ObjectNode objectRoot)) {
            throw new IllegalStateException("Draft document must be a JSON object.");
        }
        ObjectNode match = null;
        for (JsonNode section : root.path("sections")) {
            if (!"SPEAKING".equalsIgnoreCase(section.path("skill").asText())) {
                continue;
            }
            for (JsonNode group : section.path("groups")) {
                for (JsonNode candidate : group.path("questions")) {
                    if (!questionClientId.equals(candidate.path("clientId").asText())) {
                        continue;
                    }
                    if (!(candidate instanceof ObjectNode question)
                            || !"SPEAKING".equalsIgnoreCase(
                                    candidate.path("questionType").asText())) {
                        throw new EntityNotFoundException(
                                "Câu hỏi không thuộc phạm vi đề Nói.");
                    }
                    if (match != null) {
                        throw new IllegalStateException(
                                "Question client ID is not unique inside the draft.");
                    }
                    match = question;
                }
            }
        }
        if (match == null) {
            throw new EntityNotFoundException("Câu hỏi Nói không tồn tại.");
        }
        return new LocatedQuestion(objectRoot, match);
    }

    private record LocatedQuestion(ObjectNode root, ObjectNode question) {
    }

    record AuthorizedDraft(
            PracticeDraft draft,
            Long ownerId,
            Long actorId,
            ObjectNode root,
            ObjectNode question) {
        @Override
        public String toString() {
            return "AuthorizedDraft{draftId=" + draft.getId()
                    + ", ownerId=" + ownerId + '}';
        }
    }

    record DraftPrompt(
            Long draftId,
            Long ownerId,
            String questionClientId,
            String promptText) {
        @Override
        public String toString() {
            return "DraftPrompt{draftId=" + draftId
                    + ", ownerId=" + ownerId
                    + ", questionClientId='" + questionClientId + '\''
                    + ", promptTextLength="
                    + (promptText == null ? 0 : promptText.length())
                    + '}';
        }
    }

    record DraftAuthoringOptions(
            String inputType,
            boolean ttsEnabled,
            String voiceCode,
            BigDecimal speed,
            String outputFormat) {
    }

    record LockedDraft(PracticeDraft draft) {
        @Override
        public String toString() {
            return "LockedDraft{draftId=" + draft.getId() + '}';
        }
    }
}
