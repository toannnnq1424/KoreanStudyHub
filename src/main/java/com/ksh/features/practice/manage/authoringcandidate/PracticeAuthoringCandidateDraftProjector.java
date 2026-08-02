package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PracticeAuthoringCandidateDraftProjector {

    private final ObjectMapper objectMapper;

    public PracticeAuthoringCandidateDraftProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode append(
            String draftJson,
            PracticeAuthoringCandidate candidate,
            JsonNode candidateEnvelope) {
        ObjectNode draft = readDraft(draftJson);
        ObjectNode section = exactTargetSection(draft, candidate);
        ArrayNode targetGroups = section.withArray("groups");
        for (JsonNode candidateGroup : candidateEnvelope.path("groups")) {
            targetGroups.add(toDraftGroup(
                    candidateGroup, candidate.getSourceKind().name()));
        }
        return draft;
    }

    private ObjectNode toDraftGroup(JsonNode candidateGroup, String sourceKind) {
        ObjectNode group = objectMapper.createObjectNode();
        group.put("clientId",
                candidateGroup.path("candidateGroupId").asText());
        group.put("label", candidateGroup.path("label").asText());
        group.put("instruction",
                candidateGroup.path("instruction").asText(""));
        group.set("stimulus", candidateGroup.path("stimulus").deepCopy());
        group.putArray("sourceRegionIds");
        ArrayNode questions = group.putArray("questions");
        for (JsonNode candidateQuestion : candidateGroup.path("questions")) {
            questions.add(toDraftQuestion(candidateQuestion, sourceKind));
        }
        return group;
    }

    private ObjectNode toDraftQuestion(
            JsonNode candidateQuestion, String sourceKind) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("clientId",
                candidateQuestion.path("candidateQuestionId").asText());
        copy(candidateQuestion, question, "questionType");
        copy(candidateQuestion, question, "essayTaskType");
        copy(candidateQuestion, question, "prompt");
        copy(candidateQuestion, question, "points");
        copy(candidateQuestion, question, "explanationVi");
        copy(candidateQuestion, question, "explanationStrategy");
        copy(candidateQuestion, question, "questionContent");
        copy(candidateQuestion, question, "answerSpec");
        question.put("importSource", sourceKind);
        question.put("reviewRequired", false);
        question.putArray("sourceRegionIds");

        ArrayNode legacyOptions = question.putArray("options");
        JsonNode options = candidateQuestion.path("questionContent")
                .path("options");
        if (options.isArray()) {
            options.forEach(option -> legacyOptions.add(option.deepCopy()));
        }
        return question;
    }

    private static void copy(
            JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) target.set(field, source.get(field).deepCopy());
    }

    private ObjectNode exactTargetSection(
            ObjectNode draft,
            PracticeAuthoringCandidate candidate) {
        List<ObjectNode> matches = new java.util.ArrayList<>();
        for (JsonNode value : draft.path("sections")) {
            if (value instanceof ObjectNode section
                    && section.path("testNo").asInt()
                    == candidate.getTargetTestNo()
                    && candidate.getTargetSkill().equalsIgnoreCase(
                    section.path("skill").asText())
                    && candidate.getTargetLessonCode().equalsIgnoreCase(
                    section.path("lessonCode").asText())) {
                matches.add(section);
            }
        }
        if (matches.size() != 1) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_TARGET_SECTION_NOT_FOUND",
                    "Không tìm thấy đúng một section target khi áp dụng.");
        }
        return matches.get(0);
    }

    private ObjectNode readDraft(String value) {
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed instanceof ObjectNode object) return object;
        } catch (Exception exception) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_TARGET_INVALID",
                    "Bản nháp target không có JSON hợp lệ.");
        }
        throw new PracticeAuthoringCandidateException(
                "CANDIDATE_TARGET_INVALID",
                "Bản nháp target phải là một JSON object.");
    }
}
