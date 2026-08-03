package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strict server-side validation for provider authoring output. The provider's
 * JSON-schema mode is only the first boundary; this validator independently
 * rejects unknown vocabulary, evaluation data and unrequested evidence.
 */
@Service
public class PracticePdfAuthoringOutputValidator {

    private static final Set<String> ROOT_REQUIRED =
            PracticePdfAuthoringJsonContract.ROOT_FIELDS;
    private static final Set<String> GROUP_REQUIRED =
            PracticePdfAuthoringJsonContract.GROUP_FIELDS;
    private static final Set<String> STIMULUS_REQUIRED =
            PracticePdfAuthoringJsonContract.STIMULUS_FIELDS;
    private static final Set<String> QUESTION_REQUIRED = Set.of(
            "sourceQuestionId", "questionType", "prompt", "points",
            "questionContent", "answerSpec", "sourceRefs", "confidence");
    private static final Set<String> WARNING_REQUIRED =
            PracticePdfAuthoringJsonContract.WARNING_FIELDS;
    private static final Set<String> SOURCE_REF_REQUIRED = Set.of("kind", "sourceId");
    private static final Set<String> CONTENT_REQUIRED =
            Set.of("schemaVersion", "options", "blanks");
    private static final Set<String> OPTION_REQUIRED = Set.of("id", "text");
    private static final Set<String> CONTENT_BLANK_REQUIRED = Set.of("id", "prompt");
    private static final Set<String> SPEAKING_REQUIRED =
            PracticePdfAuthoringJsonContract.SPEAKING_FIELDS;
    private static final Set<String> WRITING_RESPONSE_REQUIRED =
            PracticePdfAuthoringJsonContract.WRITING_RESPONSE_FIELDS;
    private static final Set<String> WRITING_RESPONSE_BLANK_REQUIRED =
            PracticePdfAuthoringJsonContract.WRITING_RESPONSE_BLANK_FIELDS;
    private static final Set<String> ANSWER_REQUIRED = Set.of(
            "schemaVersion", "questionType", "correctOptionIds",
            "correctValue", "blanks", "scoringPolicyCode");
    private static final Set<String> ANSWER_BLANK_REQUIRED =
            PracticePdfAuthoringJsonContract.ANSWER_BLANK_FIELDS;
    private static final Set<String> WRITING_AUTHORITY_REQUIRED =
            PracticePdfAuthoringJsonContract.WRITING_AUTHORITY_FIELDS;
    private static final Set<String> WRITING_AUTHORITY_BLANK_REQUIRED =
            PracticePdfAuthoringJsonContract.WRITING_AUTHORITY_BLANK_FIELDS;
    private static final Set<String> WRITING_ACCEPTED_REQUIRED =
            Set.of("text", "equivalence", "evidenceIds");

    private final ObjectMapper objectMapper;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final PracticeContentRules contentRules;

    public PracticePdfAuthoringOutputValidator(
            ObjectMapper objectMapper,
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            PracticeContentRules contentRules) {
        this.objectMapper = objectMapper;
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.contentRules = contentRules;
    }

    public ValidatedOutput validate(
            JsonNode raw,
            PracticePdfAuthoringRequest request) {
        requireObject(raw, "");
        rejectForbiddenKeys(raw, "");
        requireVocabulary(raw, PracticePdfAuthoringJsonContract.ROOT_FIELDS,
                ROOT_REQUIRED, "");
        requireText(raw, "schemaVersion", 1, 100, "");
        requireText(raw, "operation", 1, 20, "");
        requireText(raw, "sourceDigest", 71, 71, "");
        if (!PracticePdfAuthoringJsonContract.SCHEMA_VERSION.equals(
                raw.path("schemaVersion").asText())
                || !request.operation().name().equals(
                raw.path("operation").asText())
                || !request.sourceDigest().equals(
                raw.path("sourceDigest").asText().toLowerCase(Locale.ROOT))) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "Phiên bản, operation hoặc source digest của phản hồi AI không khớp.");
        }

        Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence =
                evidenceById(request.evidence());
        Set<String> groupIds = new HashSet<>();
        Set<String> questionIds = new HashSet<>();
        JsonNode groups = requireArray(raw, "groups", 1, 100, "");
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            validateGroup(groups.get(groupIndex), groupIndex, request,
                    evidence, groupIds, questionIds);
        }
        JsonNode warnings = requireArray(raw, "warnings", 0, 200, "");
        for (int warningIndex = 0; warningIndex < warnings.size(); warningIndex++) {
            validateWarning(warnings.get(warningIndex), warningIndex, evidence);
        }
        return new ValidatedOutput((ObjectNode) raw.deepCopy());
    }

    private void validateGroup(
            JsonNode group,
            int groupIndex,
            PracticePdfAuthoringRequest request,
            Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence,
            Set<String> groupIds,
            Set<String> questionIds) {
        String path = "/groups/" + groupIndex;
        requireObject(group, path);
        requireVocabulary(group, PracticePdfAuthoringJsonContract.GROUP_FIELDS,
                GROUP_REQUIRED, path);
        String groupId = requireStableId(group, "sourceGroupId", path);
        if (!groupIds.add(groupId)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "sourceGroupId bị trùng.");
        }
        requireText(group, "label", 1, 255, path);
        requireText(group, "instruction", 0, 4000, path);
        validateSourceRefs(requireArray(group, "sourceRefs", 0, 200, path),
                path + "/sourceRefs", evidence);
        validateStimulus(group.path("stimulus"), path + "/stimulus",
                request.target().skill(), evidence);
        JsonNode questions = requireArray(group, "questions", 1, 200, path);
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            validateQuestion(questions.get(questionIndex), groupIndex,
                    questionIndex, request, evidence, questionIds);
        }
    }

    private void validateStimulus(
            JsonNode stimulus,
            String path,
            String targetSkill,
            Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence) {
        requireObject(stimulus, path);
        requireVocabulary(stimulus,
                PracticePdfAuthoringJsonContract.STIMULUS_FIELDS,
                STIMULUS_REQUIRED, path);
        String type = requireText(stimulus, "type", 1, 30, path);
        if (!Set.of("NONE", "READING_PASSAGE", "LISTENING_AUDIO").contains(type)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Stimulus type không hợp lệ.");
        }
        String passage = requireText(stimulus, "passageText", 0, 1_000_000, path);
        String transcript = requireText(stimulus, "transcriptText", 0, 1_000_000, path);
        boolean validForSkill = switch (targetSkill) {
            case "READING" -> ("NONE".equals(type) && passage.isBlank()
                    && transcript.isBlank())
                    || ("READING_PASSAGE".equals(type) && !passage.isBlank()
                    && transcript.isBlank());
            case "LISTENING" -> ("NONE".equals(type) && passage.isBlank()
                    && transcript.isBlank())
                    || ("LISTENING_AUDIO".equals(type) && passage.isBlank()
                    && !transcript.isBlank());
            case "WRITING", "SPEAKING" -> "NONE".equals(type)
                    && passage.isBlank() && transcript.isBlank();
            default -> false;
        };
        if (!validForSkill) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "Stimulus không phù hợp target skill.");
        }
        validateSourceRefs(requireArray(stimulus, "sourceRefs", 0, 200, path),
                path + "/sourceRefs", evidence);
    }

    private void validateQuestion(
            JsonNode question,
            int groupIndex,
            int questionIndex,
            PracticePdfAuthoringRequest request,
            Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence,
            Set<String> questionIds) {
        String path = "/groups/" + groupIndex + "/questions/" + questionIndex;
        requireObject(question, path);
        requireVocabulary(question,
                PracticePdfAuthoringJsonContract.QUESTION_FIELDS,
                QUESTION_REQUIRED, path);
        String questionId = requireStableId(question, "sourceQuestionId", path);
        if (!questionIds.add(questionId)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "sourceQuestionId bị trùng.");
        }
        String rawType = requireText(question, "questionType", 1, 40, path);
        CanonicalQuestionType type;
        try {
            type = typeResolver.resolve(rawType);
        } catch (IllegalArgumentException exception) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Question type không được hỗ trợ.");
            return;
        }
        AssessmentSkill skill;
        try {
            skill = AssessmentSkill.valueOf(request.target().skill());
        } catch (IllegalArgumentException exception) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Target skill không hợp lệ.");
            return;
        }
        if (!contentRules.allowedTypes(skill).contains(type)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "Question type không phù hợp target skill.");
        }
        requireText(question, "prompt", 1, 100_000, path);
        requireNumber(question, "points", 0, path);
        if (question.has("explanationVi")) {
            requireText(question, "explanationVi", 0, 100_000, path);
        }
        JsonNode confidence = question.path("confidence");
        if (!confidence.isNumber() || confidence.asDouble() < 0
                || confidence.asDouble() > 1) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Confidence phải nằm trong 0..1.");
        }
        validateSourceRefs(requireArray(question, "sourceRefs", 1, 200, path),
                path + "/sourceRefs", evidence);
        validateQuestionContent(question.path("questionContent"), path, request);
        validateAnswerSpec(question.path("answerSpec"), path, evidence);
        validateTyped(question, type);
        validateWritingAndSpeaking(question, type, request.target().skill());
    }

    private void validateQuestionContent(
            JsonNode content,
            String questionPath,
            PracticePdfAuthoringRequest request) {
        String path = questionPath + "/questionContent";
        requireObject(content, path);
        requireVocabulary(content, PracticePdfAuthoringJsonContract.CONTENT_FIELDS,
                CONTENT_REQUIRED, path);
        String version = requireText(content, "schemaVersion", 1, 50, path);
        if (!Set.of("question-content-v1", "question-content-v2",
                "question-content-v3").contains(version)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Question content version không hợp lệ.");
        }
        if ("question-content-v3".equals(version)) {
            String language = requireText(content, "languageTag", 2, 2, path);
            if (!Set.of("ko", "vi").contains(language)) {
                fail("PDF_AUTHORING_SCHEMA_INVALID", "languageTag không hợp lệ.");
            }
        } else if (content.has("languageTag")) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "languageTag chỉ thuộc question-content-v3.");
        }
        JsonNode options = requireArray(content, "options", 0, 8, path);
        Set<String> optionIds = new HashSet<>();
        for (int index = 0; index < options.size(); index++) {
            JsonNode option = options.get(index);
            String optionPath = path + "/options/" + index;
            requireObject(option, optionPath);
            requireVocabulary(option, PracticePdfAuthoringJsonContract.OPTION_FIELDS,
                    OPTION_REQUIRED, optionPath);
            if (!optionIds.add(requireStableId(option, "id", optionPath))) {
                fail("PDF_AUTHORING_SCHEMA_INVALID", "Option ID bị trùng.");
            }
            requireText(option, "text", 1, 10_000, optionPath);
            validateAssetReference(option.get("imageReference"), request);
        }
        JsonNode blanks = requireArray(content, "blanks", 0, 100, path);
        Set<String> blankIds = new HashSet<>();
        for (int index = 0; index < blanks.size(); index++) {
            JsonNode blank = blanks.get(index);
            String blankPath = path + "/blanks/" + index;
            requireObject(blank, blankPath);
            requireVocabulary(blank,
                    PracticePdfAuthoringJsonContract.CONTENT_BLANK_FIELDS,
                    CONTENT_BLANK_REQUIRED, blankPath);
            if (!blankIds.add(requireStableId(blank, "id", blankPath))) {
                fail("PDF_AUTHORING_SCHEMA_INVALID", "Blank ID bị trùng.");
            }
            requireText(blank, "prompt", 0, 10_000, blankPath);
        }
        validateAssetReference(content.get("imageReference"), request);
        requireNull(content.get("audioReference"),
                "PDF authoring không nhận audio reference từ provider.");
        if (content.has("speakingDelivery")) {
            validateSpeaking(content.path("speakingDelivery"), path + "/speakingDelivery");
        }
        if (content.has("writingResponse")) {
            validateWritingResponse(content.path("writingResponse"),
                    path + "/writingResponse");
        }
    }

    private void validateAnswerSpec(
            JsonNode answer,
            String questionPath,
            Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence) {
        String path = questionPath + "/answerSpec";
        requireObject(answer, path);
        requireVocabulary(answer, PracticePdfAuthoringJsonContract.ANSWER_FIELDS,
                ANSWER_REQUIRED, path);
        if (!"answer-spec-v1".equals(
                requireText(answer, "schemaVersion", 1, 50, path))) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Answer spec version không hợp lệ.");
        }
        requireText(answer, "questionType", 1, 40, path);
        requireArray(answer, "correctOptionIds", 0, 100, path)
                .forEach(value -> requireStableIdValue(value, path));
        requireNullableText(answer.get("correctValue"), 10_000, path);
        JsonNode blanks = requireArray(answer, "blanks", 0, 100, path);
        for (int index = 0; index < blanks.size(); index++) {
            JsonNode blank = blanks.get(index);
            String blankPath = path + "/blanks/" + index;
            requireObject(blank, blankPath);
            requireVocabulary(blank,
                    PracticePdfAuthoringJsonContract.ANSWER_BLANK_FIELDS,
                    ANSWER_BLANK_REQUIRED, blankPath);
            requireStableId(blank, "blankId", blankPath);
            JsonNode values = requireArray(blank, "acceptedValues", 1, 100, blankPath);
            for (JsonNode value : values) requireTextValue(value, 1, 10_000, blankPath);
        }
        String scoring = requireText(answer, "scoringPolicyCode", 1, 40, path);
        if (!Set.of("ALL_OR_NOTHING", "NORMALIZED_EXACT", "PROFILE_BASED")
                .contains(scoring)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Scoring policy không hợp lệ.");
        }
        if (answer.has("writingBlankAuthority")) {
            validateWritingAuthority(answer.path("writingBlankAuthority"),
                    path + "/writingBlankAuthority", evidence);
        }
    }

    private void validateSpeaking(JsonNode speaking, String path) {
        requireObject(speaking, path);
        requireVocabulary(speaking, PracticePdfAuthoringJsonContract.SPEAKING_FIELDS,
                SPEAKING_REQUIRED, path);
        if (!"manual_text".equals(requireText(speaking, "inputType", 1, 30, path))
                || !"text_only".equals(requireText(
                speaking, "deliveryMode", 1, 30, path))
                || !"none".equals(requireText(speaking, "audioOrigin", 1, 30, path))) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "Speaking authoring chỉ hỗ trợ manual_text + text_only + none.");
        }
        requireNull(speaking.get("promptAudioReference"),
                "Speaking authoring không nhận prompt audio.");
        requireNull(speaking.get("promptPlayLimit"),
                "Speaking text-only không nhận play limit.");
        requireInteger(speaking, "preparationSeconds", 0, 600, path);
        requireInteger(speaking, "responseSeconds", 1, 1800, path);
    }

    private void validateWritingResponse(JsonNode response, String path) {
        requireObject(response, path);
        requireVocabulary(response,
                PracticePdfAuthoringJsonContract.WRITING_RESPONSE_FIELDS,
                WRITING_RESPONSE_REQUIRED, path);
        if (!"writing-blanks.v1".equals(requireText(
                response, "responseSchemaVersion", 1, 50, path))
                || !"STRUCTURED_BLANKS".equals(requireText(
                response, "responseMode", 1, 50, path))) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Writing response contract không hợp lệ.");
        }
        requireQ51Q52(response, "taskType", path);
        JsonNode blanks = requireArray(response, "blanks", 2, 2, path);
        for (int index = 0; index < blanks.size(); index++) {
            JsonNode blank = blanks.get(index);
            String blankPath = path + "/blanks/" + index;
            requireObject(blank, blankPath);
            requireVocabulary(blank,
                    PracticePdfAuthoringJsonContract.WRITING_RESPONSE_BLANK_FIELDS,
                    WRITING_RESPONSE_BLANK_REQUIRED, blankPath);
            requireStableId(blank, "blankId", blankPath);
            requireInteger(blank, "ordinal", 1, 2, blankPath);
            requireText(blank, "context", 1, 1000, blankPath);
        }
    }

    private void validateWritingAuthority(
            JsonNode authority,
            String path,
            Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence) {
        requireObject(authority, path);
        requireVocabulary(authority,
                PracticePdfAuthoringJsonContract.WRITING_AUTHORITY_FIELDS,
                WRITING_AUTHORITY_REQUIRED, path);
        if (!"writing-blank-authority.v1".equals(requireText(
                authority, "contractVersion", 1, 50, path))
                || !"NFC".equals(requireText(authority, "normalization", 1, 20, path))
                || !"TRIM_COLLAPSE".equals(requireText(
                authority, "whitespacePolicy", 1, 30, path))) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Writing blank authority không hợp lệ.");
        }
        requireQ51Q52(authority, "taskType", path);
        JsonNode blanks = requireArray(authority, "blanks", 2, 2, path);
        for (int index = 0; index < blanks.size(); index++) {
            JsonNode blank = blanks.get(index);
            String blankPath = path + "/blanks/" + index;
            requireObject(blank, blankPath);
            requireVocabulary(blank,
                    PracticePdfAuthoringJsonContract.WRITING_AUTHORITY_BLANK_FIELDS,
                    WRITING_AUTHORITY_BLANK_REQUIRED, blankPath);
            requireStableId(blank, "blankId", blankPath);
            requireInteger(blank, "ordinal", 1, 2, blankPath);
            JsonNode accepted = requireArray(
                    blank, "acceptedAnswers", 1, 100, blankPath);
            for (int answerIndex = 0; answerIndex < accepted.size(); answerIndex++) {
                JsonNode value = accepted.get(answerIndex);
                String valuePath = blankPath + "/acceptedAnswers/" + answerIndex;
                requireObject(value, valuePath);
                requireVocabulary(value,
                        PracticePdfAuthoringJsonContract.WRITING_ACCEPTED_FIELDS,
                        WRITING_ACCEPTED_REQUIRED, valuePath);
                requireText(value, "text", 1, 10_000, valuePath);
                if (!"EXACT".equals(requireText(
                        value, "equivalence", 1, 20, valuePath))) {
                    fail("PDF_AUTHORING_SCHEMA_INVALID",
                            "Writing accepted answer chỉ hỗ trợ EXACT.");
                }
                requireNullableText(value.get("reason"), 1000, valuePath);
                requireArray(value, "evidenceIds", 0, 100, valuePath)
                        .forEach(item -> {
                            requireStableIdValue(item, valuePath);
                            if (!evidence.containsKey(item.asText())) {
                                fail("PDF_SOURCE_REFERENCE_UNKNOWN",
                                        "Writing accepted answer tham chiếu evidence không có trong request.");
                            }
                        });
            }
        }
    }

    private void validateTyped(JsonNode question, CanonicalQuestionType type) {
        try {
            QuestionContent content = contractCodec.readQuestionContent(
                    objectMapper.writeValueAsString(question.path("questionContent")), type);
            AnswerSpec answer = contractCodec.readAnswerSpec(
                    objectMapper.writeValueAsString(question.path("answerSpec")), content);
            if (answer.questionType() != type) {
                throw new IllegalArgumentException("question type mismatch");
            }
        } catch (Exception exception) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "questionContent hoặc answerSpec không đúng canonical contract.");
        }
    }

    private void validateWritingAndSpeaking(
            JsonNode question,
            CanonicalQuestionType type,
            String skill) {
        if (type == CanonicalQuestionType.ESSAY) {
            String task = requireText(question, "essayTaskType", 3, 3, "question");
            if (!Set.of("Q51", "Q52", "Q53", "Q54").contains(task)) {
                fail("PDF_AUTHORING_SCHEMA_INVALID", "Writing task không hợp lệ.");
            }
            boolean structured = question.path("questionContent")
                    .path("writingResponse").isObject()
                    && question.path("answerSpec")
                    .path("writingBlankAuthority").isObject();
            if (("Q51".equals(task) || "Q52".equals(task)) != structured) {
                fail("PDF_AUTHORING_SCHEMA_INVALID",
                        "Writing Q51/Q52 phải có đúng structured blank authority.");
            }
        } else if (question.has("essayTaskType")) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "essayTaskType chỉ thuộc câu Writing ESSAY.");
        }
        if (type == CanonicalQuestionType.SPEAKING
                && !question.path("questionContent").path("speakingDelivery").isObject()) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "Speaking thiếu canonical delivery.");
        }
        if (type != CanonicalQuestionType.SPEAKING
                && question.path("questionContent").has("speakingDelivery")) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "speakingDelivery chỉ thuộc câu Speaking.");
        }
        if ((type == CanonicalQuestionType.ESSAY) != "WRITING".equals(skill)
                || (type == CanonicalQuestionType.SPEAKING) != "SPEAKING".equals(skill)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Question type và skill không khớp.");
        }
    }

    private void validateWarning(
            JsonNode warning,
            int index,
            Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence) {
        String path = "/warnings/" + index;
        requireObject(warning, path);
        requireVocabulary(warning, PracticePdfAuthoringJsonContract.WARNING_FIELDS,
                WARNING_REQUIRED, path);
        String code = requireText(warning, "code", 3, 100, path);
        if (!code.matches("[A-Z][A-Z0-9_]{2,100}")) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Warning code không hợp lệ.");
        }
        requireText(warning, "messageVi", 1, 2000, path);
        validateSourceRefs(requireArray(warning, "sourceRefs", 0, 200, path),
                path + "/sourceRefs", evidence);
    }

    private void validateSourceRefs(
            JsonNode refs,
            String path,
            Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidence) {
        for (int index = 0; index < refs.size(); index++) {
            JsonNode ref = refs.get(index);
            String refPath = path + "/" + index;
            requireObject(ref, refPath);
            requireVocabulary(ref, PracticePdfAuthoringJsonContract.SOURCE_REF_FIELDS,
                    SOURCE_REF_REQUIRED, refPath);
            String kind = requireText(ref, "kind", 1, 20, refPath);
            String sourceId = requireText(ref, "sourceId", 1, 200, refPath);
            PracticePdfAuthoringRequest.SourceEvidence requested = evidence.get(sourceId);
            if (requested == null || !kind.equals(requested.kind())) {
                fail("PDF_SOURCE_REFERENCE_UNKNOWN",
                        "AI đã tham chiếu evidence không có trong request.");
            }
            if ("TEXT_SPAN".equals(kind)) {
                int start = requireInteger(ref, "start", 0,
                        requested.textLength(), refPath);
                int end = requireInteger(ref, "end", start,
                        requested.textLength(), refPath);
                if (end < start) {
                    fail("PDF_SOURCE_REFERENCE_UNKNOWN", "Text span không hợp lệ.");
                }
                if (ref.has("pageNumber")
                        && !matchesPage(ref.path("pageNumber"), requested.pageNumber())) {
                    fail("PDF_SOURCE_REFERENCE_UNKNOWN", "Text span page không khớp.");
                }
            } else {
                int page = requireInteger(ref, "pageNumber", 1,
                        Integer.MAX_VALUE, refPath);
                if (requested.pageNumber() == null
                        || requested.pageNumber() != page
                        || ref.has("start") || ref.has("end")) {
                    fail("PDF_SOURCE_REFERENCE_UNKNOWN", "Page reference không khớp.");
                }
            }
        }
    }

    private static Map<String, PracticePdfAuthoringRequest.SourceEvidence> evidenceById(
            List<PracticePdfAuthoringRequest.SourceEvidence> evidence) {
        Map<String, PracticePdfAuthoringRequest.SourceEvidence> result = new HashMap<>();
        for (PracticePdfAuthoringRequest.SourceEvidence item : evidence) {
            if (result.put(item.sourceId(), item) != null) {
                fail("PDF_AUTHORING_SCHEMA_INVALID", "Evidence ID trong request bị trùng.");
            }
        }
        return result;
    }

    private static void rejectForbiddenKeys(JsonNode node, String path) {
        if (node == null) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey().replaceAll("[^A-Za-z0-9]", "")
                        .toLowerCase(Locale.ROOT);
                if (PracticePdfAuthoringJsonContract.FORBIDDEN_NORMALIZED_KEYS
                        .contains(normalized)) {
                    fail("PDF_AUTHORING_SCHEMA_INVALID",
                            "Phản hồi authoring chứa trường evaluation/result bị cấm tại "
                                    + path + "/" + field.getKey() + ".");
                }
                rejectForbiddenKeys(field.getValue(), path + "/" + field.getKey());
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                rejectForbiddenKeys(node.get(index), path + "/" + index);
            }
        }
    }

    private static void requireVocabulary(
            JsonNode node,
            Set<String> allowed,
            Set<String> required,
            String path) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        Set<String> unknown = new HashSet<>(actual);
        unknown.removeAll(allowed);
        if (!unknown.isEmpty() || !actual.containsAll(required)) {
            fail("PDF_AUTHORING_SCHEMA_INVALID",
                    "Thiếu hoặc thừa trường JSON tại " + (path.isBlank() ? "/" : path) + ".");
        }
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "JSON object không hợp lệ tại " + path + ".");
        }
    }

    private static JsonNode requireArray(
            JsonNode node, String field, int min, int max, String path) {
        JsonNode value = node.path(field);
        if (!value.isArray() || value.size() < min || value.size() > max) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "JSON array không hợp lệ tại "
                    + path + "/" + field + ".");
        }
        return value;
    }

    private static String requireText(
            JsonNode node, String field, int min, int max, String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Text field không hợp lệ tại "
                    + path + "/" + field + ".");
        }
        String text = PracticePdfAuthoringRequest.normalize(value.asText());
        if (text.length() < min || text.length() > max) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Độ dài field không hợp lệ tại "
                    + path + "/" + field + ".");
        }
        return text;
    }

    private static void requireTextValue(
            JsonNode value, int min, int max, String path) {
        if (value == null || !value.isTextual()) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Text value không hợp lệ tại " + path + ".");
        }
        int length = PracticePdfAuthoringRequest.normalize(value.asText()).length();
        if (length < min || length > max) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Độ dài text value không hợp lệ.");
        }
    }

    private static String requireStableId(JsonNode node, String field, String path) {
        String value = requireText(node, field, 1, 80, path);
        if (!value.matches("[A-Za-z0-9._-]{1,80}")) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Stable ID không hợp lệ.");
        }
        return value;
    }

    private static void requireStableIdValue(JsonNode value, String path) {
        requireTextValue(value, 1, 80, path);
        if (!value.asText().matches("[A-Za-z0-9._-]{1,80}")) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Stable ID không hợp lệ.");
        }
    }

    private static void requireNumber(
            JsonNode node, String field, double exclusiveMin, String path) {
        JsonNode value = node.path(field);
        if (!value.isNumber() || !Double.isFinite(value.asDouble())
                || value.asDouble() <= exclusiveMin) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Number field không hợp lệ tại "
                    + path + "/" + field + ".");
        }
    }

    private static int requireInteger(
            JsonNode node, String field, int min, int max, String path) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.asInt() < min || value.asInt() > max) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Integer field không hợp lệ tại "
                    + path + "/" + field + ".");
        }
        return value.asInt();
    }

    private static void requireNullableText(JsonNode value, int max, String path) {
        if (value == null || value.isNull()) return;
        if (!value.isTextual()
                || PracticePdfAuthoringRequest.normalize(value.asText()).length() > max) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Nullable text không hợp lệ tại " + path + ".");
        }
    }

    private static void requireNull(JsonNode value, String message) {
        if (value != null && !value.isNull()) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", message);
        }
    }

    private static void requireQ51Q52(JsonNode node, String field, String path) {
        if (!Set.of("Q51", "Q52").contains(requireText(node, field, 3, 3, path))) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Writing structured task phải là Q51/Q52.");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateAssetReference(
            JsonNode value,
            PracticePdfAuthoringRequest request) {
        if (value == null || value.isNull()) return;
        if (!value.isTextual() || value.asText().isBlank()) {
            fail("PDF_AUTHORING_SCHEMA_INVALID", "Asset reference không hợp lệ.");
        }
        Object raw = request.sourceContext().get("assetReferences");
        Map<String, String> allowed = raw instanceof Map<?, ?> map
                ? (Map<String, String>) map : Map.of();
        if (!allowed.containsKey(value.asText())) {
            fail("PDF_SOURCE_REFERENCE_UNKNOWN",
                    "AI đã trả asset reference không có trong request.");
        }
    }

    private static boolean matchesPage(JsonNode value, Integer requested) {
        return requested != null && value.isIntegralNumber()
                && value.asInt() == requested;
    }

    private static void fail(String code, String message) {
        throw new PracticeAuthoringCandidateException(code, message);
    }

    public record ValidatedOutput(ObjectNode root) {
        public ValidatedOutput {
            root = root.deepCopy();
        }

        @Override
        public ObjectNode root() {
            return root.deepCopy();
        }
    }
}
