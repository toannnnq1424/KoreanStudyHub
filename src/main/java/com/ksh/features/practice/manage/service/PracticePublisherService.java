package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeEditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PracticePublisherService {

    private static final Logger log = LoggerFactory.getLogger(PracticePublisherService.class);

    private final PracticeDraftRepository draftRepository;
    private final PracticeSetRepository setRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeEditLogRepository editLogRepository;
    private final PracticeDraftValidator draftValidator;
    private final ObjectMapper objectMapper;

    public PracticePublisherService(PracticeDraftRepository draftRepository,
                                    PracticeSetRepository setRepository,
                                    PracticeSectionRepository sectionRepository,
                                    PracticeQuestionGroupRepository groupRepository,
                                    PracticeQuestionRepository questionRepository,
                                    PracticeEditLogRepository editLogRepository,
                                    PracticeDraftValidator draftValidator,
                                    ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.questionRepository = questionRepository;
        this.editLogRepository = editLogRepository;
        this.draftValidator = draftValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long publish(Long draftId, Long ownerId) {
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Bản nháp không tồn tại."));

        // Parse JSON
        JsonNode root;
        try {
            root = objectMapper.readTree(draft.getDraftJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("Dữ liệu JSON bản nháp bị lỗi cú pháp.");
        }
        validateWritingTaskMetadata(root);

        // Validate
        PracticeDraftValidator.ValidationResult valRes = draftValidator.validate(draft.getDraftJson());
        if (valRes.hasBlocking()) {
            throw new IllegalStateException("Không thể xuất bản bản nháp do chứa lỗi nghiêm trọng (BLOCKING). Vui lòng kiểm tra lại cấu trúc đề.");
        }

        // Validate category
        String category = draft.getCategory();
        if (category == null || category.isBlank() || "UNCLASSIFIED".equalsIgnoreCase(category)) {
            throw new IllegalStateException("Không thể xuất bản bản nháp chưa phân loại (UNCLASSIFIED). Vui lòng chọn phân loại bộ đề trước.");
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
            metaJson = objectMapper.writeValueAsString(metaMap);
        } catch (Exception e) {
            log.warn("[Publisher] Failed serialize metadataJson: {}", e.getMessage());
        }

        PracticeSet set;
        String beforeSnapshot = null;
        if (draft.getPublishedSetId() != null) {
            set = setRepository.findById(draft.getPublishedSetId())
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Học liệu gốc không tồn tại."));
            
            // Capture before snapshot
            beforeSnapshot = captureSetSnapshot(set.getId());

            // Clear old children (cascade is handled by deleteBySetId)
            questionRepository.deleteBySetId(set.getId());
            groupRepository.deleteBySetId(set.getId());
            sectionRepository.deleteBySetId(set.getId());
            
            questionRepository.flush();
            groupRepository.flush();
            sectionRepository.flush();

            // Update set metadata
            set.setTitle(draft.getTitle());
            set.setDescription(draft.getDescription());
            set.setSkill(targetSkill);
            set.setTopikLevel(draft.getCategory());
            set.setScope(draft.getScope());
            set.setClassId(draft.getClassId());
            set.setMetadataJson(metaJson);
            set.setCreationMethod(draft.getCreationMethod());
        } else {
            set = new PracticeSet(
                    draft.getTitle(),
                    draft.getDescription(),
                    targetSkill,
                    draft.getCategory(),
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

        // Save sections, groups and questions
        if (sectionsNode.isArray()) {
            for (int sIdx = 0; sIdx < sectionsNode.size(); sIdx++) {
                JsonNode sNode = sectionsNode.get(sIdx);
                PracticeSection section = new PracticeSection(
                        savedSet.getId(),
                        sNode.path("title").asText("Phần " + (sIdx + 1)),
                        sNode.path("skill").asText("READING"),
                        sNode.path("sectionType").asText("DEFAULT"),
                        sNode.path("instructions").asText(""),
                        sNode.path("durationMinutes").asInt(40),
                        BigDecimal.valueOf(sNode.path("totalPoints").asDouble(100.0)),
                        sIdx
                );
                PracticeSection savedSection = sectionRepository.save(section);

                // Counter reset per section — questions numbered 1, 2, 3… independently in each section
                int sectionLocalQNo = 1;

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
                        String gImgUrl = gNode.path("imageUrl").asText("");
                        if (gImgUrl != null && !gImgUrl.isBlank()) {
                            instruction = "![image](" + gImgUrl + ")\n" + instruction;
                        }

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
                                gNode.path("label").asText("Câu"),
                                groupQuestionFrom,
                                groupQuestionTo,
                                instruction,
                                gNode.path("audioUrl").asText(null),
                                exampleBoxJson,
                                gIdx
                        );
                        group.setSectionId(savedSection.getId());
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

                                String rawType = qNode.path("questionType").asText("MCQ");
                                String dbType = mapUiTypeToDbType(rawType);

                                String qPrompt = qNode.path("prompt").asText("");
                                String qImgUrl = qNode.path("imageUrl").asText("");
                                if (qImgUrl != null && !qImgUrl.isBlank()) {
                                    qPrompt = "![image](" + qImgUrl + ")\n" + qPrompt;
                                }

                                PracticeQuestion question = new PracticeQuestion(
                                        savedSet.getId(),
                                        sectionLocalQNo,   // enforce section-local numbering
                                        dbType,
                                        qPrompt,
                                        optJsonString,
                                        ansVal,
                                        qNode.path("explanationVi").asText(""),
                                        BigDecimal.valueOf(qNode.path("points").asDouble(1.0)),
                                        qIdx
                                );
                                question.setWritingTaskType(resolveWritingTaskTypeForPublish(
                                        sNode.path("skill").asText("READING"),
                                        dbType,
                                        qNode
                                ));
                                question.setGroupId(savedGroup.getId());
                                questionRepository.save(question);
                                sectionLocalQNo++;
                            }
                        }
                    }
                }
            }
        }

        // Set draft status as PUBLISHED so we know it is done
        draft.setStatus("PUBLISHED");
        draftRepository.save(draft);

        // Capture after snapshot
        String afterSnapshot = captureSetSnapshot(savedSet.getId());
        String editType = (beforeSnapshot == null) ? "METADATA" : determineEditType(beforeSnapshot, afterSnapshot);

        // Save PracticeEditLog
        com.ksh.entities.PracticeEditLog logEntry = new com.ksh.entities.PracticeEditLog(
                savedSet.getId(),
                ownerId,
                "Cập nhật cấu trúc học liệu qua editor",
                "{}",
                beforeSnapshot,
                afterSnapshot,
                editType
        );
        editLogRepository.save(logEntry);

        // Send collaborative notification
        log.info("[Notification] Sent collaborative edit notification for Set ID={} (editType={}) to owner={} and administrators.",
                savedSet.getId(), editType, savedSet.getCreatedBy());

        log.info("[Publisher] Complete publish draftId={} to setId={}", draftId, savedSet.getId());
        return savedSet.getId();
    }

    private String captureSetSnapshot(Long setId) {
        try {
            java.util.Map<String, Object> root = new java.util.HashMap<>();
            PracticeSet set = setRepository.findById(setId).orElse(null);
            if (set == null) return "{}";

            java.util.Map<String, Object> doc = new java.util.HashMap<>();
            doc.put("title", set.getTitle());
            doc.put("description", set.getDescription());
            doc.put("detectedCategory", set.getTopikLevel());
            root.put("document", doc);

            List<java.util.Map<String, Object>> sectionsList = new java.util.ArrayList<>();
            List<PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
            for (PracticeSection sec : sections) {
                java.util.Map<String, Object> secMap = new java.util.HashMap<>();
                secMap.put("title", sec.getTitle());
                secMap.put("skill", sec.getSkill());
                secMap.put("durationMinutes", sec.getDurationMinutes());
                secMap.put("totalPoints", sec.getTotalPoints());

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
                        grpMap.put("instruction", grp.getInstruction());
                        grpMap.put("audioUrl", grp.getAudioUrl());
                        
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
            return "{}";
        }
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
        if (uiType == null) return "MCQ";
        return switch (uiType.toUpperCase()) {
            case "SINGLE_CHOICE", "MULTIPLE_CHOICE" -> "MCQ";
            case "MATCHING" -> "MATCHING_INFORMATION";
            case "GAP_FILL" -> "FILL_BLANK";
            default -> uiType.toUpperCase();
        };
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
                    String dbType = mapUiTypeToDbType(question.path("questionType").asText("MCQ"));
                    resolveWritingTaskTypeForPublish(skill, dbType, question);
                }
            }
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
