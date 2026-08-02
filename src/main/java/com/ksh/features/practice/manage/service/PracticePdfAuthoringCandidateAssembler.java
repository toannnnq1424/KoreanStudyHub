package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceSnapshot;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ValidationIssue;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts one strictly validated provider output into the sole AIM-2 staging boundary. */
@Service
public class PracticePdfAuthoringCandidateAssembler {

    private final ObjectMapper objectMapper;
    private final PracticePdfAuthoringOutputValidator outputValidator;
    private final PracticeAuthoringCandidateService candidateService;
    private final AssessmentAuthoringCatalogService authoringCatalog;

    public PracticePdfAuthoringCandidateAssembler(
            ObjectMapper objectMapper,
            PracticePdfAuthoringOutputValidator outputValidator,
            PracticeAuthoringCandidateService candidateService,
            AssessmentAuthoringCatalogService authoringCatalog) {
        this.objectMapper = objectMapper;
        this.outputValidator = outputValidator;
        this.candidateService = candidateService;
        this.authoringCatalog = authoringCatalog;
    }

    public CandidateView assemble(
            PracticePdfAuthoringRequest request,
            PracticePdfAiOrchestrator.GenerationResult generation,
            Long actorId) {
        ObjectNode output = outputValidator.validate(
                generation.output(), request).root();
        List<ValidationIssue> sourceIssues = new ArrayList<>();
        ArrayNode groups = candidateGroups(output.path("groups"), request, sourceIssues);
        appendProviderWarnings(output.path("warnings"), sourceIssues);

        SourceSnapshot source = new SourceSnapshot(
                SourceKind.PDF_AI,
                SourceKind.PDF_AI.contractVersion(),
                request.sourceDigest(),
                generation.sourceRevision(),
                request.sourceName(),
                request.operation(),
                generation.aiExecution());
        return candidateService.createOrReuse(
                new CreateCommand(actorId, source, request.target(), groups),
                sourceIssues);
    }

    private ArrayNode candidateGroups(
            JsonNode outputGroups,
            PracticePdfAuthoringRequest request,
            List<ValidationIssue> sourceIssues) {
        ArrayNode groups = objectMapper.createArrayNode();
        for (int groupIndex = 0; groupIndex < outputGroups.size(); groupIndex++) {
            JsonNode raw = outputGroups.get(groupIndex);
            ObjectNode group = groups.addObject();
            group.put("candidateGroupId", raw.path("sourceGroupId").asText());
            group.put("groupOrder", groupIndex + 1);
            group.put("label", raw.path("label").asText());
            group.put("instruction", raw.path("instruction").asText());
            group.set("sourceRefs", raw.path("sourceRefs").deepCopy());

            JsonNode rawStimulus = raw.path("stimulus");
            ObjectNode stimulus = group.putObject("stimulus");
            stimulus.put("schemaVersion", "practice-stimulus-v2");
            stimulus.put("type", rawStimulus.path("type").asText());
            stimulus.put("instruction", raw.path("instruction").asText());
            stimulus.put("passageText", rawStimulus.path("passageText").asText());
            stimulus.put("transcriptText", rawStimulus.path("transcriptText").asText());
            stimulus.putNull("mediaReference");
            ObjectNode provenance = stimulus.putObject("provenance");
            provenance.put("source", "PDF_AI");
            provenance.put("approved", false);
            provenance.set("sourceRefs", rawStimulus.path("sourceRefs").deepCopy());

            ArrayNode questions = group.putArray("questions");
            JsonNode rawQuestions = raw.path("questions");
            for (int questionIndex = 0;
                 questionIndex < rawQuestions.size(); questionIndex++) {
                JsonNode rawQuestion = rawQuestions.get(questionIndex);
                ObjectNode question = questions.addObject();
                question.put("candidateQuestionId",
                        rawQuestion.path("sourceQuestionId").asText());
                question.put("questionOrder", questionIndex + 1);
                String type = rawQuestion.path("questionType").asText();
                question.put("questionType", type);
                if (rawQuestion.hasNonNull("essayTaskType")) {
                    question.put("essayTaskType",
                            rawQuestion.path("essayTaskType").asText());
                }
                question.put("prompt", rawQuestion.path("prompt").asText());
                double authoritativePoints = authoritativePoints(
                        request.target().skill(),
                        rawQuestion.path("essayTaskType").asText());
                question.put("points", authoritativePoints);
                if (rawQuestion.has("explanationVi")) {
                    question.put("explanationVi",
                            rawQuestion.path("explanationVi").asText());
                }
                question.set("questionContent", resolveAssetReferences(
                        rawQuestion.path("questionContent"), request));
                question.set("answerSpec", rawQuestion.path("answerSpec").deepCopy());
                question.put("reviewState", "REVIEW_REQUIRED");
                question.set("sourceRefs", rawQuestion.path("sourceRefs").deepCopy());

                String questionPath = "/groups/" + groupIndex
                        + "/questions/" + questionIndex;
                double confidence = rawQuestion.path("confidence").asDouble();
                if (confidence < 0.8d) {
                    sourceIssues.add(warning(
                            "PDF_LOW_CONFIDENCE", questionPath,
                            "AI đánh dấu câu hỏi có độ tin cậy thấp; hãy kiểm tra nguồn và đáp án.",
                            firstSourceRef(rawQuestion.path("sourceRefs"))));
                }
                if (Math.abs(rawQuestion.path("points").asDouble()
                        - authoritativePoints) > 0.000_001d) {
                    sourceIssues.add(warning(
                            "PDF_PROVIDER_POINTS_NORMALIZED",
                            questionPath + "/points",
                            "Điểm do AI trả về đã được thay bằng điểm chuẩn của hệ thống.",
                            firstSourceRef(rawQuestion.path("sourceRefs"))));
                }
            }
        }
        return groups;
    }

    private double authoritativePoints(String skill, String writingTask) {
        AssessmentAuthoringCatalogService.SkillAuthoringPolicy policy =
                authoringCatalog.defaultTemplate().requireSkill(skill);
        if ("WRITING".equals(skill)) {
            AssessmentAuthoringCatalogService.WritingTaskAuthoringPolicy task =
                    policy.writingTask(writingTask);
            if (task == null) {
                throw new IllegalArgumentException("Writing task authority is unavailable");
            }
            return task.points().doubleValue();
        }
        BigDecimal value = policy.defaultPoints();
        return value == null ? 1d : value.doubleValue();
    }

    private void appendProviderWarnings(
            JsonNode warnings,
            List<ValidationIssue> issues) {
        for (JsonNode raw : warnings) {
            issues.add(warning(
                    "PDF_PROVIDER_WARNING",
                    "/groups",
                    raw.path("messageVi").asText(),
                    firstSourceRef(raw.path("sourceRefs"))));
        }
    }

    private ValidationIssue warning(
            String code,
            String path,
            String message,
            JsonNode sourceLocation) {
        ValidationIssue issue = ValidationIssue.warning(
                code, "SOURCE", path, message, "EDIT_IN_REVIEW");
        return sourceLocation == null ? issue : issue.withSourceLocation(sourceLocation);
    }

    private static JsonNode firstSourceRef(JsonNode refs) {
        return refs.isArray() && !refs.isEmpty() ? refs.get(0).deepCopy() : null;
    }

    @SuppressWarnings("unchecked")
    private JsonNode resolveAssetReferences(
            JsonNode raw,
            PracticePdfAuthoringRequest request) {
        JsonNode resolved = raw.deepCopy();
        Object value = request.sourceContext().get("assetReferences");
        Map<String, String> references = value instanceof Map<?, ?> map
                ? (Map<String, String>) map : Map.of();
        replaceAssetReference(resolved, references);
        return resolved;
    }

    private void replaceAssetReference(
            JsonNode node,
            Map<String, String> references) {
        if (node instanceof ObjectNode object) {
            for (String field : List.of("imageReference")) {
                JsonNode value = object.get(field);
                if (value != null && value.isTextual()) {
                    String authorized = references.get(value.asText());
                    if (authorized == null) {
                        throw new IllegalArgumentException(
                                "PDF source asset reference is unavailable");
                    }
                    object.put(field, authorized);
                }
            }
            object.fields().forEachRemaining(entry ->
                    replaceAssetReference(entry.getValue(), references));
        } else if (node.isArray()) {
            node.forEach(value -> replaceAssetReference(value, references));
        }
    }
}
