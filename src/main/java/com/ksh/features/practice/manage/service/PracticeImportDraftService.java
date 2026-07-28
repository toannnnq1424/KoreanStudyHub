package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.manage.speaking.SpeakingPromptSourceRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeImportDraftService {

    private static final Logger log = LoggerFactory.getLogger(PracticeImportDraftService.class);

    private final PracticeDraftRepository draftRepository;
    private final PracticePdfImportSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final PracticeAuthorizationService authorizationService;
    private final SpeakingPromptSourceRepository promptSourceRepository;
    private final PracticeMaterialReferenceRepository materialReferenceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public PracticeImportDraftService(PracticeDraftRepository draftRepository,
                                      PracticePdfImportSessionRepository sessionRepository,
                                      ObjectMapper objectMapper,
                                      PracticeAuthorizationService authorizationService,
                                      SpeakingPromptSourceRepository promptSourceRepository,
                                      PracticeMaterialReferenceRepository materialReferenceRepository) {
        this.draftRepository = draftRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.promptSourceRepository = promptSourceRepository;
        this.materialReferenceRepository = materialReferenceRepository;
    }

    PracticeImportDraftService(PracticeDraftRepository draftRepository,
                               PracticePdfImportSessionRepository sessionRepository,
                               ObjectMapper objectMapper,
                               SpeakingPromptSourceRepository promptSourceRepository,
                               PracticeMaterialReferenceRepository materialReferenceRepository) {
        this(draftRepository, sessionRepository, objectMapper, null,
                promptSourceRepository, materialReferenceRepository);
    }

    public PracticeImportDraftService(PracticeDraftRepository draftRepository,
                                      PracticePdfImportSessionRepository sessionRepository,
                                      ObjectMapper objectMapper) {
        this(draftRepository, sessionRepository, objectMapper, null, null, null);
    }

    @Transactional
    public PracticeDraft createManualDraftFromSession(Long sessionId, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền quản lý session này.");
        }

        if (session.getLinkedDraftId() == null) {
            throw new IllegalStateException("Session chưa chạy AI hoặc không có AI Draft liên kết.");
        }
        PracticeDraft aiDraft = lockAuthorizedDraft(
                editableDraft(session.getLinkedDraftId(), userId));
        requirePristinePdfOnlyDraft(aiDraft, userId);

        // Copy and elevate AI Draft to MANUAL mode
        PracticeDraft manualDraft = new PracticeDraft(
                aiDraft.getTitle(),
                aiDraft.getDescription(),
                aiDraft.getScope(),
                null,
                "DRAFT",
                aiDraft.getOwnerId(),
                aiDraft.getDraftJson()
        );
        manualDraft.setCreationMethod("MANUAL"); // set to manual creation so it integrates with manual editor
        
        PracticeDraft saved = draftRepository.save(manualDraft);
        
        session.setLinkedDraftId(saved.getId());
        session.setStatus("REVIEWING");
        sessionRepository.save(session);
        
        log.info("[ImportDraftService] Created manual draft id={} from import session id={}", saved.getId(), sessionId);
        return saved;
    }

    @Transactional
    public PracticeDraft attachToExistingDraft(Long sessionId, Long targetDraftId, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền quản lý session này.");
        }

        if (session.getLinkedDraftId() == null) {
            throw new IllegalStateException("Session chưa chạy AI hoặc không có AI Draft liên kết.");
        }
        if (java.util.Objects.equals(
                session.getLinkedDraftId(), targetDraftId)) {
            throw new IllegalArgumentException(
                    "Bản nháp đích phải khác bản nháp tạm của phiên import.");
        }

        PracticeDraft aiDraft = lockAuthorizedDraft(
                editableDraft(session.getLinkedDraftId(), userId));
        requirePristinePdfOnlyDraft(aiDraft, userId);

        PracticeDraft targetDraft = editableDraft(targetDraftId, userId);

        try {
            // Read target json & ai json to merge sections
            JsonNode aiRoot = objectMapper.readTree(aiDraft.getDraftJson());
            JsonNode targetRoot = objectMapper.readTree(targetDraft.getDraftJson());

            ObjectNode mergedRoot = targetRoot.deepCopy();
            ArrayNode targetSections = mergedRoot.has("sections") ? (ArrayNode) mergedRoot.get("sections") : mergedRoot.putArray("sections");

            if (aiRoot.has("sections")) {
                ArrayNode aiSections = (ArrayNode) aiRoot.get("sections");
                for (JsonNode sec : aiSections) {
                    targetSections.add(sec.deepCopy());
                }
            }

            targetDraft.setDraftJson(mergedRoot.toString());
            PracticeDraft saved = draftRepository.save(targetDraft);

            // Clean up temporary AI Draft
            draftRepository.delete(aiDraft);

            session.setLinkedDraftId(saved.getId());
            session.setStatus("MERGED_TO_MANUAL_DRAFT");
            sessionRepository.save(session);

            log.info("[ImportDraftService] Attached import result to draft id={}", targetDraftId);
            return saved;
        } catch (Exception e) {
            log.error("[ImportDraftService] Failed to attach import session to draft id={}", targetDraftId, e);
            throw new RuntimeException("Không thể ghép kết quả import vào bản nháp.", e);
        }
    }

    private PracticeDraft editableDraft(Long draftId, Long actorId) {
        if (authorizationService == null) {
            return draftRepository.findByIdAndOwnerId(draftId, actorId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Không tìm thấy bản nháp tương ứng."));
        }
        PracticeAuthorizationService.Decision decision =
                authorizationService.requireDraft(
                        draftId, actorId, PracticeAction.EDIT);
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Không tìm thấy bản nháp tương ứng."));
        if (!java.util.Objects.equals(
                decision.ownerId(), draft.getOwnerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chủ sở hữu bản nháp không khớp quyền đã xác thực.");
        }
        return draft;
    }

    private PracticeDraft lockAuthorizedDraft(PracticeDraft authorizedDraft) {
        PracticeDraft locked = draftRepository.findByIdForUpdate(
                        authorizedDraft.getId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Không tìm thấy bản nháp tương ứng."));
        if (!java.util.Objects.equals(
                authorizedDraft.getOwnerId(), locked.getOwnerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chủ sở hữu bản nháp đã thay đổi sau khi xác thực.");
        }
        return locked;
    }

    /**
     * PDF import can copy only a draft whose identity is still wholly carried
     * by the import session and JSON. Speaking v2 sources and draft material
     * bindings are draft-local mutable state; copying their client/audio
     * identity without an approved migration would either orphan that state or
     * make the copied draft point at another draft's private media.
     */
    private void requirePristinePdfOnlyDraft(
            PracticeDraft lockedDraft,
            Long actorId) {
        if (promptSourceRepository == null
                || materialReferenceRepository == null) {
            throw new IllegalStateException(
                    "Không thể xác minh trạng thái nguồn của bản nháp import.");
        }
        if (!java.util.Objects.equals(
                lockedDraft.getOwnerId(), actorId)
                && authorizationService == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền quản lý bản nháp import này.");
        }
        if (!promptSourceRepository
                .findByDraftIdForUpdate(lockedDraft.getId())
                .isEmpty()) {
            throw sourceIdentityConflict();
        }
        if (!materialReferenceRepository
                .findByDraftId(lockedDraft.getId())
                .isEmpty()) {
            throw sourceIdentityConflict();
        }
        if (containsMutableSpeakingIdentity(lockedDraft.getDraftJson())) {
            throw sourceIdentityConflict();
        }
    }

    private boolean containsMutableSpeakingIdentity(String draftJson) {
        try {
            JsonNode root = objectMapper.readTree(draftJson);
            if (root == null || !root.isObject()) {
                return true;
            }
            for (JsonNode section : root.path("sections")) {
                boolean speakingSection =
                        "SPEAKING".equalsIgnoreCase(
                                section.path("skill").asText(""));
                for (JsonNode group : section.path("groups")) {
                    for (JsonNode question : group.path("questions")) {
                        if (!speakingSection
                                && !"SPEAKING".equalsIgnoreCase(
                                        question.path("questionType")
                                                .asText(""))) {
                            continue;
                        }
                        JsonNode content = question.path("questionContent");
                        boolean explicitV2 =
                                "question-content-v2".equals(
                                        content.path("schemaVersion").asText(""))
                                || content.has("speakingDelivery");
                        boolean authoringIdentity =
                                question.has("speakingPromptAuthoring");
                        boolean audioIdentity =
                                hasText(question, "speakingPromptAudioUrl")
                                || hasText(question, "audioUrl")
                                || hasText(
                                        content.path("speakingDelivery"),
                                        "promptAudioReference");
                        if (explicitV2
                                || authoringIdentity
                                || audioIdentity) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception exception) {
            return true;
        }
    }

    private static boolean hasText(JsonNode node, String field) {
        return node != null
                && node.hasNonNull(field)
                && !node.path(field).asText("").isBlank();
    }

    private static IllegalStateException sourceIdentityConflict() {
        return new IllegalStateException(
                "Bản nháp AI đã có nguồn hoặc tài nguyên Speaking gắn riêng. "
                        + "Hãy tiếp tục chỉnh sửa bản nháp đó; hệ thống không "
                        + "tự sao chép hoặc xóa định danh audio/clientId.");
    }
}
