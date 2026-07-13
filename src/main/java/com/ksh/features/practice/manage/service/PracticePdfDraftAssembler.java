package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PracticePdfDraftAssembler {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfDraftAssembler.class);

    private final PracticeDraftRepository draftRepository;
    private final PracticePdfImportSessionService sessionService;
    private final ObjectMapper objectMapper;
    private final PracticeDraftContractService draftContractService;
    private final PracticeAuthorizationService authorizationService;

    @Autowired
    public PracticePdfDraftAssembler(PracticeDraftRepository draftRepository,
                                     PracticePdfImportSessionService sessionService,
                                     ObjectMapper objectMapper,
                                     PracticeDraftContractService draftContractService,
                                     PracticeAuthorizationService authorizationService) {
        this.draftRepository = draftRepository;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
        this.draftContractService = draftContractService;
        this.authorizationService = authorizationService;
    }

    PracticePdfDraftAssembler(PracticeDraftRepository draftRepository,
                              PracticePdfImportSessionService sessionService,
                              ObjectMapper objectMapper) {
        this(draftRepository, sessionService, objectMapper, null, null);
    }

    PracticePdfDraftAssembler(PracticeDraftRepository draftRepository,
                              PracticePdfImportSessionService sessionService,
                              ObjectMapper objectMapper,
                              PracticeDraftContractService draftContractService) {
        this(draftRepository, sessionService, objectMapper, draftContractService, null);
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

        // Standardize the AI result for the shared KSH draft editor contract.
        ObjectNode editorRoot = objectMapper.createObjectNode();
        
        ObjectNode docMeta = objectMapper.createObjectNode();
        docMeta.put("title", session.getOriginalFilename().replaceFirst("(?i)\\.pdf$", ""));
        docMeta.put("description", "Đề thi tự động bóc tách từ tệp PDF: " + session.getOriginalFilename());
        docMeta.put("sourceFileName", session.getOriginalFilename());
        docMeta.put("sourcePageFrom", session.getSelectedStartPage());
        docMeta.put("sourcePageTo", session.getSelectedEndPage());
        if (session.getTargetTestNo() != null) docMeta.put("targetTestNo", session.getTargetTestNo());
        putIfPresent(docMeta, "targetSkill", session.getTargetSkill());
        putIfPresent(docMeta, "targetLessonCode", session.getTargetLessonCode());
        editorRoot.set("document", docMeta);

        if (aiRoot.has("sections")) {
            JsonNode sections = aiRoot.get("sections").deepCopy();
            normalizeImportedStimuli(sections);
            editorRoot.set("sections", targetImportedSections(sections, session));
        } else {
            editorRoot.putArray("sections");
        }

        if (aiRoot.has("warnings")) {
            editorRoot.set("warnings", aiRoot.get("warnings"));
        } else {
            editorRoot.putArray("warnings");
        }

        PracticeDraft draft = null;
        if (session.getLinkedDraftId() != null) {
            if (authorizationService != null) {
                authorizationService.requireDraft(
                        session.getLinkedDraftId(), userId, PracticeAction.EDIT);
                draft = draftRepository.findById(session.getLinkedDraftId())
                        .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                                "Bản nháp liên kết không tồn tại."));
            } else {
                draft = draftRepository.findByIdAndOwnerId(session.getLinkedDraftId(), userId)
                        .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                                "Bản nháp liên kết không tồn tại."));
            }
        } else if (authorizationService != null) {
            authorizationService.requireGlobal(userId, PracticeAction.CREATE);
        }

        ObjectNode rootToNormalize = draft == null
                ? editorRoot
                : mergeIntoLinkedDraft(draft, editorRoot, session);
        PracticeDraftContractService.NormalizedDraft normalized = draftContractService == null
                ? new PracticeDraftContractService.NormalizedDraft(rootToNormalize.toString())
                : draftContractService.normalize(rootToNormalize, "PDF_AI");
        String draftJson = normalized.json();

        if (draft != null) {
            draft.setDraftJson(draftJson);
        } else {
            draft = new PracticeDraft(
                    session.getOriginalFilename().replaceFirst("(?i)\\.pdf$", ""),
                    "Tạo tự động từ PDF: " + session.getOriginalFilename(),
                    "GLOBAL",
                    null,
                    "DRAFT",
                    userId,
                    draftJson
            );
            draft.setCreationMethod("PDF_AI");
        }

        draft.setDraftSchemaVersion(PracticeDraftContractService.SCHEMA_VERSION);
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
                    int sourceQuestionNo = question.path("questionNo").asInt(0);
                    if (sourceQuestionNo > 0 && !question.has("sourceQuestionNo")) {
                        question.put("sourceQuestionNo", sourceQuestionNo);
                    }
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

    private ArrayNode targetImportedSections(JsonNode sections, PracticePdfImportSession session) {
        ArrayNode imported = sections instanceof ArrayNode array
                ? array
                : objectMapper.createArrayNode();
        if (session.getTargetTestNo() == null || session.getTargetSkill() == null
                || session.getTargetLessonCode() == null) {
            return imported;
        }

        ObjectNode target = objectMapper.createObjectNode();
        target.put("clientId", "sec-pdf-" + UUID.randomUUID());
        target.put("testNo", session.getTargetTestNo());
        target.put("lessonCode", session.getTargetLessonCode());
        target.put("skill", session.getTargetSkill());
        target.put("title", skillLabel(session.getTargetSkill()));
        ArrayNode groups = target.putArray("groups");
        ArrayNode sourceRegionIds = target.putArray("sourceRegionIds");
        for (JsonNode section : imported) {
            section.path("sourceRegionIds").forEach(sourceRegionIds::add);
            section.path("groups").forEach(group -> groups.add(group.deepCopy()));
        }
        ArrayNode result = objectMapper.createArrayNode();
        result.add(target);
        return result;
    }

    private ObjectNode mergeIntoLinkedDraft(PracticeDraft draft, ObjectNode importedRoot,
                                            PracticePdfImportSession session) {
        try {
            JsonNode parsed = objectMapper.readTree(draft.getDraftJson());
            if (!(parsed instanceof ObjectNode existingRoot)) {
                throw new IllegalArgumentException("Bản nháp liên kết không có cấu trúc hợp lệ.");
            }
            ObjectNode target = findTargetSection(existingRoot, session);
            ArrayNode targetGroups = target.path("groups") instanceof ArrayNode array
                    ? array
                    : target.putArray("groups");
            for (JsonNode importedSection : importedRoot.path("sections")) {
                for (JsonNode importedGroup : importedSection.path("groups")) {
                    if (!(importedGroup.deepCopy() instanceof ObjectNode group)) continue;
                    renewImportIds(group);
                    int groupNo = targetGroups.size() + 1;
                    group.put("groupCode", session.getTargetLessonCode() + "." + groupNo);
                    if (!group.hasNonNull("label") || group.path("label").asText().isBlank()) {
                        group.put("label", session.getTargetLessonCode() + "." + groupNo);
                    }
                    targetGroups.add(group);
                }
            }
            ArrayNode warnings = existingRoot.path("warnings") instanceof ArrayNode array
                    ? array
                    : existingRoot.putArray("warnings");
            importedRoot.path("warnings").forEach(warning -> warnings.add(warning.deepCopy()));
            ObjectNode document = existingRoot.path("document") instanceof ObjectNode object
                    ? object
                    : existingRoot.putObject("document");
            document.put("lastPdfImportFileName", session.getOriginalFilename());
            document.put("lastPdfImportPageFrom", session.getSelectedStartPage());
            document.put("lastPdfImportPageTo", session.getSelectedEndPage());
            document.put("lastPdfImportTarget", session.getTargetLessonCode());
            return existingRoot;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ghép dữ liệu PDF vào bản nháp hiện tại.", exception);
        }
    }

    private ObjectNode findTargetSection(ObjectNode root, PracticePdfImportSession session) {
        for (JsonNode node : root.path("sections")) {
            if (!(node instanceof ObjectNode section)) continue;
            boolean sameTest = session.getTargetTestNo() != null
                    && session.getTargetTestNo() == section.path("testNo").asInt();
            boolean sameLesson = session.getTargetLessonCode() != null
                    && session.getTargetLessonCode().equalsIgnoreCase(section.path("lessonCode").asText());
            if (sameTest && sameLesson) return section;
        }
        throw new IllegalArgumentException("Không tìm thấy phần thi đích trong bản nháp liên kết.");
    }

    private void renewImportIds(ObjectNode group) {
        group.put("clientId", "grp-pdf-" + UUID.randomUUID());
        for (JsonNode node : group.path("questions")) {
            if (node instanceof ObjectNode question) {
                question.put("clientId", "q-pdf-" + UUID.randomUUID());
            }
        }
    }

    private static String skillLabel(String skill) {
        return switch (skill == null ? "" : skill.toUpperCase()) {
            case "LISTENING" -> "Phần Nghe";
            case "WRITING" -> "Phần Viết";
            case "SPEAKING" -> "Phần Nói";
            default -> "Phần Đọc";
        };
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) node.put(field, value);
    }
}
