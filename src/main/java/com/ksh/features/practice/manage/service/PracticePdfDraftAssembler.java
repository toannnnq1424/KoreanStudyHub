package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticePdfDraftAssembler {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfDraftAssembler.class);

    private final PracticeDraftRepository draftRepository;
    private final PracticePdfImportSessionService sessionService;
    private final ObjectMapper objectMapper;

    public PracticePdfDraftAssembler(PracticeDraftRepository draftRepository,
                                     PracticePdfImportSessionService sessionService,
                                     ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PracticeDraft assembleAndSaveDraft(PracticePdfImportSession session, String aiRawJson, Long userId) {
        // Parse AI response to check structure
        JsonNode aiRoot;
        try {
            aiRoot = objectMapper.readTree(aiRawJson);
        } catch (Exception e) {
            log.error("[DraftAssembler] Failed to parse AI raw response as JSON", e);
            throw new IllegalArgumentException("Dữ liệu phản hồi từ AI không đúng định dạng JSON.");
        }

        // Standardize structure for draft editor compatibility
        // The draft editor expects root format: { document: { title: "...", detectedCategory: "..." }, sections: [...] }
        ObjectNode editorRoot = objectMapper.createObjectNode();
        
        ObjectNode docMeta = objectMapper.createObjectNode();
        docMeta.put("title", session.getOriginalFilename().replaceFirst("(?i)\\.pdf$", ""));
        docMeta.put("detectedCategory", "TOPIK_II"); // Default hint, lecturer can change it
        docMeta.put("description", "Đề thi tự động bóc tách từ tệp PDF: " + session.getOriginalFilename());
        editorRoot.set("document", docMeta);

        if (aiRoot.has("sections")) {
            editorRoot.set("sections", aiRoot.get("sections"));
        } else {
            editorRoot.putArray("sections");
        }

        if (aiRoot.has("warnings")) {
            editorRoot.set("warnings", aiRoot.get("warnings"));
        } else {
            editorRoot.putArray("warnings");
        }

        String draftJson = editorRoot.toString();

        PracticeDraft draft = null;
        if (session.getLinkedDraftId() != null) {
            draft = draftRepository.findById(session.getLinkedDraftId()).orElse(null);
        }

        if (draft != null) {
            draft.setDraftJson(draftJson);
            draft.setTitle(session.getOriginalFilename().replaceFirst("(?i)\\.pdf$", ""));
            draft.setDescription("Đề thi tự động bóc tách từ tệp PDF");
        } else {
            draft = new PracticeDraft(
                    session.getOriginalFilename().replaceFirst("(?i)\\.pdf$", ""),
                    "Tạo tự động từ PDF: " + session.getOriginalFilename(),
                    "TOPIK_II",
                    "GLOBAL",
                    null,
                    "DRAFT",
                    userId,
                    draftJson
            );
        }

        draft.setCreationMethod("PDF_AI");
        PracticeDraft savedDraft = draftRepository.save(draft);

        // Map draftId to import session
        sessionService.updateDraftId(session.getId(), savedDraft.getId());
        sessionService.updateStatus(session.getId(), "AI_COMPLETED");

        log.info("[DraftAssembler] Assembled and saved draft id={} for session id={}", savedDraft.getId(), session.getId());
        return savedDraft;
    }
}
