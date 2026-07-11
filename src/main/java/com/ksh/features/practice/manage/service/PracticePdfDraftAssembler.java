package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticePdfDraftAssembler {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfDraftAssembler.class);

    private final PracticeDraftRepository draftRepository;
    private final PracticePdfImportSessionService sessionService;
    private final ObjectMapper objectMapper;
    private final PracticeDraftContractService draftContractService;

    @Autowired
    public PracticePdfDraftAssembler(PracticeDraftRepository draftRepository,
                                     PracticePdfImportSessionService sessionService,
                                     ObjectMapper objectMapper,
                                     PracticeDraftContractService draftContractService) {
        this.draftRepository = draftRepository;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
        this.draftContractService = draftContractService;
    }

    PracticePdfDraftAssembler(PracticeDraftRepository draftRepository,
                              PracticePdfImportSessionService sessionService,
                              ObjectMapper objectMapper) {
        this(draftRepository, sessionService, objectMapper, null);
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
        String sessionCategory = session.getExamCategory() == null
                ? "TOPIK_II"
                : session.getExamCategory();
        String templateCode = switch (sessionCategory.toUpperCase()) {
            case "TOPIK_I", "TOPIK_II", "CUSTOM_FLEXIBLE" -> sessionCategory.toUpperCase();
            default -> AssessmentAuthoringCatalogService.defaultTemplateForCategory(sessionCategory);
        };
        docMeta.put("detectedCategory", sessionCategory);
        docMeta.put("examTemplateCode", templateCode);
        docMeta.put("description", "Đề thi tự động bóc tách từ tệp PDF: " + session.getOriginalFilename());
        docMeta.put("sourceFileName", session.getOriginalFilename());
        docMeta.put("sourcePageFrom", session.getSelectedStartPage());
        docMeta.put("sourcePageTo", session.getSelectedEndPage());
        editorRoot.set("document", docMeta);

        if (aiRoot.has("sections")) {
            JsonNode sections = aiRoot.get("sections").deepCopy();
            normalizeImportedStimuli(sections);
            editorRoot.set("sections", sections);
        } else {
            editorRoot.putArray("sections");
        }

        if (aiRoot.has("warnings")) {
            editorRoot.set("warnings", aiRoot.get("warnings"));
        } else {
            editorRoot.putArray("warnings");
        }

        PracticeDraftContractService.NormalizedDraft normalized = draftContractService == null
                ? new PracticeDraftContractService.NormalizedDraft(
                        editorRoot.toString(), sessionCategory, null, null, templateCode)
                : draftContractService.normalize(editorRoot, "PDF_AI");
        String draftJson = normalized.json();

        PracticeDraft draft = null;
        if (session.getLinkedDraftId() != null) {
            draft = draftRepository.findByIdAndOwnerId(session.getLinkedDraftId(), userId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Bản nháp liên kết không tồn tại."));
        }

        if (draft != null) {
            draft.setDraftJson(draftJson);
            draft.setTitle(session.getOriginalFilename().replaceFirst("(?i)\\.pdf$", ""));
            draft.setDescription("Đề thi tự động bóc tách từ tệp PDF");
            draft.setCategory(normalized.category());
        } else {
            draft = new PracticeDraft(
                    session.getOriginalFilename().replaceFirst("(?i)\\.pdf$", ""),
                    "Tạo tự động từ PDF: " + session.getOriginalFilename(),
                    normalized.category(),
                    "GLOBAL",
                    null,
                    "DRAFT",
                    userId,
                    draftJson
            );
        }

        draft.setCreationMethod("PDF_AI");
        draft.setDraftSchemaVersion(PracticeDraftContractService.SCHEMA_VERSION);
        draft.setAssessmentProgramCode(normalized.programCode());
        draft.setAssessmentProgramVersionId(normalized.programVersionId());
        draft.setExamTemplateCode(normalized.examTemplateCode());
        PracticeDraft savedDraft = draftRepository.save(draft);

        // Map draftId to import session
        sessionService.updateDraftId(session.getId(), savedDraft.getId());
        sessionService.updateStatus(session.getId(), "AI_COMPLETED");

        log.info("[DraftAssembler] Assembled and saved draft id={} for session id={}", savedDraft.getId(), session.getId());
        return savedDraft;
    }

    private void normalizeImportedStimuli(JsonNode sections) {
        if (!sections.isArray()) {
            return;
        }
        for (JsonNode sectionNode : sections) {
            if (!(sectionNode instanceof ObjectNode section)) continue;
            if (!section.hasNonNull("title") && section.hasNonNull("label")) {
                section.set("title", section.get("label"));
            }
            for (JsonNode groupNode : section.path("groups")) {
                if (!(groupNode instanceof ObjectNode group)) continue;
                ObjectNode stimulus = objectMapper.createObjectNode();
                stimulus.put("schemaVersion", PracticeDraftContractService.STIMULUS_SCHEMA_VERSION);
                String skill = section.path("skill").asText("");
                String passage = group.path("passage").asText("");
                String transcript = group.path("transcript").asText("");
                String audioRef = group.path("audioRef").asText("");
                String stimulusType = "NONE";
                if ("READING".equalsIgnoreCase(skill) && !passage.isBlank()) {
                    stimulusType = "READING_PASSAGE";
                } else if ("LISTENING".equalsIgnoreCase(skill)
                        && (!transcript.isBlank() || !audioRef.isBlank())) {
                    stimulusType = "LISTENING_AUDIO";
                }
                stimulus.put("type", stimulusType);
                stimulus.put("instruction", group.path("instruction").asText(""));
                stimulus.put("passageText", passage);
                stimulus.put("transcriptText", transcript);
                stimulus.put("mediaReference", audioRef);
                ObjectNode provenance = stimulus.putObject("provenance");
                provenance.put("source", "PDF_AI");
                provenance.put("approved", false);
                if (group.path("sourceRegionIds").isArray()) {
                    provenance.set("sourceRegionIds", group.path("sourceRegionIds").deepCopy());
                }
                group.set("stimulus", stimulus);
                for (JsonNode questionNode : group.path("questions")) {
                    if (!(questionNode instanceof ObjectNode question)) continue;
                    question.put("importSource", "PDF_AI");
                    if (!question.has("confidence")) question.put("confidence", 0.0);
                    if (!question.has("reviewRequired")) question.put("reviewRequired", true);
                    double confidence = question.path("confidence").asDouble(0.0);
                    if (confidence < 0.8 || confidence > 1.0
                            || question.path("answerKey").asText("").isBlank()) {
                        question.put("reviewRequired", true);
                    }
                }
            }
        }
    }
}
