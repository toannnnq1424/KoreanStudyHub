package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ValidationIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PracticeAuthoringCandidateNormalizer {

    private static final Set<String> GROUP_FIELDS = Set.of(
            "candidateGroupId", "groupOrder", "label", "instruction",
            "stimulus", "sourceRefs", "questions");
    private static final Set<String> STIMULUS_FIELDS = Set.of(
            "schemaVersion", "type", "instruction", "passageText",
            "transcriptText", "mediaReference", "provenance");
    private static final Set<String> PROVENANCE_FIELDS = Set.of(
            "source", "approved", "sourceRefs");
    private static final Set<String> QUESTION_FIELDS = Set.of(
            "candidateQuestionId", "questionOrder", "questionType",
            "essayTaskType", "prompt", "points", "explanationVi",
            "explanationStrategy", "questionContent", "answerSpec",
            "reviewState", "sourceRefs");
    private static final Set<String> EXPLANATION_STRATEGY_FIELDS = Set.of(
            "registryVersion", "strategyCode", "strategyVersion");
    private static final Set<String> QUESTION_CONTENT_FIELDS = Set.of(
            "schemaVersion", "options", "blanks", "imageReference",
            "audioReference", "speakingDelivery", "writingResponse",
            "languageTag");
    private static final Set<String> OPTION_FIELDS = Set.of(
            "id", "text", "imageReference");
    private static final Set<String> CONTENT_BLANK_FIELDS = Set.of(
            "id", "prompt");
    private static final Set<String> SPEAKING_DELIVERY_FIELDS = Set.of(
            "inputType", "deliveryMode", "promptAudioReference",
            "audioOrigin", "promptPlayLimit", "preparationSeconds",
            "responseSeconds");
    private static final Set<String> WRITING_RESPONSE_FIELDS = Set.of(
            "responseSchemaVersion", "responseMode", "taskType", "blanks");
    private static final Set<String> WRITING_RESPONSE_BLANK_FIELDS = Set.of(
            "blankId", "ordinal", "context");
    private static final Set<String> ANSWER_FIELDS = Set.of(
            "schemaVersion", "questionType", "correctOptionIds",
            "correctValue", "blanks", "scoringPolicyCode",
            "writingBlankAuthority");
    private static final Set<String> ANSWER_BLANK_FIELDS = Set.of(
            "blankId", "acceptedValues");
    private static final Set<String> WRITING_AUTHORITY_FIELDS = Set.of(
            "contractVersion", "taskType", "normalization",
            "whitespacePolicy", "blanks");
    private static final Set<String> WRITING_AUTHORITY_BLANK_FIELDS = Set.of(
            "blankId", "ordinal", "acceptedAnswers");
    private static final Set<String> WRITING_ACCEPTED_ANSWER_FIELDS = Set.of(
            "text", "equivalence", "reason", "evidenceIds");
    private static final Set<String> SOURCE_REF_FIELDS = Set.of(
            "kind", "sourceId", "sheet", "row", "column", "pageNumber",
            "start", "end");

    private final ObjectMapper objectMapper;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final PracticeAuthoringCandidateJson candidateJson;

    public PracticeAuthoringCandidateNormalizer(
            ObjectMapper objectMapper,
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            PracticeAuthoringCandidateJson candidateJson) {
        this.objectMapper = objectMapper;
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.candidateJson = candidateJson;
    }

    public NormalizationResult normalize(
            String candidateId,
            SourceKind sourceKind,
            JsonNode rawGroups) {
        List<ValidationIssue> issues = new ArrayList<>();
        ArrayNode groups = objectMapper.createArrayNode();
        if (rawGroups == null || !rawGroups.isArray()) {
            issues.add(error(
                    "CANDIDATE_GROUPS_REQUIRED", "CANDIDATE", "/groups",
                    "Candidate phải chứa danh sách nhóm câu hỏi."));
            return new NormalizationResult(groups, issues);
        }

        for (int index = 0; index < rawGroups.size(); index++) {
            JsonNode raw = rawGroups.get(index);
            String path = "/groups/" + index;
            if (!raw.isObject()) {
                issues.add(error(
                        "CANDIDATE_GROUP_INVALID", "GROUP", path,
                        "Nhóm câu hỏi phải là một JSON object."));
                continue;
            }
            unknownFields(raw, GROUP_FIELDS, path, issues);
            groups.add(normalizeGroup(
                    candidateId, sourceKind, raw, index, path, issues));
        }

        List<JsonNode> ordered = new ArrayList<>();
        groups.forEach(ordered::add);
        ordered.sort(Comparator
                .comparingInt((JsonNode group) -> group.path("groupOrder").asInt())
                .thenComparing(group -> group.path("candidateGroupId").asText()));
        groups.removeAll();
        ordered.forEach(groups::add);
        return new NormalizationResult(groups, List.copyOf(issues));
    }

    private ObjectNode normalizeGroup(
            String candidateId,
            SourceKind sourceKind,
            JsonNode raw,
            int groupIndex,
            String path,
            List<ValidationIssue> issues) {
        ObjectNode group = objectMapper.createObjectNode();
        group.put("candidateGroupId", stableId(
                raw.path("candidateGroupId").asText(""),
                "grp", candidateId, groupIndex, path, issues));
        group.put("groupOrder", raw.path("groupOrder").asInt(groupIndex + 1));
        group.put("label", text(raw, "label"));
        group.put("instruction", text(raw, "instruction"));
        group.set("stimulus", normalizeStimulus(
                sourceKind, raw.path("stimulus"), path + "/stimulus", issues));
        group.set("sourceRefs", normalizeSourceRefs(
                raw.path("sourceRefs"), path + "/sourceRefs", issues));

        ArrayNode questions = objectMapper.createArrayNode();
        JsonNode rawQuestions = raw.path("questions");
        if (!rawQuestions.isArray()) {
            issues.add(error(
                    "CANDIDATE_QUESTIONS_REQUIRED", "GROUP",
                    path + "/questions",
                    "Nhóm phải chứa danh sách câu hỏi."));
        } else {
            for (int questionIndex = 0;
                 questionIndex < rawQuestions.size(); questionIndex++) {
                JsonNode question = rawQuestions.get(questionIndex);
                String questionPath = path + "/questions/" + questionIndex;
                if (!question.isObject()) {
                    issues.add(error(
                            "CANDIDATE_QUESTION_INVALID", "QUESTION",
                            questionPath,
                            "Câu hỏi phải là một JSON object."));
                    continue;
                }
                unknownFields(question, QUESTION_FIELDS, questionPath, issues);
                questions.add(normalizeQuestion(
                        candidateId, question, groupIndex, questionIndex,
                        questionPath, issues));
            }
        }
        List<JsonNode> ordered = new ArrayList<>();
        questions.forEach(ordered::add);
        ordered.sort(Comparator
                .comparingInt((JsonNode question) ->
                        question.path("questionOrder").asInt())
                .thenComparing(question ->
                        question.path("candidateQuestionId").asText()));
        questions.removeAll();
        ordered.forEach(questions::add);
        group.set("questions", questions);
        return group;
    }

    private ObjectNode normalizeStimulus(
            SourceKind sourceKind,
            JsonNode raw,
            String path,
            List<ValidationIssue> issues) {
        if (!raw.isObject()) {
            issues.add(error(
                    "CANDIDATE_STIMULUS_INVALID", "FIELD", path,
                    "Stimulus phải là một JSON object."));
            raw = objectMapper.createObjectNode();
        }
        unknownFields(raw, STIMULUS_FIELDS, path, issues);
        ObjectNode stimulus = objectMapper.createObjectNode();
        stimulus.put("schemaVersion", "practice-stimulus-v2");
        stimulus.put("type", upper(text(raw, "type", "NONE")));
        stimulus.put("instruction", text(raw, "instruction"));
        stimulus.put("passageText", text(raw, "passageText"));
        stimulus.put("transcriptText", text(raw, "transcriptText"));
        putNullableText(stimulus, "mediaReference", raw.get("mediaReference"));

        JsonNode rawProvenance = raw.path("provenance");
        if (!rawProvenance.isObject()) {
            rawProvenance = objectMapper.createObjectNode();
        }
        unknownFields(rawProvenance, PROVENANCE_FIELDS,
                path + "/provenance", issues);
        ObjectNode provenance = objectMapper.createObjectNode();
        provenance.put("source", sourceKind.name());
        provenance.put("approved", rawProvenance.path("approved").asBoolean(false));
        provenance.set("sourceRefs", normalizeSourceRefs(
                rawProvenance.path("sourceRefs"),
                path + "/provenance/sourceRefs", issues));
        stimulus.set("provenance", provenance);
        return stimulus;
    }

    private ObjectNode normalizeQuestion(
            String candidateId,
            JsonNode raw,
            int groupIndex,
            int questionIndex,
            String path,
            List<ValidationIssue> issues) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("candidateQuestionId", stableId(
                raw.path("candidateQuestionId").asText(""),
                "q", candidateId + ":" + groupIndex,
                questionIndex, path, issues));
        question.put("questionOrder",
                raw.path("questionOrder").asInt(questionIndex + 1));
        String rawType = upper(text(raw, "questionType"));
        question.put("questionType", rawType);
        if (raw.hasNonNull("essayTaskType")) {
            question.put("essayTaskType", upper(text(raw, "essayTaskType")));
        }
        question.put("prompt", text(raw, "prompt"));
        question.put("points", raw.path("points").asDouble(0));
        if (raw.has("explanationVi")) {
            question.put("explanationVi", text(raw, "explanationVi"));
        }
        if (raw.path("explanationStrategy").isObject()) {
            unknownFields(raw.path("explanationStrategy"),
                    EXPLANATION_STRATEGY_FIELDS,
                    path + "/explanationStrategy", issues);
            question.set("explanationStrategy",
                    normalizeExplanationStrategy(raw.path("explanationStrategy")));
        }

        validateRawTypedContracts(raw, rawType, path, issues);
        ObjectNode content = normalizeQuestionContent(
                raw.path("questionContent"), path + "/questionContent", issues);
        ObjectNode answer = normalizeAnswerSpec(
                raw.path("answerSpec"), rawType,
                path + "/answerSpec", issues);
        question.set("questionContent", content);
        question.set("answerSpec", answer);
        question.put("reviewState", upper(text(
                raw, "reviewState", "REVIEW_REQUIRED")));
        question.set("sourceRefs", normalizeSourceRefs(
                raw.path("sourceRefs"), path + "/sourceRefs", issues));

        validateTypedContracts(question, content, answer, path, issues);
        return question;
    }

    private void validateRawTypedContracts(
            JsonNode question,
            String rawType,
            String path,
            List<ValidationIssue> issues) {
        try {
            CanonicalQuestionType type = typeResolver.resolve(rawType);
            QuestionContent content = contractCodec.readQuestionContent(
                    candidateJson.write(question.path("questionContent")), type);
            AnswerSpec answer = contractCodec.readAnswerSpec(
                    candidateJson.write(question.path("answerSpec")), content);
            if (answer.questionType() != type) {
                throw new IllegalArgumentException(
                        "Answer type does not match question type");
            }
        } catch (IllegalArgumentException exception) {
            issues.add(error(
                    "CANDIDATE_RAW_TYPED_CONTRACT_INVALID", "QUESTION", path,
                    "Dữ liệu typed đầu vào chứa trường lạ hoặc sai contract."));
        }
    }

    private ObjectNode normalizeQuestionContent(
            JsonNode raw,
            String path,
            List<ValidationIssue> issues) {
        if (!raw.isObject()) {
            issues.add(error(
                    "QUESTION_CONTENT_REQUIRED", "FIELD", path,
                    "Câu hỏi thiếu nội dung typed."));
            raw = objectMapper.createObjectNode();
        }
        unknownFields(raw, QUESTION_CONTENT_FIELDS, path, issues);
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", text(
                raw, "schemaVersion", "question-content-v3"));
        ArrayNode options = content.putArray("options");
        JsonNode rawOptions = raw.path("options");
        if (rawOptions.isArray()) {
            for (int index = 0; index < rawOptions.size(); index++) {
                JsonNode value = rawOptions.get(index);
                unknownFields(value, OPTION_FIELDS,
                        path + "/options/" + index, issues);
                ObjectNode option = options.addObject();
                option.put("id", text(value, "id"));
                option.put("text", text(value, "text"));
                if (value.has("imageReference")) {
                    putNullableText(option, "imageReference",
                            value.get("imageReference"));
                }
            }
        }
        ArrayNode blanks = content.putArray("blanks");
        JsonNode rawBlanks = raw.path("blanks");
        if (rawBlanks.isArray()) {
            for (int index = 0; index < rawBlanks.size(); index++) {
                JsonNode value = rawBlanks.get(index);
                unknownFields(value, CONTENT_BLANK_FIELDS,
                        path + "/blanks/" + index, issues);
                ObjectNode blank = blanks.addObject();
                blank.put("id", text(value, "id"));
                blank.put("prompt", text(value, "prompt"));
            }
        }
        copyNullableText(raw, content, "imageReference");
        copyNullableText(raw, content, "audioReference");
        if (raw.path("speakingDelivery").isObject()) {
            content.set("speakingDelivery", normalizeSpeakingDelivery(
                    raw.path("speakingDelivery"),
                    path + "/speakingDelivery", issues));
        }
        if (raw.path("writingResponse").isObject()) {
            content.set("writingResponse", normalizeWritingResponse(
                    raw.path("writingResponse"),
                    path + "/writingResponse", issues));
        }
        if (raw.hasNonNull("languageTag")) {
            content.put("languageTag", text(raw, "languageTag")
                    .toLowerCase(java.util.Locale.ROOT));
        }
        return content;
    }

    private ObjectNode normalizeAnswerSpec(
            JsonNode raw,
            String questionType,
            String path,
            List<ValidationIssue> issues) {
        if (!raw.isObject()) {
            issues.add(error(
                    "ANSWER_SPEC_REQUIRED", "FIELD", path,
                    "Câu hỏi thiếu đáp án typed."));
            raw = objectMapper.createObjectNode();
        }
        unknownFields(raw, ANSWER_FIELDS, path, issues);
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("schemaVersion", text(raw, "schemaVersion", "answer-spec-v1"));
        answer.put("questionType", upper(text(
                raw, "questionType", questionType)));
        ArrayNode correctIds = answer.putArray("correctOptionIds");
        if (raw.path("correctOptionIds").isArray()) {
            raw.path("correctOptionIds").forEach(value ->
                    correctIds.add(PracticeAuthoringCandidateJson.normalizedText(
                            value.asText(""))));
        }
        putNullableText(answer, "correctValue", raw.get("correctValue"));
        ArrayNode blanks = answer.putArray("blanks");
        if (raw.path("blanks").isArray()) {
            for (int index = 0; index < raw.path("blanks").size(); index++) {
                JsonNode value = raw.path("blanks").get(index);
                unknownFields(value, ANSWER_BLANK_FIELDS,
                        path + "/blanks/" + index, issues);
                ObjectNode blank = blanks.addObject();
                blank.put("blankId", text(value, "blankId"));
                ArrayNode acceptedValues = blank.putArray("acceptedValues");
                if (value.path("acceptedValues").isArray()) {
                    value.path("acceptedValues").forEach(item ->
                            acceptedValues.add(
                                    PracticeAuthoringCandidateJson.normalizedText(
                                            item.asText(""))));
                }
            }
        }
        answer.put("scoringPolicyCode", upper(text(
                raw, "scoringPolicyCode", "PROFILE_BASED")));
        if (raw.path("writingBlankAuthority").isObject()) {
            answer.set("writingBlankAuthority", normalizeWritingAuthority(
                    raw.path("writingBlankAuthority"),
                    path + "/writingBlankAuthority", issues));
        }
        return answer;
    }

    private ObjectNode normalizeWritingResponse(
            JsonNode raw,
            String path,
            List<ValidationIssue> issues) {
        ObjectNode response = normalizeFilteredObject(
                raw, WRITING_RESPONSE_FIELDS, path, issues);
        response.put("responseSchemaVersion", text(raw, "responseSchemaVersion"));
        response.put("responseMode", text(raw, "responseMode"));
        response.put("taskType", upper(text(raw, "taskType")));
        ArrayNode blanks = objectMapper.createArrayNode();
        JsonNode rawBlanks = raw.path("blanks");
        if (rawBlanks.isArray()) {
            for (int index = 0; index < rawBlanks.size(); index++) {
                JsonNode rawBlank = rawBlanks.get(index);
                ObjectNode blank = normalizeFilteredObject(
                        rawBlank, WRITING_RESPONSE_BLANK_FIELDS,
                        path + "/blanks/" + index, issues);
                blank.put("blankId", text(rawBlank, "blankId"));
                blank.put("ordinal", rawBlank.path("ordinal").asInt(0));
                blank.put("context", text(rawBlank, "context"));
                blanks.add(blank);
            }
        }
        response.set("blanks", blanks);
        return response;
    }

    private ObjectNode normalizeWritingAuthority(
            JsonNode raw,
            String path,
            List<ValidationIssue> issues) {
        ObjectNode authority = normalizeFilteredObject(
                raw, WRITING_AUTHORITY_FIELDS, path, issues);
        authority.put("contractVersion", text(raw, "contractVersion"));
        authority.put("taskType", upper(text(raw, "taskType")));
        authority.put("normalization", upper(text(raw, "normalization")));
        authority.put("whitespacePolicy",
                upper(text(raw, "whitespacePolicy")));
        ArrayNode blanks = objectMapper.createArrayNode();
        JsonNode rawBlanks = raw.path("blanks");
        if (rawBlanks.isArray()) {
            for (int index = 0; index < rawBlanks.size(); index++) {
                JsonNode rawBlank = rawBlanks.get(index);
                ObjectNode blank = normalizeFilteredObject(
                        rawBlank, WRITING_AUTHORITY_BLANK_FIELDS,
                        path + "/blanks/" + index, issues);
                blank.put("blankId", text(rawBlank, "blankId"));
                blank.put("ordinal", rawBlank.path("ordinal").asInt(0));
                ArrayNode accepted = objectMapper.createArrayNode();
                JsonNode rawAccepted = rawBlank.path("acceptedAnswers");
                if (rawAccepted.isArray()) {
                    for (int answerIndex = 0;
                         answerIndex < rawAccepted.size(); answerIndex++) {
                        JsonNode rawAnswer = rawAccepted.get(answerIndex);
                        ObjectNode answer = normalizeFilteredObject(
                                rawAnswer,
                                WRITING_ACCEPTED_ANSWER_FIELDS,
                                path + "/blanks/" + index
                                        + "/acceptedAnswers/" + answerIndex,
                                issues);
                        answer.put("text", text(rawAnswer, "text"));
                        answer.put("equivalence",
                                upper(text(rawAnswer, "equivalence")));
                        if (rawAnswer.has("reason")) {
                            putNullableText(answer, "reason",
                                    rawAnswer.get("reason"));
                        }
                        ArrayNode evidenceIds = objectMapper.createArrayNode();
                        if (rawAnswer.path("evidenceIds").isArray()) {
                            rawAnswer.path("evidenceIds").forEach(value ->
                                    evidenceIds.add(PracticeAuthoringCandidateJson
                                            .normalizedText(value.asText(""))));
                        }
                        answer.set("evidenceIds", evidenceIds);
                        accepted.add(answer);
                    }
                }
                blank.set("acceptedAnswers", accepted);
                blanks.add(blank);
            }
        }
        authority.set("blanks", blanks);
        return authority;
    }

    private ObjectNode normalizeSpeakingDelivery(
            JsonNode raw,
            String path,
            List<ValidationIssue> issues) {
        ObjectNode delivery = normalizeFilteredObject(
                raw, SPEAKING_DELIVERY_FIELDS, path, issues);
        delivery.put("inputType", text(raw, "inputType").toLowerCase(
                java.util.Locale.ROOT));
        delivery.put("deliveryMode", text(raw, "deliveryMode").toLowerCase(
                java.util.Locale.ROOT));
        delivery.put("audioOrigin", text(raw, "audioOrigin").toLowerCase(
                java.util.Locale.ROOT));
        putNullableText(delivery, "promptAudioReference",
                raw.get("promptAudioReference"));
        if (raw.has("promptPlayLimit") && !raw.get("promptPlayLimit").isNull()) {
            delivery.put("promptPlayLimit",
                    raw.path("promptPlayLimit").asInt(0));
        } else {
            delivery.putNull("promptPlayLimit");
        }
        delivery.put("preparationSeconds",
                raw.path("preparationSeconds").asInt(-1));
        delivery.put("responseSeconds",
                raw.path("responseSeconds").asInt(-1));
        return delivery;
    }

    private ObjectNode normalizeFilteredObject(
            JsonNode raw,
            Set<String> allowed,
            String path,
            List<ValidationIssue> issues) {
        if (!raw.isObject()) {
            issues.add(error(
                    "CANDIDATE_TYPED_OBJECT_INVALID", "FIELD", path,
                    "Dữ liệu typed phải là một JSON object."));
            return objectMapper.createObjectNode();
        }
        unknownFields(raw, allowed, path, issues);
        ObjectNode normalized = objectMapper.createObjectNode();
        allowed.stream().sorted().forEach(field -> {
            if (raw.has(field)) {
                normalized.set(field, normalizeDeepText(raw.get(field)));
            }
        });
        return normalized;
    }

    private void validateTypedContracts(
            ObjectNode question,
            ObjectNode contentNode,
            ObjectNode answerNode,
            String path,
            List<ValidationIssue> issues) {
        try {
            CanonicalQuestionType type = typeResolver.resolve(
                    question.path("questionType").asText());
            QuestionContent content = contractCodec.readQuestionContent(
                    candidateJson.write(contentNode), type);
            AnswerSpec answer = contractCodec.readAnswerSpec(
                    candidateJson.write(answerNode), content);
            if (answer.questionType() != type) {
                throw new IllegalArgumentException(
                        "Answer type does not match question type");
            }
        } catch (IllegalArgumentException exception) {
            issues.add(error(
                    "CANDIDATE_TYPED_CONTRACT_INVALID", "QUESTION", path,
                    "Nội dung hoặc đáp án typed của câu hỏi không hợp lệ."));
        }
    }

    private ArrayNode normalizeSourceRefs(
            JsonNode raw,
            String path,
            List<ValidationIssue> issues) {
        ArrayNode refs = objectMapper.createArrayNode();
        if (raw == null || raw.isMissingNode() || raw.isNull()) {
            return refs;
        }
        if (!raw.isArray()) {
            issues.add(error(
                    "SOURCE_REFERENCE_INVALID", "SOURCE", path,
                    "Source reference phải là một danh sách."));
            return refs;
        }
        for (int index = 0; index < raw.size(); index++) {
            JsonNode value = raw.get(index);
            if (!value.isObject()) {
                issues.add(error(
                        "SOURCE_REFERENCE_INVALID", "SOURCE",
                        path + "/" + index,
                        "Source reference phải là một JSON object."));
                continue;
            }
            unknownFields(value, SOURCE_REF_FIELDS,
                    path + "/" + index, issues);
            ObjectNode ref = refs.addObject();
            ref.put("kind", upper(text(value, "kind")));
            ref.put("sourceId", text(value, "sourceId"));
            copyTextIfPresent(value, ref, "sheet");
            copyIntegerIfPresent(value, ref, "row");
            copyTextIfPresent(value, ref, "column");
            copyIntegerIfPresent(value, ref, "pageNumber");
            copyIntegerIfPresent(value, ref, "start");
            copyIntegerIfPresent(value, ref, "end");
        }
        return refs;
    }

    private ObjectNode normalizeExplanationStrategy(JsonNode raw) {
        ObjectNode strategy = objectMapper.createObjectNode();
        strategy.put("registryVersion", text(raw, "registryVersion"));
        strategy.put("strategyCode", text(raw, "strategyCode"));
        strategy.put("strategyVersion", text(raw, "strategyVersion"));
        return strategy;
    }

    private JsonNode normalizeDeepText(JsonNode value) {
        if (value.isTextual()) {
            return objectMapper.getNodeFactory().textNode(
                    PracticeAuthoringCandidateJson.normalizedText(value.asText()));
        }
        if (value.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            value.forEach(item -> normalized.add(normalizeDeepText(item)));
            return normalized;
        }
        if (value.isObject()) {
            ObjectNode normalized = objectMapper.createObjectNode();
            value.fields().forEachRemaining(field -> normalized.set(
                    field.getKey(), normalizeDeepText(field.getValue())));
            return normalized;
        }
        return value.deepCopy();
    }

    private String stableId(
            String raw,
            String prefix,
            String seed,
            int index,
            String path,
            List<ValidationIssue> issues) {
        String normalized = PracticeAuthoringCandidateJson.normalizedText(raw);
        if (normalized.isBlank()) {
            String digest = candidateJson.digest(
                    objectMapper.getNodeFactory().textNode(
                            seed + ":" + prefix + ":" + index));
            return prefix + "_" + digest.substring(0, 24);
        }
        if (!normalized.matches("[A-Za-z0-9._-]{1,80}")) {
            issues.add(error(
                    "CANDIDATE_STABLE_ID_INVALID", "FIELD", path,
                    "ID ổn định chỉ được dùng chữ, số, dấu chấm, gạch dưới hoặc gạch ngang."));
        }
        return normalized;
    }

    private void unknownFields(
            JsonNode raw,
            Set<String> allowed,
            String path,
            List<ValidationIssue> issues) {
        if (raw == null || !raw.isObject()) return;
        Set<String> names = new HashSet<>();
        raw.fieldNames().forEachRemaining(names::add);
        names.removeAll(allowed);
        names.stream().sorted().forEach(name -> issues.add(error(
                "CANDIDATE_SCHEMA_FIELD_UNKNOWN", "FIELD",
                path + "/" + escapePointer(name),
                "Candidate chứa trường không được hỗ trợ: " + name + ".")));
    }

    private static ValidationIssue error(
            String code, String scope, String path, String message) {
        return ValidationIssue.error(
                code, scope, path, message, "EDIT_IN_REVIEW");
    }

    private static String text(JsonNode node, String field) {
        return text(node, field, "");
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.isObject()) return fallback;
        String value = PracticeAuthoringCandidateJson.normalizedText(
                node.path(field).asText(""));
        return value.isBlank() ? fallback : value;
    }

    private static String upper(String value) {
        return value.toUpperCase(java.util.Locale.ROOT);
    }

    private static void putNullableText(
            ObjectNode target, String field, JsonNode value) {
        if (value == null || value.isNull()) {
            target.putNull(field);
            return;
        }
        String normalized = PracticeAuthoringCandidateJson.normalizedText(
                value.asText(""));
        if (normalized.isBlank()) target.putNull(field);
        else target.put(field, normalized);
    }

    private static void copyNullableText(
            JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) {
            putNullableText(target, field, source.get(field));
        }
    }

    private static void copyTextIfPresent(
            JsonNode source, ObjectNode target, String field) {
        if (source.hasNonNull(field)) target.put(field, text(source, field));
    }

    private static void copyIntegerIfPresent(
            JsonNode source, ObjectNode target, String field) {
        if (source.has(field) && source.get(field).canConvertToInt()) {
            target.put(field, source.get(field).asInt());
        }
    }

    private static String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    public record NormalizationResult(
            ArrayNode groups,
            List<ValidationIssue> issues) {
        public NormalizationResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}
