package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.*;
import com.ksh.features.practice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PracticeRevisionService {

    private static final Logger log = LoggerFactory.getLogger(PracticeRevisionService.class);

    private final PracticeSetRepository setRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeEditLogRepository editLogRepository;
    private final ObjectMapper objectMapper;

    public PracticeRevisionService(PracticeSetRepository setRepository,
                                   PracticeSectionRepository sectionRepository,
                                   PracticeQuestionGroupRepository groupRepository,
                                   PracticeQuestionRepository questionRepository,
                                   PracticeEditLogRepository editLogRepository,
                                   ObjectMapper objectMapper) {
        this.setRepository = setRepository;
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.questionRepository = questionRepository;
        this.editLogRepository = editLogRepository;
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
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Học liệu gốc không tồn tại."));

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
        
        questionRepository.flush();
        groupRepository.flush();
        sectionRepository.flush();

        // Restore set metadata
        JsonNode docNode = root.path("document");
        if (!docNode.isMissingNode()) {
            set.setTitle(docNode.path("title").asText(set.getTitle()));
            set.setDescription(docNode.path("description").asText(set.getDescription()));
            set.setTopikLevel(docNode.path("detectedCategory").asText(set.getTopikLevel()));
        }
        setRepository.save(set);

        // Restore sections, groups and questions
        JsonNode sectionsNode = root.path("sections");
        if (sectionsNode.isArray()) {
            for (int sIdx = 0; sIdx < sectionsNode.size(); sIdx++) {
                JsonNode sNode = sectionsNode.get(sIdx);
                PracticeSection section = new PracticeSection(
                        setId,
                        sNode.path("title").asText("Phần " + (sIdx + 1)),
                        sNode.path("skill").asText("READING"),
                        "DEFAULT",
                        "",
                        sNode.path("durationMinutes").asInt(40),
                        BigDecimal.valueOf(sNode.path("totalPoints").asDouble(100.0)),
                        sIdx
                );
                PracticeSection savedSection = sectionRepository.save(section);

                JsonNode groupsNode = sNode.path("groups");
                if (groupsNode.isArray()) {
                    for (int gIdx = 0; gIdx < groupsNode.size(); gIdx++) {
                        JsonNode gNode = groupsNode.get(gIdx);
                        
                        PracticeQuestionGroup group = new PracticeQuestionGroup(
                                setId,
                                gNode.path("label").asText("Câu"),
                                1,
                                1,
                                gNode.path("instruction").asText(""),
                                gNode.path("audioUrl").asText(null),
                                null,
                                gIdx
                        );
                        group.setSectionId(savedSection.getId());
                        PracticeQuestionGroup savedGroup = groupRepository.save(group);

                        JsonNode questionsNode = gNode.path("questions");
                        if (questionsNode.isArray()) {
                            for (int qIdx = 0; qIdx < questionsNode.size(); qIdx++) {
                                JsonNode qNode = questionsNode.get(qIdx);
                                
                                List<String> optList = new ArrayList<>();
                                JsonNode optsNode = qNode.path("options");
                                if (optsNode.isArray()) {
                                    for (JsonNode opt : optsNode) {
                                        optList.add(opt.asText(""));
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
}
