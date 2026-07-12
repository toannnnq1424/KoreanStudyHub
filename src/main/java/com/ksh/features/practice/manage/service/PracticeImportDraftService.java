package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.repository.PracticeDraftRepository;
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

    @org.springframework.beans.factory.annotation.Autowired
    public PracticeImportDraftService(PracticeDraftRepository draftRepository,
                                      PracticePdfImportSessionRepository sessionRepository,
                                      ObjectMapper objectMapper,
                                      PracticeAuthorizationService authorizationService) {
        this.draftRepository = draftRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
    }

    public PracticeImportDraftService(PracticeDraftRepository draftRepository,
                                      PracticePdfImportSessionRepository sessionRepository,
                                      ObjectMapper objectMapper) {
        this(draftRepository, sessionRepository, objectMapper, null);
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

        PracticeDraft aiDraft = editableDraft(session.getLinkedDraftId(), userId);

        // Copy and elevate AI Draft to MANUAL mode
        PracticeDraft manualDraft = new PracticeDraft(
                aiDraft.getTitle(),
                aiDraft.getDescription(),
                aiDraft.getCategory(),
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

        PracticeDraft aiDraft = editableDraft(session.getLinkedDraftId(), userId);

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
        authorizationService.requireDraft(draftId, actorId, PracticeAction.EDIT, null);
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Không tìm thấy bản nháp tương ứng."));
    }
}
