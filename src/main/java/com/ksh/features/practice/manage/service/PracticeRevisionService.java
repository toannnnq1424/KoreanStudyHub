package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.*;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PracticeRevisionService {

    private static final Logger log = LoggerFactory.getLogger(PracticeRevisionService.class);

    private final PracticeSetRepository setRepository;
    private final PracticeTestRepository testRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeEditLogRepository editLogRepository;
    private final PracticePublishedGraphMutationGuard mutationGuard;
    private final ObjectMapper objectMapper;
    private final QuestionTypeResolver questionTypeResolver = new QuestionTypeResolver();

    public PracticeRevisionService(PracticeSetRepository setRepository,
                                   PracticeTestRepository testRepository,
                                   PracticeSectionRepository sectionRepository,
                                    PracticeQuestionGroupRepository groupRepository,
                                    PracticeQuestionRepository questionRepository,
                                    PracticeEditLogRepository editLogRepository,
                                    PracticePublishedGraphMutationGuard mutationGuard,
                                    ObjectMapper objectMapper) {
        this.setRepository = setRepository;
        this.testRepository = testRepository;
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.questionRepository = questionRepository;
        this.editLogRepository = editLogRepository;
        this.mutationGuard = mutationGuard;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void restoreRevision(Long logId, Long editorId) {
        PracticeEditLog logEntry = editLogRepository.findById(logId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Lịch sử sửa đổi không tồn tại."));

        String snapshotJson = logEntry.getBeforeSnapshotJson();
        if (snapshotJson == null || snapshotJson.equals("{}")) {
            throw new IllegalArgumentException("Không thể khôi phục về phiên bản này (không có dữ liệu snapshot trước thay đổi).");
        }

        Long setId = logEntry.getSetId();
        PracticeSet set = mutationGuard.lockAndAssertRestoreAllowed(setId);
        if (!editorId.equals(set.getCreatedBy())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền khôi phục học liệu này.");
        }
        // Parse JSON snapshot
        JsonNode root;
        try {
            root = objectMapper.readTree(snapshotJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Dữ liệu JSON snapshot bị lỗi cú pháp.");
        }
        validateWritingTaskMetadata(root);

        // Delete current entities
        questionRepository.deleteBySetId(setId);
        groupRepository.deleteBySetId(setId);
        sectionRepository.deleteBySetId(setId);
        testRepository.deleteBySetId(setId);
        
        questionRepository.flush();
        groupRepository.flush();
        sectionRepository.flush();
        testRepository.flush();

        // Restore set metadata
        JsonNode docNode = root.path("document");
        if (!docNode.isMissingNode()) {
            set.setTitle(docNode.path("title").asText(set.getTitle()));
            set.setDescription(docNode.path("description").asText(set.getDescription()));
            set.setTopikLevel(docNode.path("detectedCategory").asText(set.getTopikLevel()));
            String programCode = docNode.path("assessmentProgramCode").asText("");
            set.setAssessmentProgramCode(programCode.isBlank()
                    ? programCodeForCategory(set.getTopikLevel())
                    : programCode);
            Long programVersionId = nullableLong(docNode, "assessmentProgramVersionId");
            if (programVersionId != null) {
                set.setAssessmentProgramVersionId(programVersionId);
            }
            String examTemplateCode = nullableText(docNode, "examTemplateCode");
            if (examTemplateCode != null) {
                set.setExamTemplateCode(examTemplateCode);
            }
        }
        if (root.path("materials").isArray()) {
            try {
                JsonNode metadata = set.getMetadataJson() == null || set.getMetadataJson().isBlank()
                        ? objectMapper.createObjectNode()
                        : objectMapper.readTree(set.getMetadataJson());
                if (metadata instanceof com.fasterxml.jackson.databind.node.ObjectNode objectMetadata) {
                    objectMetadata.set("materials", root.path("materials").deepCopy());
                    set.setMetadataJson(objectMetadata.toString());
                }
            } catch (Exception exception) {
                throw new IllegalArgumentException("Metadata tài nguyên snapshot không hợp lệ.", exception);
            }
        }
        setRepository.save(set);
        Map<String, PracticeTest> persistedTests = persistTests(root, set);
        Map<Long, Integer> sectionOrderByTest = new LinkedHashMap<>();

        // Restore sections, groups and questions
        JsonNode sectionsNode = root.path("sections");
        if (sectionsNode.isArray()) {
            for (int sIdx = 0; sIdx < sectionsNode.size(); sIdx++) {
                JsonNode sNode = sectionsNode.get(sIdx);
                PracticeTest targetTest = resolveTestForSection(sNode, persistedTests);
                int sectionDisplayOrder = sectionOrderByTest.getOrDefault(targetTest.getId(), 0);
                sectionOrderByTest.put(targetTest.getId(), sectionDisplayOrder + 1);
                PracticeSection section = new PracticeSection(
                        setId,
                        sNode.path("title").asText("Phần " + (sIdx + 1)),
                        sNode.path("skill").asText("READING"),
                        sNode.path("lessonCode").asText("DEFAULT"),
                        sNode.path("instructions").asText(""),
                        sNode.path("durationMinutes").asInt(40),
                        BigDecimal.valueOf(sNode.path("totalPoints").asDouble(100.0)),
                        sectionDisplayOrder
                );
                section.setTestId(targetTest.getId());
                PracticeSection savedSection = sectionRepository.save(section);

                JsonNode groupsNode = sNode.path("groups");
                if (groupsNode.isArray()) {
                    for (int gIdx = 0; gIdx < groupsNode.size(); gIdx++) {
                        JsonNode gNode = groupsNode.get(gIdx);
                        
                        PracticeQuestionGroup group = new PracticeQuestionGroup(
                                setId,
                                gNode.path("groupCode").asText(gNode.path("label").asText("Câu")),
                                1,
                                1,
                                gNode.path("instruction").asText(""),
                                gNode.path("audioUrl").asText(null),
                                null,
                                gIdx
                        );
                        group.setSectionId(savedSection.getId());
                        applyStimulus(group, gNode, sNode.path("skill").asText("READING"));
                        PracticeQuestionGroup savedGroup = groupRepository.save(group);

                        JsonNode questionsNode = gNode.path("questions");
                        if (questionsNode.isArray()) {
                            for (int qIdx = 0; qIdx < questionsNode.size(); qIdx++) {
                                JsonNode qNode = questionsNode.get(qIdx);
                                
                                List<String> optList = new ArrayList<>();
                                JsonNode optsNode = qNode.path("options");
                                if (optsNode.isArray()) {
                                    for (JsonNode opt : optsNode) {
                                        optList.add(opt.isObject() ? opt.path("text").asText("") : opt.asText(""));
                                    }
                                }

                                String optJsonString = null;
                                if (!optList.isEmpty()) {
                                    try {
                                        optJsonString = objectMapper.writeValueAsString(optList);
                                    } catch (Exception e) {
                                        log.warn("[RevisionRestore] Failed options parse: {}", e.getMessage());
                                    }
                                }

                                String ansVal = qNode.path("answerKey").asText("");
                                String rawType = qNode.path("questionType").asText("MCQ");

                                PracticeQuestion question = new PracticeQuestion(
                                        setId,
                                        qNode.path("questionNo").asInt(qIdx + 1),
                                        rawType,
                                        qNode.path("prompt").asText(""),
                                        optJsonString,
                                        ansVal,
                                        qNode.path("explanationVi").asText(""),
                                        BigDecimal.valueOf(qNode.path("points").asDouble(1.0)),
                                        qIdx
                                );
                                question.setWritingTaskType(resolveWritingTaskTypeForRestore(
                                        sNode.path("skill").asText("READING"),
                                        rawType,
                                        qNode
                                ));
                                String canonicalType = qNode.path("canonicalQuestionType").asText("");
                                if (canonicalType.isBlank()) {
                                    canonicalType = questionTypeResolver.resolveOptional(rawType)
                                            .map(Enum::name)
                                            .orElse(null);
                                }
                                question.setCanonicalQuestionType(canonicalType);
                                question.setQuestionContentJson(jsonField(qNode, "questionContent"));
                                question.setAnswerSpecJson(jsonField(qNode, "answerSpec"));
                                question.setScoringPolicyCode(nullableText(qNode, "scoringPolicyCode"));
                                question.setScoringProfileCode(nullableText(qNode, "scoringProfileCode"));
                                question.setScoringProfileVersion(nullableInteger(qNode, "scoringProfileVersion"));
                                question.setPromptProfileCode(nullableText(qNode, "promptProfileCode"));
                                question.setPromptProfileVersion(nullableInteger(qNode, "promptProfileVersion"));
                                question.setRubricProfileCode(nullableText(qNode, "rubricProfileCode"));
                                question.setRubricProfileVersion(nullableInteger(qNode, "rubricProfileVersion"));
                                question.setGroupId(savedGroup.getId());
                                questionRepository.save(question);
                            }
                        }
                    }
                }
            }
        }

        // Create a new log indicating restoration
        PracticeEditLog newLog = new PracticeEditLog(
                setId,
                editorId,
                "Khôi phục về lịch sử sửa đổi ngày " + logEntry.getEditedAt(),
                "{}",
                null,
                snapshotJson,
                "ROLLBACK"
        );
        editLogRepository.save(newLog);
        log.info("[Revision] Successfully restored Set ID={} to revision from logId={}", setId, logId);
    }

    private Map<String, PracticeTest> persistTests(JsonNode root, PracticeSet set) {
        Map<String, PracticeTest> result = new LinkedHashMap<>();
        JsonNode tests = root.path("tests");
        if (tests.isArray()) {
            for (int index = 0; index < tests.size(); index++) {
                JsonNode testNode = tests.get(index);
                int testNo = testNode.path("testNo").asInt(index + 1);
                Integer estimatedMinutes = nullableInteger(testNode, "estimatedMinutes");
                PracticeTest saved = testRepository.save(new PracticeTest(
                        set.getId(),
                        testNode.path("title").asText("Test " + testNo),
                        testNode.path("description").asText(""),
                        index,
                        estimatedMinutes != null && estimatedMinutes > 0 ? estimatedMinutes : null
                ));
                result.put(testNode.path("clientId").asText("test-" + testNo), saved);
                result.put("no:" + testNo, saved);
                result.putIfAbsent("default", saved);
            }
        }
        if (result.isEmpty()) {
            PracticeTest saved = testRepository.save(new PracticeTest(
                    set.getId(), set.getTitle(), set.getDescription(), 0, null));
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
        if (test == null) throw new IllegalStateException("Snapshot section không tham chiếu test hợp lệ.");
        return test;
    }

    private void validateWritingTaskMetadata(JsonNode root) {
        JsonNode sections = root.path("sections");
        if (!sections.isArray()) {
            return;
        }
        for (JsonNode section : sections) {
            String skill = section.path("skill").asText("READING");
            JsonNode groups = section.path("groups");
            if (!groups.isArray()) {
                continue;
            }
            for (JsonNode group : groups) {
                JsonNode questions = group.path("questions");
                if (!questions.isArray()) {
                    continue;
                }
                for (JsonNode question : questions) {
                    String questionType = question.path("questionType").asText("MCQ");
                    if ("SPEAKING".equalsIgnoreCase(skill) && PracticeQuestion.TYPE_ESSAY.equals(questionType)) {
                        throw new IllegalArgumentException("Câu Speaking mới phải dùng question type SPEAKING; không dùng ESSAY cho bài nói.");
                    }
                    resolveWritingTaskTypeForRestore(skill, questionType, question);
                }
            }
        }
    }

    private void applyStimulus(PracticeQuestionGroup group, JsonNode groupNode, String skill) {
        JsonNode stimulus = groupNode.path("stimulus");
        String passage = firstNonBlank(stimulus.path("passageText").asText(""),
                groupNode.path("passageText").asText(""));
        String transcript = firstNonBlank(stimulus.path("transcriptText").asText(""),
                groupNode.path("transcriptText").asText(""));
        if (transcript.isBlank() && "LISTENING".equalsIgnoreCase(skill)) {
            transcript = groupNode.path("passageText").asText("");
        }
        String type = stimulus.path("type").asText("");
        if (type.isBlank()) {
            type = "READING".equalsIgnoreCase(skill) && !passage.isBlank()
                    ? "READING_PASSAGE"
                    : ("LISTENING".equalsIgnoreCase(skill) ? "LISTENING_AUDIO" : "NONE");
        }
        group.setStimulusType(type);
        group.setPassageText(blankToNull(passage));
        group.setTranscriptText(blankToNull(transcript));
        group.setImageUrl(blankToNull(firstNonBlank(stimulus.path("imageReference").asText(""),
                groupNode.path("imageUrl").asText(""))));
        if (stimulus.path("provenance").isObject()) {
            try {
                group.setStimulusProvenanceJson(objectMapper.writeValueAsString(stimulus.path("provenance")));
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

    private static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.longValue();
    }

    private WritingTaskType resolveWritingTaskTypeForRestore(String skill, String questionType, JsonNode question) {
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

    private static String jsonField(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        return value == null || value.isNull() ? null : value.toString();
    }

    private static String nullableText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        return value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()
                ? null
                : value.asText();
    }

    private static Integer nullableInteger(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        return value == null || value.isNull() || !value.canConvertToInt() ? null : value.asInt();
    }

    private static String programCodeForCategory(String category) {
        return category != null && category.toUpperCase().startsWith("TOPIK") ? "TOPIK" : "CUSTOM";
    }
}
