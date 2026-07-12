package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.*;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
public class PracticeDraftService {

    private static final Logger log = LoggerFactory.getLogger(PracticeDraftService.class);

    private final PracticeDraftRepository draftRepository;
    private final PracticeSetRepository setRepository;
    private final PracticeTestRepository testRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final PracticeDraftContractService draftContractService;
    private final PracticeAuthorizationService authorizationService;

    @org.springframework.beans.factory.annotation.Autowired
    public PracticeDraftService(PracticeDraftRepository draftRepository,
                                PracticeSetRepository setRepository,
                                PracticeTestRepository testRepository,
                                PracticeSectionRepository sectionRepository,
                                PracticeQuestionGroupRepository groupRepository,
                                PracticeQuestionRepository questionRepository,
                                ObjectMapper objectMapper,
                                PracticeDraftContractService draftContractService,
                                PracticeAuthorizationService authorizationService) {
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
        this.testRepository = testRepository;
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.draftContractService = draftContractService;
        this.authorizationService = authorizationService;
    }

    public PracticeDraftService(PracticeDraftRepository draftRepository, ObjectMapper objectMapper) {
        this(draftRepository, null, null, null, null, null, objectMapper, null, null);
    }

    public PracticeDraftService(PracticeDraftRepository draftRepository,
                                PracticeSetRepository setRepository,
                                PracticeSectionRepository sectionRepository,
                                PracticeQuestionGroupRepository groupRepository,
                                PracticeQuestionRepository questionRepository,
                                ObjectMapper objectMapper) {
        this(draftRepository, setRepository, null, sectionRepository, groupRepository, questionRepository,
                objectMapper, null, null);
    }

    @Transactional
    public PracticeDraft getDraft(Long id, Long actorId) {
        PracticeDraft draft = authorizedDraft(id, actorId, PracticeAction.READ, null);
        normalizeDraft(draft, draft.getDraftJson(), draft.getCreationMethod());
        return draft;
    }

    @Transactional
    public PracticeDraft getOrCreateEmptyDraft(Long ownerId) {
        if (authorizationService != null) {
            authorizationService.requireGlobal(ownerId, PracticeAction.CREATE);
        }
        List<PracticeDraft> drafts = draftRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
        for (PracticeDraft d : drafts) {
            if (d.getPublishedSetId() == null && "DRAFT".equals(d.getStatus())) {
                String json = d.getDraftJson();
                if (json != null) {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        if (root.has("sections") && root.get("sections").isArray() && root.get("sections").size() == 0) {
                            log.info("[DraftService] Reusing empty manual draft id={} for owner={}", d.getId(), ownerId);
                            return d;
                        }
                    } catch (Exception e) {
                        // ignore and continue
                    }
                }
            }
        }
        // Create basic empty multi-section structure
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("detectedCategory", "UNCLASSIFIED");
        doc.put("title", "Bộ đề luyện tập tự tạo");
        doc.put("confidence", 1.0);
        root.set("document", doc);
        root.putArray("sections");
        root.putArray("warnings");

        PracticeDraft draft = new PracticeDraft(
                "Bộ đề luyện tập tự tạo",
                "Tạo thủ công bởi giáo viên",
                "UNCLASSIFIED",
                "GLOBAL",
                null,
                "DRAFT",
                ownerId,
                root.toString()
        );
        draft.setCreationMethod("MANUAL");
        normalizeDraft(draft, root.toString(), "MANUAL");

        PracticeDraft saved = draftRepository.save(draft);
        log.info("[DraftService] Created empty manual draft id={} for owner={}", saved.getId(), ownerId);
        return saved;
    }

    @Transactional
    public PracticeDraft saveDraftState(Long id, Long actorId, String draftJson, String title, String description, Integer clientVersion) {
        PracticeDraft draft = authorizedDraft(id, actorId, PracticeAction.EDIT, null);

        // Check optimistic locking clientVersion
        if (clientVersion != null && draft.getVersion() > clientVersion) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    PracticeDraft.class, id
            );
        }

        String normalizedJson = normalizeDraft(draft, draftJson, draft.getCreationMethod());
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(normalizedJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Định dạng dữ liệu JSON không hợp lệ.");
        }
        if (title != null && !title.isBlank()) {
            draft.setTitle(title.trim());
        }
        if (description != null) {
            draft.setDescription(description.trim());
        }

        // Sync category from JSON document metadata if present
        if (rootNode != null && rootNode.has("document")) {
            JsonNode docNode = rootNode.get("document");
            if (docNode.has("detectedCategory")) {
                String cat = docNode.get("detectedCategory").asText().trim();
                if (!cat.isEmpty()) {
                    draft.setCategory(cat);
                }
            }
        }

        // Maintain creationMethod fallback
        if (draft.getCreationMethod() == null) {
            draft.setCreationMethod("MANUAL");
        }

        // JPA @Version optimistic locking handling
        PracticeDraft saved = draftRepository.saveAndFlush(draft);
        log.info("[DraftService] Autosaved draft id={} version={}", saved.getId(), saved.getVersion());
        return saved;
    }

    @Transactional
    public PracticeDraft createDraftFromPublishedSet(Long setId, Long actorId) {
        Long ownerId = actorId;
        if (authorizationService != null) {
            ownerId = authorizationService.requireSet(setId, actorId, PracticeAction.EDIT, null).ownerId();
        }
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu gốc không tồn tại."));
        if (authorizationService == null && !ownerId.equals(set.getCreatedBy())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền sửa học liệu này.");
        }

        java.util.Optional<PracticeDraft> existing = draftRepository.findByPublishedSetIdAndOwnerId(setId, ownerId);
        if (existing.isPresent()) {
            PracticeDraft draft = existing.get();
            if (draft.getDraftJson() == null || draft.getDraftJson().equals("{}") || !draft.getDraftJson().contains("\"questions\"")) {
                String recaptured = captureSetSnapshot(setId);
                if (recaptured.contains("\"questions\"")) {
                    draft.setDraftJson(recaptured);
                    draft = draftRepository.save(draft);
                }
            }
            return draft;
        }

        String draftJson = captureSetSnapshot(setId);

        PracticeDraft draft = new PracticeDraft(
                set.getTitle(),
                set.getDescription(),
                set.getTopikLevel(),
                set.getScope(),
                set.getClassId(),
                "DRAFT",
                ownerId,
                draftJson
        );
        draft.setPublishedSetId(setId);
        normalizeDraft(draft, draftJson, "PUBLISHED_SET");

        PracticeDraft saved = draftRepository.save(draft);
        log.info("[DraftService] Created draft id={} from published set id={}", saved.getId(), setId);
        return saved;
    }

    private String captureSetSnapshot(Long setId) {
        try {
            java.util.Map<String, Object> root = new java.util.HashMap<>();
            PracticeSet set = setRepository.findById(setId)
                    .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));

            java.util.Map<String, Object> doc = new java.util.HashMap<>();
            doc.put("title", set.getTitle());
            doc.put("description", set.getDescription());
            doc.put("detectedCategory", set.getTopikLevel());
            doc.put("assessmentProgramCode", set.getAssessmentProgramCode());
            doc.put("assessmentProgramVersionId", set.getAssessmentProgramVersionId());
            doc.put("examTemplateCode", set.getExamTemplateCode());
            root.put("document", doc);
            root.put("schemaVersion", PracticeDraftContractService.SCHEMA_VERSION);

            java.util.List<PracticeTest> tests = testRepository == null
                    ? java.util.List.of()
                    : testRepository.findBySetIdOrderByDisplayOrderAsc(setId);
            java.util.Map<Long, String> testClientIds = new java.util.LinkedHashMap<>();
            java.util.Map<Long, Integer> testNumbers = new java.util.LinkedHashMap<>();
            java.util.List<java.util.Map<String, Object>> testsList = new java.util.ArrayList<>();
            for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
                PracticeTest test = tests.get(testIndex);
                int testNo = testIndex + 1;
                String clientId = "test-" + test.getId();
                testClientIds.put(test.getId(), clientId);
                testNumbers.put(test.getId(), testNo);
                java.util.Map<String, Object> testMap = new java.util.LinkedHashMap<>();
                testMap.put("clientId", clientId);
                testMap.put("testNo", testNo);
                testMap.put("title", test.getTitle());
                testMap.put("description", test.getDescription());
                testMap.put("estimatedMinutes", test.getEstimatedMinutes());
                testsList.add(testMap);
            }
            root.put("tests", testsList);
            if (set.getMetadataJson() != null && !set.getMetadataJson().isBlank()) {
                JsonNode metadata = objectMapper.readTree(set.getMetadataJson());
                if (metadata.path("materials").isArray()) {
                    root.put("materials", objectMapper.convertValue(metadata.path("materials"), java.util.List.class));
                }
            }

            java.util.List<java.util.Map<String, Object>> sectionsList = new java.util.ArrayList<>();
            java.util.List<PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
            for (PracticeSection sec : sections) {
                java.util.Map<String, Object> secMap = new java.util.HashMap<>();
                secMap.put("title", sec.getTitle());
                secMap.put("skill", sec.getSkill());
                int testNo = testNumbers.getOrDefault(sec.getTestId(), 1);
                secMap.put("testNo", testNo);
                secMap.put("testClientId", testClientIds.getOrDefault(sec.getTestId(), "test-1"));
                secMap.put("lessonCode", lessonCode(sec.getSkill(), testNo, sec.getSectionType()));
                secMap.put("durationMinutes", sec.getDurationMinutes());
                secMap.put("totalPoints", sec.getTotalPoints());

                java.util.List<java.util.Map<String, Object>> groupsList = new java.util.ArrayList<>();
                java.util.List<PracticeQuestionGroup> groups = groupRepository.findBySetIdOrderByDisplayOrderAsc(setId);
                for (PracticeQuestionGroup grp : groups) {
                    Long grpSectionId = grp.getSectionId();
                    if (grpSectionId == null && !sections.isEmpty()) {
                        grpSectionId = sections.get(0).getId();
                    }
                    if (sec.getId().equals(grpSectionId)) {
                        java.util.Map<String, Object> grpMap = new java.util.HashMap<>();
                        grpMap.put("label", grp.getGroupLabel());
                        grpMap.put("groupCode", grp.getGroupLabel());
                        grpMap.put("instruction", grp.getInstruction());
                        grpMap.put("stimulusType", grp.getStimulusType());
                        grpMap.put("passageText", grp.getPassageText());
                        grpMap.put("transcriptText", grp.getTranscriptText());
                        grpMap.put("imageUrl", grp.getImageUrl());
                        putJsonField(grpMap, "stimulusProvenance", grp.getStimulusProvenanceJson());
                        grpMap.put("audioUrl", grp.getAudioUrl());
                        java.util.Map<String, Object> stimulus = new java.util.LinkedHashMap<>();
                        stimulus.put("schemaVersion", PracticeDraftContractService.STIMULUS_SCHEMA_VERSION);
                        stimulus.put("type", grp.getStimulusType() == null ? "NONE" : grp.getStimulusType());
                        stimulus.put("instruction", grp.getInstruction());
                        stimulus.put("passageText", grp.getPassageText());
                        stimulus.put("transcriptText", grp.getTranscriptText());
                        stimulus.put("mediaReference", grp.getAudioUrl());
                        stimulus.put("imageReference", grp.getImageUrl());
                        if (grp.getStimulusProvenanceJson() != null && !grp.getStimulusProvenanceJson().isBlank()) {
                            stimulus.put("provenance", objectMapper.readTree(grp.getStimulusProvenanceJson()));
                        }
                        grpMap.put("stimulus", stimulus);
                        
                        java.util.List<java.util.Map<String, Object>> qsList = new java.util.ArrayList<>();
                        java.util.List<PracticeQuestion> questions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
                        for (PracticeQuestion q : questions) {
                            if (grp.getId().equals(q.getGroupId())) {
                                java.util.Map<String, Object> qMap = new java.util.HashMap<>();
                                qMap.put("questionNo", q.getQuestionNo());
                                qMap.put("questionType", q.getQuestionType());
                                qMap.put("prompt", q.getPrompt());
                                qMap.put("points", q.getPoints());
                                qMap.put("explanationVi", q.getExplanation());
                                qMap.put("answerKey", q.getAnswerKey());
                                qMap.put("canonicalQuestionType", q.getCanonicalQuestionType());
                                qMap.put("scoringPolicyCode", q.getScoringPolicyCode());
                                qMap.put("scoringProfileCode", q.getScoringProfileCode());
                                qMap.put("scoringProfileVersion", q.getScoringProfileVersion());
                                qMap.put("promptProfileCode", q.getPromptProfileCode());
                                qMap.put("promptProfileVersion", q.getPromptProfileVersion());
                                qMap.put("rubricProfileCode", q.getRubricProfileCode());
                                qMap.put("rubricProfileVersion", q.getRubricProfileVersion());
                                if (q.getQuestionContentJson() != null) {
                                    qMap.put("questionContent", objectMapper.readTree(q.getQuestionContentJson()));
                                }
                                if (q.getAnswerSpecJson() != null) {
                                    qMap.put("answerSpec", objectMapper.readTree(q.getAnswerSpecJson()));
                                }
                                if ("WRITING".equalsIgnoreCase(sec.getSkill())
                                        && PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())
                                        && q.getWritingTaskType() != null) {
                                    qMap.put("essayTaskType", q.getWritingTaskType().name());
                                }
                                
                                if (q.getOptionsJson() != null) {
                                    try {
                                        qMap.put("options", objectMapper.readValue(q.getOptionsJson(), java.util.List.class));
                                    } catch (Exception e) {
                                        qMap.put("options", new java.util.ArrayList<>());
                                    }
                                }
                                qsList.add(qMap);
                            }
                        }
                        grpMap.put("questions", qsList);
                        groupsList.add(grpMap);
                    }
                }
                secMap.put("groups", groupsList);
                sectionsList.add(secMap);
            }
            root.put("sections", sectionsList);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to capture snapshot for setId={}", setId, e);
            throw new IllegalStateException(
                    "Không thể tạo snapshot lịch sử; thao tác đã bị dừng an toàn.", e);
        }
    }

    @Transactional
    public void deleteDraft(Long id, Long actorId) {
        PracticeDraft draft;
        if (authorizationService == null) {
            draft = draftRepository.findByIdAndOwnerId(id, actorId)
                    .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
        } else {
            authorizationService.requireDraftOwnerOrOverride(
                    id, actorId, PracticeAction.EDIT, null);
            draft = draftRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
        }
        draftRepository.delete(draft);
        log.info("[DraftService] Deleted draft id={}", id);
    }

    @Transactional
    public void cleanupEmptyDrafts(Long ownerId) {
        List<PracticeDraft> drafts = draftRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
        for (PracticeDraft d : drafts) {
            if (d.getPublishedSetId() == null && "DRAFT".equals(d.getStatus())) {
                String json = d.getDraftJson();
                if (json != null) {
                    try {
                        JsonNode rootNode = objectMapper.readTree(json);
                        if (rootNode.has("sections") && rootNode.get("sections").isArray() && rootNode.get("sections").size() == 0) {
                            draftRepository.delete(d);
                            log.info("[DraftService] Cleaned up empty draft id={} for owner={}", d.getId(), ownerId);
                        }
                    } catch (Exception e) {
                        // ignore parsing error
                    }
                }
            }
        }
    }

    private static String lessonCode(String skill, int testNo, String persistedSectionType) {
        if (persistedSectionType != null
                && persistedSectionType.trim().toUpperCase(java.util.Locale.ROOT).matches("[LRWS]\\d+")) {
            return persistedSectionType.trim().toUpperCase(java.util.Locale.ROOT);
        }
        String prefix = switch (skill == null ? "" : skill.toUpperCase(java.util.Locale.ROOT)) {
            case "LISTENING" -> "L";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> "R";
        };
        return prefix + testNo;
    }

    private String normalizeDraft(PracticeDraft draft, String draftJson, String source) {
        if (draftContractService == null) {
            draft.setDraftJson(draftJson);
            return draftJson;
        }
        PracticeDraftContractService.NormalizedDraft normalized =
                draftContractService.normalize(draftJson, source);
        draft.setDraftJson(normalized.json());
        draft.setCategory(normalized.category());
        draft.setDraftSchemaVersion(PracticeDraftContractService.SCHEMA_VERSION);
        draft.setAssessmentProgramCode(normalized.programCode());
        draft.setAssessmentProgramVersionId(normalized.programVersionId());
        draft.setExamTemplateCode(normalized.examTemplateCode());
        return normalized.json();
    }

    private void putJsonField(java.util.Map<String, Object> target, String field, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            target.put(field, objectMapper.readTree(json));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể đọc stimulus provenance.", exception);
        }
    }

    private PracticeDraft authorizedDraft(Long id, Long actorId, PracticeAction action,
                                          String overrideReason) {
        if (authorizationService == null) {
            return draftRepository.findByIdAndOwnerId(id, actorId)
                    .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
        }
        authorizationService.requireDraft(id, actorId, action, overrideReason);
        return draftRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bản nháp không tồn tại."));
    }
}
