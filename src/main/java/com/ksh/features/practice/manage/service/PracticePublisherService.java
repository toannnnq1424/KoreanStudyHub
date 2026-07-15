package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeEditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PracticePublisherService {

    private static final Logger log = LoggerFactory.getLogger(PracticePublisherService.class);

    private final PracticeDraftRepository draftRepository;
    private final PracticeSetRepository setRepository;
    private final PracticeTestRepository testRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeEditLogRepository editLogRepository;
    private final PracticePublishedGraphMutationGuard mutationGuard;
    private final com.ksh.features.practice.service.PracticePublishedVersionService publishedVersionService;
    private final PracticeDraftValidator draftValidator;
    private final ObjectMapper objectMapper;
    private final QuestionTypeResolver questionTypeResolver;
    private final AssessmentContractCodec assessmentContractCodec;
    private final PracticeContentRules contentRules;
    private final PracticeDraftContractService draftContractService;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeMaterialReferenceService materialReferenceService;

    @Autowired
    public PracticePublisherService(PracticeDraftRepository draftRepository,
                                    PracticeSetRepository setRepository,
                                    PracticeTestRepository testRepository,
                                    PracticeSectionRepository sectionRepository,
                                     PracticeQuestionGroupRepository groupRepository,
                                     PracticeQuestionRepository questionRepository,
                                     PracticeEditLogRepository editLogRepository,
                                     PracticePublishedGraphMutationGuard mutationGuard,
                                     com.ksh.features.practice.service.PracticePublishedVersionService publishedVersionService,
                                     PracticeContentRules contentRules,
                                     PracticeDraftContractService draftContractService,
                                     PracticeDraftValidator draftValidator,
                                     ObjectMapper objectMapper,
                                     PracticeAuthorizationService authorizationService,
                                     PracticeMaterialReferenceService materialReferenceService) {
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
        this.testRepository = testRepository;
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.questionRepository = questionRepository;
        this.editLogRepository = editLogRepository;
        this.mutationGuard = mutationGuard;
        this.publishedVersionService = publishedVersionService;
        this.contentRules = contentRules == null ? new PracticeContentRules() : contentRules;
        this.draftContractService = draftContractService;
        this.draftValidator = draftValidator;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.materialReferenceService = materialReferenceService;
        this.questionTypeResolver = new QuestionTypeResolver();
        this.assessmentContractCodec = new AssessmentContractCodec(objectMapper, questionTypeResolver);
    }

    PracticePublisherService(PracticeDraftRepository draftRepository,
                             PracticeSetRepository setRepository,
                             PracticeTestRepository testRepository,
                             PracticeSectionRepository sectionRepository,
                             PracticeQuestionGroupRepository groupRepository,
                             PracticeQuestionRepository questionRepository,
                             PracticeEditLogRepository editLogRepository,
                             PracticePublishedGraphMutationGuard mutationGuard,
                             PracticeDraftValidator draftValidator,
                             ObjectMapper objectMapper) {
        this(draftRepository, setRepository, testRepository, sectionRepository, groupRepository, questionRepository,
                editLogRepository, mutationGuard, null, null, null, draftValidator, objectMapper,
                null, null);
    }

    @Transactional
    public Long publish(Long draftId, Long actorId) {
        return publishAuthorized(draftId, actorId, PracticeAction.PUBLISH);
    }

    @Transactional
    public Long publishRestored(Long draftId, Long actorId) {
        return publishAuthorized(draftId, actorId, PracticeAction.RESTORE);
    }

    private Long publishAuthorized(Long draftId, Long actorId,
                                   PracticeAction authorizationAction) {
        PracticeAuthorizationService.Decision authorization = null;
        PracticeDraft draft;
        if (authorizationService == null) {
            draft = draftRepository.findByIdAndOwnerId(draftId, actorId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Bản nháp không tồn tại."));
        } else {
            authorization = authorizationService.requireDraft(
                    draftId, actorId, authorizationAction);
            draft = draftRepository.findById(draftId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Bản nháp không tồn tại."));
        }
        Long ownerId = authorization == null ? actorId : authorization.ownerId();

        // Parse JSON
        JsonNode root;
        try {
            root = objectMapper.readTree(draft.getDraftJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("Dữ liệu JSON bản nháp bị lỗi cú pháp.");
        }
        if (draftContractService != null && root instanceof com.fasterxml.jackson.databind.node.ObjectNode objectRoot) {
            PracticeDraftContractService.NormalizedDraft normalized =
                    draftContractService.normalize(objectRoot, draft.getCreationMethod());
            draft.setDraftJson(normalized.json());
            draft.setDraftSchemaVersion(PracticeDraftContractService.SCHEMA_VERSION);
            try {
                root = objectMapper.readTree(normalized.json());
            } catch (Exception exception) {
                throw new IllegalStateException("Không thể chuẩn hóa bản nháp để xuất bản.", exception);
            }
        }
        // Validate
        PracticeDraftValidator.ValidationResult valRes = draftValidator.validate(draft.getDraftJson());
        if (valRes.hasBlocking()) {
            throw new IllegalStateException("Không thể xuất bản bản nháp do chứa lỗi nghiêm trọng (BLOCKING). Vui lòng kiểm tra lại cấu trúc đề.");
        }

        // Create or update PracticeSet
        JsonNode sectionsNode = root.path("sections");
        
        // Collect unique skills from sections
        java.util.Set<String> uniqueSkills = new java.util.HashSet<>();
        if (sectionsNode.isArray()) {
            for (JsonNode sNode : sectionsNode) {
                String sSkill = sNode.path("skill").asText("").trim();
                if (!sSkill.isEmpty()) {
                    uniqueSkills.add(sSkill);
                }
            }
        }

        String targetSkill;
        if (uniqueSkills.isEmpty()) {
            targetSkill = "UNSPECIFIED";
        } else if (uniqueSkills.size() == 1) {
            targetSkill = uniqueSkills.iterator().next();
        } else {
            targetSkill = "MIXED";
        }
        String metaJson = "{}";
        try {
            java.util.Map<String, Object> metaMap = new java.util.HashMap<>();
            metaMap.put("skills", uniqueSkills);
            if (root.path("materials").isArray()) {
                metaMap.put("materials", objectMapper.convertValue(root.path("materials"), Object.class));
            }
            metaJson = objectMapper.writeValueAsString(metaMap);
        } catch (Exception e) {
            log.warn("[Publisher] Failed serialize metadataJson: {}", e.getMessage());
        }

        PracticeSet set;
        String beforeSnapshot = null;
        if (draft.getPublishedSetId() != null) {
            set = mutationGuard.lockAndAssertRepublishAllowed(draft.getPublishedSetId());
            if (authorizationService == null && !ownerId.equals(set.getCreatedBy())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Bạn không có quyền xuất bản học liệu này.");
            }
            
            // Capture before snapshot
            beforeSnapshot = captureSetSnapshot(set.getId());

            // Clear old children (cascade is handled by deleteBySetId)
            questionRepository.deleteBySetId(set.getId());
            groupRepository.deleteBySetId(set.getId());
            sectionRepository.deleteBySetId(set.getId());
            testRepository.deleteBySetId(set.getId());
            
            questionRepository.flush();
            groupRepository.flush();
            sectionRepository.flush();
            testRepository.flush();

            // Update set metadata
            set.setTitle(draft.getTitle());
            set.setDescription(draft.getDescription());
            set.setSkill(targetSkill);
            set.setScope(draft.getScope());
            set.setClassId(draft.getClassId());
            set.setMetadataJson(metaJson);
            set.setCreationMethod(draft.getCreationMethod());
            set.setStatus(PracticeSet.STATUS_PUBLISHED);
        } else {
            set = new PracticeSet(
                    draft.getTitle(),
                    draft.getDescription(),
                    targetSkill,
                    draft.getScope(),
                    draft.getClassId(),
                    null, // pdf path
                    metaJson, // metadata
                    PracticeSet.STATUS_PUBLISHED,
                    ownerId
            );
            set.setCreationMethod(draft.getCreationMethod());
        }

        PracticeSet savedSet = setRepository.save(set);
        log.info("[Publisher] Saved PracticeSet id={} title={}", savedSet.getId(), savedSet.getTitle());

        Map<String, PracticeTest> persistedTests = persistTests(root, savedSet, draft);
        Map<Long, Integer> sectionOrderByTest = new LinkedHashMap<>();

        // Save sections, groups and questions
        if (sectionsNode.isArray()) {
            for (int sIdx = 0; sIdx < sectionsNode.size(); sIdx++) {
                JsonNode sNode = sectionsNode.get(sIdx);
                PracticeTest targetTest = resolveTestForSection(sNode, persistedTests);
                int sectionDisplayOrder = sectionOrderByTest.getOrDefault(targetTest.getId(), 0);
                sectionOrderByTest.put(targetTest.getId(), sectionDisplayOrder + 1);
                PracticeSection section = new PracticeSection(
                        savedSet.getId(),
                        sNode.path("title").asText("Phần " + (sIdx + 1)),
                        sNode.path("skill").asText("READING"),
                        sNode.path("lessonCode").asText(sNode.path("sectionType").asText("DEFAULT")),
                        sNode.path("instructions").asText(""),
                        sNode.path("durationMinutes").asInt(40),
                        sectionTotalPoints(sNode),
                        sectionDisplayOrder
                );
                section.setTestId(targetTest.getId());
                section.setDeliveryJson(sectionDeliveryJson(sNode));
                PracticeSection savedSection = sectionRepository.save(section);

                boolean writingSection = "WRITING".equalsIgnoreCase(
                        sNode.path("skill").asText(""));
                int sectionLocalQNo = writingSection ? 51 : 1;

                JsonNode groupsNode = sNode.path("groups");
                if (groupsNode.isArray()) {
                    for (int gIdx = 0; gIdx < groupsNode.size(); gIdx++) {
                        JsonNode gNode = groupsNode.get(gIdx);
                        
                        String exampleBoxJson = null;
                        if (gNode.hasNonNull("exampleBox")) {
                            try {
                                exampleBoxJson = objectMapper.writeValueAsString(gNode.path("exampleBox"));
                            } catch (Exception e) {
                                log.warn("[Publisher] Failed serialize exampleBox: {}", e.getMessage());
                            }
                        }

                        String instruction = gNode.path("instruction").asText("");

                        // Compute questionFrom/questionTo based on the actual section-local counter
                        int groupQuestionCount = 0;
                        JsonNode questionsNode = gNode.path("questions");
                        if (questionsNode.isArray()) {
                            groupQuestionCount = questionsNode.size();
                        }
                        int groupQuestionFrom = sectionLocalQNo;
                        int groupQuestionTo = groupQuestionCount > 0 ? sectionLocalQNo + groupQuestionCount - 1 : sectionLocalQNo;

                        PracticeQuestionGroup group = new PracticeQuestionGroup(
                                savedSet.getId(),
                                gNode.path("groupCode").asText(gNode.path("label").asText("Câu")),
                                groupQuestionFrom,
                                groupQuestionTo,
                                instruction,
                                gNode.path("audioUrl").asText(null),
                                exampleBoxJson,
                                gIdx
                        );
                        group.setSectionId(savedSection.getId());
                        applyStimulus(group, gNode, sNode.path("skill").asText("READING"));
                        PracticeQuestionGroup savedGroup = groupRepository.save(group);

                        if (questionsNode.isArray()) {
                            for (int qIdx = 0; qIdx < questionsNode.size(); qIdx++) {
                                JsonNode qNode = questionsNode.get(qIdx);
                                
                                List<String> optList = new ArrayList<>();
                                JsonNode optsNode = qNode.path("options");
                                if (optsNode.isArray()) {
                                    for (JsonNode opt : optsNode) {
                                        if (opt.isObject()) {
                                            optList.add(opt.path("text").asText(""));
                                        } else {
                                            optList.add(opt.asText(""));
                                        }
                                    }
                                }

                                String optJsonString = null;
                                if (!optList.isEmpty()) {
                                    try {
                                        optJsonString = objectMapper.writeValueAsString(optList);
                                    } catch (Exception e) {
                                        log.warn("[Publisher] Failed options parse: {}", e.getMessage());
                                    }
                                }

                                String ansVal = "";
                                JsonNode ansNode = qNode.path("answer");
                                if (ansNode.isObject()) {
                                    ansVal = ansNode.path("value").asText("");
                                } else {
                                    ansVal = ansNode.asText("");
                                }

                                String rawType = qNode.path("questionType").asText("SINGLE_CHOICE");
                                CanonicalQuestionType canonicalType = questionTypeResolver.resolve(rawType);
                                String dbType = canonicalType.name();

                                String qPrompt = qNode.path("prompt").asText("");

                                WritingTaskType writingTaskType = resolveWritingTaskTypeForPublish(
                                        sNode.path("skill").asText("READING"),
                                        dbType,
                                        qNode
                                );
                                QuestionContent questionContent = resolveQuestionContent(
                                        qNode, canonicalType, optJsonString);
                                AnswerSpec answerSpec = resolveAnswerSpec(
                                        qNode, rawType, ansVal, questionContent);
                                AssessmentSkill questionSkill = resolveSkill(
                                        sNode.path("skill").asText("READING"));
                                contentRules.requireAllowed(questionSkill, canonicalType);

                                int persistedQuestionNo = writingSection
                                        ? contentRules.writingQuestionNumber(
                                                resolveWritingTaskTypeForPublish(
                                                        sNode.path("skill").asText("WRITING"),
                                                        dbType, qNode))
                                        : sectionLocalQNo;
                                PracticeQuestion question = new PracticeQuestion(
                                        savedSet.getId(),
                                        persistedQuestionNo,
                                        dbType,
                                        qPrompt,
                                        optJsonString,
                                        ansVal,
                                        qNode.path("explanationVi").asText(""),
                                        BigDecimal.valueOf(qNode.path("points").asDouble(1.0)),
                                        qIdx
                                );
                                question.setWritingTaskType(writingTaskType);
                                question.setQuestionContentJson(assessmentContractCodec.writeQuestionContent(
                                        questionContent, canonicalType));
                                question.setAnswerSpecJson(assessmentContractCodec.writeAnswerSpec(
                                        answerSpec, questionContent));
                                question.setGroupId(savedGroup.getId());
                                questionRepository.save(question);
                                sectionLocalQNo = persistedQuestionNo + 1;
                            }
                        }
                    }
                }
            }
        }

        // Set draft status as PUBLISHED so we know it is done
        draft.setPublishedSetId(savedSet.getId());
        draft.setStatus("PUBLISHED");
        draftRepository.save(draft);

        // Capture after snapshot
        String afterSnapshot = captureSetSnapshot(savedSet.getId());
        String editType = (beforeSnapshot == null) ? "METADATA" : determineEditType(beforeSnapshot, afterSnapshot);

        // Save PracticeEditLog
        com.ksh.entities.PracticeEditLog logEntry = new com.ksh.entities.PracticeEditLog(
                savedSet.getId(),
                actorId,
                "Cập nhật cấu trúc học liệu qua editor",
                "{}",
                beforeSnapshot,
                afterSnapshot,
                editType
        );
        editLogRepository.save(logEntry);

        com.ksh.entities.PracticePublishedVersion publishedVersion = null;
        if (publishedVersionService != null) {
            publishedVersion = publishedVersionService.createPublishedVersion(
                    savedSet.getId(), actorId);
        }

        if (materialReferenceService != null && publishedVersion != null) {
            materialReferenceService.promoteDraftReferences(
                    draftId, savedSet.getId(), publishedVersion.getId());
        }

        log.info("[Publisher] Complete publish draftId={} to setId={}", draftId, savedSet.getId());
        return savedSet.getId();
    }

    private Map<String, PracticeTest> persistTests(JsonNode root, PracticeSet set, PracticeDraft draft) {
        Map<String, PracticeTest> result = new LinkedHashMap<>();
        JsonNode tests = root.path("tests");
        if (tests.isArray()) {
            for (int index = 0; index < tests.size(); index++) {
                JsonNode testNode = tests.get(index);
                int testNo = testNode.path("testNo").asInt(index + 1);
                Integer estimatedMinutes = testNode.path("estimatedMinutes").canConvertToInt()
                        && testNode.path("estimatedMinutes").asInt() > 0
                        ? testNode.path("estimatedMinutes").asInt()
                        : null;
                PracticeTest saved = testRepository.save(new PracticeTest(
                        set.getId(),
                        testNode.path("title").asText("Test " + testNo),
                        testNode.path("description").asText(""),
                        index,
                        estimatedMinutes
                ));
                String clientId = testNode.path("clientId").asText("test-" + testNo);
                result.put(clientId, saved);
                result.put("no:" + testNo, saved);
                result.putIfAbsent("default", saved);
            }
        }
        if (result.isEmpty()) {
            PracticeTest saved = testRepository.save(new PracticeTest(
                    set.getId(), draft.getTitle(), draft.getDescription(), 0, null));
            result.put("no:1", saved);
            result.put("default", saved);
        }
        return result;
    }

    private static PracticeTest resolveTestForSection(JsonNode section,
                                                      Map<String, PracticeTest> persistedTests) {
        String clientId = section.path("testClientId").asText("");
        PracticeTest test = clientId.isBlank() ? null : persistedTests.get(clientId);
        if (test == null) test = persistedTests.get("no:" + section.path("testNo").asInt(1));
        if (test == null) test = persistedTests.get("default");
        if (test == null) throw new IllegalStateException("Section không tham chiếu Practice Test hợp lệ.");
        return test;
    }

    private String captureSetSnapshot(Long setId) {
        try {
            java.util.Map<String, Object> root = new java.util.HashMap<>();
            PracticeSet set = setRepository.findById(setId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Học liệu không tồn tại."));

            java.util.Map<String, Object> doc = new java.util.HashMap<>();
            doc.put("title", set.getTitle());
            doc.put("description", set.getDescription());
            root.put("document", doc);
            root.put("schemaVersion", PracticeDraftContractService.SCHEMA_VERSION);

            List<PracticeTest> tests = testRepository.findBySetIdOrderByDisplayOrderAsc(setId);
            Map<Long, String> testClientIds = new LinkedHashMap<>();
            Map<Long, Integer> testNumbers = new LinkedHashMap<>();
            List<java.util.Map<String, Object>> testList = new ArrayList<>();
            for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
                PracticeTest test = tests.get(testIndex);
                int testNo = testIndex + 1;
                String clientId = "test-" + test.getId();
                testClientIds.put(test.getId(), clientId);
                testNumbers.put(test.getId(), testNo);
                java.util.Map<String, Object> testMap = new LinkedHashMap<>();
                testMap.put("clientId", clientId);
                testMap.put("testNo", testNo);
                testMap.put("title", test.getTitle());
                testMap.put("description", test.getDescription());
                testMap.put("estimatedMinutes", test.getEstimatedMinutes());
                testList.add(testMap);
            }
            root.put("tests", testList);
            if (set.getMetadataJson() != null && !set.getMetadataJson().isBlank()) {
                JsonNode metadata = objectMapper.readTree(set.getMetadataJson());
                if (metadata.path("materials").isArray()) {
                    root.put("materials", objectMapper.convertValue(metadata.path("materials"), List.class));
                }
            }

            List<java.util.Map<String, Object>> sectionsList = new java.util.ArrayList<>();
            List<PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
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
                putJsonField(secMap, "sectionDelivery", sec.getDeliveryJson());

                List<java.util.Map<String, Object>> groupsList = new java.util.ArrayList<>();
                List<PracticeQuestionGroup> groups = groupRepository.findBySetIdOrderByDisplayOrderAsc(setId);
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
                        grpMap.put("passageText", grp.getPassageText());
                        grpMap.put("transcriptText", grp.getTranscriptText());
                        grpMap.put("imageUrl", grp.getImageUrl());
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
                        
                        List<java.util.Map<String, Object>> qsList = new java.util.ArrayList<>();
                        List<PracticeQuestion> questions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
                        for (PracticeQuestion q : questions) {
                            if (grp.getId().equals(q.getGroupId())) {
                                java.util.Map<String, Object> qMap = new java.util.HashMap<>();
                                qMap.put("questionNo", q.getQuestionNo());
                                qMap.put("questionType", q.getQuestionType());
                                qMap.put("prompt", q.getPrompt());
                                qMap.put("points", q.getPoints());
                                qMap.put("explanationVi", q.getExplanation());
                                qMap.put("answerKey", q.getAnswerKey());
                                putJsonField(qMap, "questionContent", q.getQuestionContentJson());
                                putJsonField(qMap, "answerSpec", q.getAnswerSpecJson());
                                if ("WRITING".equalsIgnoreCase(sec.getSkill())
                                        && PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())
                                        && q.getWritingTaskType() != null) {
                                    qMap.put("essayTaskType", q.getWritingTaskType().name());
                                }
                                
                                if (q.getOptionsJson() != null) {
                                    try {
                                        qMap.put("options", objectMapper.readValue(q.getOptionsJson(), List.class));
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
                    "Không thể tạo snapshot lịch sử; xuất bản đã bị dừng an toàn.", e);
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

    private String determineEditType(String beforeJson, String afterJson) {
        if (beforeJson == null || beforeJson.equals("{}")) return "METADATA";
        try {
            JsonNode before = objectMapper.readTree(beforeJson);
            JsonNode after = objectMapper.readTree(afterJson);

            java.util.Set<String> types = new java.util.HashSet<>();

            String beforeTitle = before.path("document").path("title").asText("");
            String afterTitle = after.path("document").path("title").asText("");
            String beforeDesc = before.path("document").path("description").asText("");
            String afterDesc = after.path("document").path("description").asText("");
            if (!beforeTitle.equals(afterTitle) || !beforeDesc.equals(afterDesc)) {
                types.add("METADATA");
            }

            JsonNode beforeSecs = before.path("sections");
            JsonNode afterSecs = after.path("sections");
            
            if (beforeSecs.size() != afterSecs.size()) {
                types.add("QUESTIONS");
            } else {
                for (int s = 0; s < beforeSecs.size(); s++) {
                    JsonNode bGroups = beforeSecs.get(s).path("groups");
                    JsonNode aGroups = afterSecs.get(s).path("groups");
                    if (bGroups.size() != aGroups.size()) {
                        types.add("QUESTIONS");
                        break;
                    }
                    for (int g = 0; g < bGroups.size(); g++) {
                        JsonNode bQs = bGroups.get(g).path("questions");
                        JsonNode aQs = aGroups.get(g).path("questions");
                        if (bQs.size() != aQs.size()) {
                            types.add("QUESTIONS");
                            break;
                        }
                        for (int q = 0; q < bQs.size(); q++) {
                            JsonNode bQ = bQs.get(q);
                            JsonNode aQ = aQs.get(q);

                            if (!bQ.path("prompt").asText("").equals(aQ.path("prompt").asText(""))) {
                                types.add("QUESTIONS");
                            }
                            if (!bQ.path("answerKey").asText("").equals(aQ.path("answerKey").asText(""))) {
                                types.add("ANSWERS");
                            }
                            if (!bQ.path("essaySample").asText("").equals(aQ.path("essaySample").asText("")) ||
                                !bQ.path("speakingSample").asText("").equals(aQ.path("speakingSample").asText(""))) {
                                types.add("SAMPLE_ANSWERS");
                            }
                        }
                    }
                }
            }

            if (types.isEmpty()) return "METADATA";
            return String.join(",", types);
        } catch (Exception e) {
            return "QUESTIONS";
        }
    }

    private String mapUiTypeToDbType(String uiType) {
        return questionTypeResolver.canonicalCode(uiType == null ? "SINGLE_CHOICE" : uiType);
    }

    private QuestionContent resolveQuestionContent(JsonNode question,
                                                   CanonicalQuestionType type,
                                                   String legacyOptionsJson) {
        JsonNode typedContent = question.get("questionContent");
        QuestionContent content;
        if (typedContent != null && typedContent.isObject()) {
            content = assessmentContractCodec.readQuestionContent(typedContent.toString(), type);
        } else {
            content = assessmentContractCodec.adaptLegacyContent(legacyOptionsJson, type.name());
        }
        return withQuestionMediaFallbacks(content, question);
    }

    private QuestionContent withQuestionMediaFallbacks(QuestionContent content, JsonNode question) {
        String imageReference = firstNonBlank(
                content.imageReference(),
                question.path("imageUrl").asText(""));
        String audioReference = firstNonBlank(
                content.audioReference(),
                question.path("audioUrl").asText(""));
        if (Objects.equals(imageReference, content.imageReference())
                && Objects.equals(audioReference, content.audioReference())) {
            return content;
        }
        return new QuestionContent(
                content.schemaVersion(),
                content.options(),
                content.blanks(),
                blankToNull(imageReference),
                blankToNull(audioReference),
                content.speakingDelivery());
    }

    private AnswerSpec resolveAnswerSpec(JsonNode question,
                                         String rawType,
                                         String legacyAnswer,
                                         QuestionContent content) {
        JsonNode typedSpec = question.get("answerSpec");
        if (typedSpec != null && typedSpec.isObject()) {
            return assessmentContractCodec.readAnswerSpec(typedSpec.toString(), content);
        }
        return assessmentContractCodec.adaptLegacyAnswerSpec(rawType, legacyAnswer, content);
    }

    private AssessmentSkill resolveSkill(String rawSkill) {
        try {
            return AssessmentSkill.valueOf(rawSkill.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Kỹ năng không được hỗ trợ: " + rawSkill);
        }
    }

    private void putJsonField(java.util.Map<String, Object> target, String field, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            target.put(field, objectMapper.readTree(json));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể snapshot assessment field: " + field, exception);
        }
    }

    private BigDecimal sectionTotalPoints(JsonNode section) {
        JsonNode explicit = section.get("totalPoints");
        if (explicit != null && explicit.isNumber() && explicit.decimalValue().signum() > 0) {
            return explicit.decimalValue();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode group : section.path("groups")) {
            for (JsonNode question : group.path("questions")) {
                BigDecimal points = question.path("points").isNumber()
                        ? question.path("points").decimalValue()
                        : BigDecimal.ONE;
                if (points.signum() > 0) {
                    total = total.add(points);
                }
            }
        }
        return total.signum() > 0 ? total : BigDecimal.ONE;
    }

    private void applyStimulus(PracticeQuestionGroup group, JsonNode groupNode, String skill) {
        JsonNode stimulus = groupNode.path("stimulus");
        String type = stimulus.path("type").asText("");
        String passage = firstNonBlank(
                stimulus.path("passageText").asText(""),
                groupNode.path("passageText").asText(""));
        String transcript = firstNonBlank(
                stimulus.path("transcriptText").asText(""),
                groupNode.path("transcriptText").asText(""));
        if (transcript.isBlank() && "LISTENING".equalsIgnoreCase(skill)) {
            transcript = groupNode.path("passageText").asText("");
        }
        if (type.isBlank()) {
            type = "READING".equalsIgnoreCase(skill) && !passage.isBlank()
                    ? "READING_PASSAGE"
                    : ("LISTENING".equalsIgnoreCase(skill) ? "LISTENING_AUDIO" : "NONE");
        }
        group.setStimulusType(type);
        group.setPassageText(blankToNull(passage));
        group.setTranscriptText(blankToNull(transcript));
        group.setAudioUrl(blankToNull(firstNonBlank(
                stimulus.path("mediaReference").asText(""),
                groupNode.path("audioUrl").asText(""))));
        group.setImageUrl(blankToNull(firstNonBlank(
                stimulus.path("imageReference").asText(""),
                groupNode.path("imageUrl").asText(""))));
        JsonNode provenance = stimulus.path("provenance");
        if (provenance.isObject()) {
            try {
                group.setStimulusProvenanceJson(objectMapper.writeValueAsString(provenance));
            } catch (Exception exception) {
                throw new IllegalArgumentException("Stimulus provenance không hợp lệ.", exception);
            }
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String sectionDeliveryJson(JsonNode section) {
        JsonNode delivery = section.path("sectionDelivery");
        if (!delivery.isObject()) return null;
        try {
            return objectMapper.writeValueAsString(delivery);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cấu hình phát nội dung của section không hợp lệ.", exception);
        }
    }

    private WritingTaskType resolveWritingTaskTypeForPublish(String skill, String questionType, JsonNode question) {
        if (!"WRITING".equalsIgnoreCase(skill) || !PracticeQuestion.TYPE_ESSAY.equals(questionType)) {
            return null;
        }
        JsonNode taskNode = question.get("essayTaskType");
        if (taskNode == null || taskNode.isNull()) {
            return null;
        }
        if (!taskNode.isTextual()) {
            throw new IllegalArgumentException("Loại bài Writing không hợp lệ.");
        }
        String value = taskNode.asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn loại bài Writing cho câu tự luận.");
        }
        try {
            return WritingTaskType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Loại bài Writing không hợp lệ.");
        }
    }
}
