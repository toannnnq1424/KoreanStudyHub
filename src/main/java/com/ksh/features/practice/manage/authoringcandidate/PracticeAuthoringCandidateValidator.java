package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ValidationIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PracticeAuthoringCandidateValidator {

    private static final Pattern STABLE_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,80}");
    private static final Pattern KOREAN = Pattern.compile(".*[가-힣].*", Pattern.DOTALL);
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final PracticeAuthoringCandidateJson candidateJson;

    public PracticeAuthoringCandidateValidator(
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            PracticeAuthoringCandidateJson candidateJson) {
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.candidateJson = candidateJson;
    }

    public ValidationResult validate(
            SourceKind sourceKind,
            TargetRoute target,
            JsonNode groups,
            List<ValidationIssue> normalizationIssues) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (normalizationIssues != null) issues.addAll(normalizationIssues);
        if (groups == null || !groups.isArray() || groups.isEmpty()) {
            issues.add(error(
                    "CANDIDATE_GROUPS_REQUIRED", "CANDIDATE", "/groups",
                    "Candidate phải có ít nhất một nhóm câu hỏi.",
                    "EDIT_IN_REVIEW"));
            return result(issues);
        }

        validateTarget(target, issues);
        Set<String> groupIds = new HashSet<>();
        Set<Integer> groupOrders = new HashSet<>();
        Set<String> questionIds = new HashSet<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            JsonNode group = groups.get(groupIndex);
            String groupPath = "/groups/" + groupIndex;
            String groupId = group.path("candidateGroupId").asText("");
            requireStableId(groupId, groupPath + "/candidateGroupId", issues);
            if (!groupIds.add(groupId)) {
                issues.add(error(
                        "CANDIDATE_GROUP_ID_DUPLICATED", "GROUP",
                        groupPath + "/candidateGroupId",
                        "ID nhóm candidate bị trùng.", "EDIT_IN_REVIEW"));
            }
            int groupOrder = group.path("groupOrder").asInt(0);
            if (groupOrder < 1 || !groupOrders.add(groupOrder)) {
                issues.add(error(
                        "CANDIDATE_GROUP_ORDER_INVALID", "GROUP",
                        groupPath + "/groupOrder",
                        "Thứ tự nhóm phải là số dương và không trùng.",
                        "EDIT_IN_REVIEW"));
            }
            if (group.path("label").asText("").isBlank()) {
                issues.add(error(
                        "CANDIDATE_GROUP_LABEL_REQUIRED", "GROUP",
                        groupPath + "/label",
                        "Nhóm candidate phải có nhãn.", "EDIT_IN_REVIEW"));
            }
            requireMaximumLength(group.path("label").asText(""), 255,
                    groupPath + "/label", issues);
            requireMaximumLength(group.path("instruction").asText(""), 4000,
                    groupPath + "/instruction", issues);
            validateSourceRefs(sourceKind, group.path("sourceRefs"),
                    groupPath + "/sourceRefs", issues);
            validateStimulus(sourceKind, target, group, groupPath, issues);
            validateQuestions(
                    sourceKind, target, group, groupIndex,
                    questionIds, issues);
        }
        return result(issues);
    }

    private void validateTarget(TargetRoute target, List<ValidationIssue> issues) {
        if (target == null || target.draftId() == null || target.testNo() < 1) {
            issues.add(error(
                    "CANDIDATE_TARGET_INVALID", "TARGET", "/target",
                    "Target candidate không hợp lệ.", "REOPEN_TARGET"));
            return;
        }
        String skill = upper(target.skill());
        String expectedPrefix = switch (skill) {
            case "READING" -> "R";
            case "LISTENING" -> "L";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> "";
        };
        if (expectedPrefix.isBlank()
                || !target.lessonCode().equals(expectedPrefix + target.testNo())) {
            issues.add(error(
                    "CANDIDATE_TARGET_IDENTITY_MISMATCH", "TARGET",
                    "/target/lessonCode",
                    "Skill, Test và lessonCode của target không khớp.",
                    "REOPEN_TARGET"));
        }
    }

    private void validateStimulus(
            SourceKind sourceKind,
            TargetRoute target,
            JsonNode group,
            String path,
            List<ValidationIssue> issues) {
        JsonNode stimulus = group.path("stimulus");
        String type = stimulus.path("type").asText("NONE");
        String skill = upper(target.skill());
        requireMaximumLength(stimulus.path("instruction").asText(""), 4000,
                path + "/stimulus/instruction", issues);
        requireMaximumLength(stimulus.path("passageText").asText(""), 1_000_000,
                path + "/stimulus/passageText", issues);
        requireMaximumLength(stimulus.path("transcriptText").asText(""), 1_000_000,
                path + "/stimulus/transcriptText", issues);
        requireMaximumLength(stimulus.path("mediaReference").asText(""), 512,
                path + "/stimulus/mediaReference", issues);
        validateSourceRefs(sourceKind,
                stimulus.path("provenance").path("sourceRefs"),
                path + "/stimulus/provenance/sourceRefs", issues);
        if (!Set.of("NONE", "READING_PASSAGE", "LISTENING_AUDIO")
                .contains(type)) {
            issues.add(error(
                    "CANDIDATE_STIMULUS_TYPE_INVALID", "FIELD",
                    path + "/stimulus/type",
                    "Loại stimulus không hợp lệ.", "EDIT_IN_REVIEW"));
        }
        if (!sourceKind.name().equals(stimulus.path("provenance")
                .path("source").asText())) {
            issues.add(error(
                    "CANDIDATE_SOURCE_PROVENANCE_MISMATCH", "SOURCE",
                    path + "/stimulus/provenance/source",
                    "Provenance không khớp source candidate.",
                    "FIX_SOURCE"));
        }
        if (!"NONE".equals(type)
                && !stimulus.path("provenance").path("approved")
                .asBoolean(false)) {
            issues.add(error(
                    "STIMULUS_REVIEW_REQUIRED", "GROUP",
                    path + "/stimulus/provenance/approved",
                    "Giảng viên phải xác nhận stimulus trước khi áp dụng.",
                    "EDIT_IN_REVIEW"));
        }
        if ("READING".equals(skill)
                && "READING_PASSAGE".equals(type)
                && stimulus.path("passageText").asText("").isBlank()) {
            issues.add(error(
                    "READING_PASSAGE_REQUIRED", "FIELD",
                    path + "/stimulus/passageText",
                    "Nhóm Reading passage phải có nội dung nguồn.",
                    "EDIT_IN_REVIEW"));
        }
        if ("LISTENING".equals(skill)
                && "LISTENING_AUDIO".equals(type)
                && stimulus.path("transcriptText").asText("").isBlank()) {
            issues.add(error(
                    "LISTENING_TRANSCRIPT_REQUIRED", "FIELD",
                    path + "/stimulus/transcriptText",
                    "Nhóm Listening phải có transcript được duyệt.",
                    "EDIT_IN_REVIEW"));
        }
        if (sourceKind == SourceKind.QUICK_EXCEL) {
            validateQuickStimulus(target, stimulus, path, issues);
        }
    }

    private void validateQuickStimulus(
            TargetRoute target,
            JsonNode stimulus,
            String path,
            List<ValidationIssue> issues) {
        String skill = upper(target.skill());
        String type = stimulus.path("type").asText("NONE");
        if (stimulus.hasNonNull("mediaReference")) {
            issues.add(advanced(
                    "ADVANCED_AUTHORING_REQUIRED",
                    path + "/stimulus/mediaReference",
                    "Quick Excel không hỗ trợ media reference."));
        }
        if ("READING".equals(skill) && !"READING_PASSAGE".equals(type)) {
            issues.add(error(
                    "QUICK_READING_STIMULUS_INVALID", "GROUP",
                    path + "/stimulus/type",
                    "Quick Reading phải dùng passage text.",
                    "FIX_SOURCE"));
        } else if ("LISTENING".equals(skill)
                && !"LISTENING_AUDIO".equals(type)) {
            issues.add(error(
                    "QUICK_LISTENING_STIMULUS_INVALID", "GROUP",
                    path + "/stimulus/type",
                    "Quick Listening phải dùng transcript text.",
                    "FIX_SOURCE"));
        } else if (("WRITING".equals(skill) || "SPEAKING".equals(skill))
                && !"NONE".equals(type)) {
            issues.add(advanced(
                    "ADVANCED_AUTHORING_REQUIRED", path + "/stimulus/type",
                    "Quick Writing/Speaking không hỗ trợ stimulus dùng chung."));
        }
    }

    private void validateQuestions(
            SourceKind sourceKind,
            TargetRoute target,
            JsonNode group,
            int groupIndex,
            Set<String> questionIds,
            List<ValidationIssue> issues) {
        JsonNode questions = group.path("questions");
        if (!questions.isArray() || questions.isEmpty()) {
            issues.add(error(
                    "CANDIDATE_QUESTIONS_REQUIRED", "GROUP",
                    "/groups/" + groupIndex + "/questions",
                    "Nhóm candidate phải có ít nhất một câu hỏi.",
                    "EDIT_IN_REVIEW"));
            return;
        }
        Set<Integer> questionOrders = new HashSet<>();
        for (int questionIndex = 0;
             questionIndex < questions.size(); questionIndex++) {
            JsonNode question = questions.get(questionIndex);
            String path = "/groups/" + groupIndex
                    + "/questions/" + questionIndex;
            String id = question.path("candidateQuestionId").asText("");
            requireStableId(id, path + "/candidateQuestionId", issues);
            if (!questionIds.add(id)) {
                issues.add(error(
                        "CANDIDATE_QUESTION_ID_DUPLICATED", "QUESTION",
                        path + "/candidateQuestionId",
                        "ID câu hỏi candidate bị trùng.",
                        "EDIT_IN_REVIEW"));
            }
            int order = question.path("questionOrder").asInt(0);
            if (order < 1 || !questionOrders.add(order)) {
                issues.add(error(
                        "CANDIDATE_QUESTION_ORDER_INVALID", "QUESTION",
                        path + "/questionOrder",
                        "Thứ tự câu hỏi phải là số dương và không trùng trong nhóm.",
                        "EDIT_IN_REVIEW"));
            }
            validateSourceRefs(sourceKind, question.path("sourceRefs"),
                    path + "/sourceRefs", issues);
            validateQuestion(
                    sourceKind, target, group.path("stimulus"),
                    question, path, issues);
        }
    }

    private void validateQuestion(
            SourceKind sourceKind,
            TargetRoute target,
            JsonNode groupStimulus,
            JsonNode question,
            String path,
            List<ValidationIssue> issues) {
        CanonicalQuestionType type;
        try {
            type = typeResolver.resolve(question.path("questionType").asText());
        } catch (IllegalArgumentException exception) {
            issues.add(error(
                    "QUESTION_TYPE_UNKNOWN", "QUESTION",
                    path + "/questionType",
                    "Loại câu hỏi không được hỗ trợ.", "FIX_SOURCE"));
            return;
        }
        if (!allowedForSkill(type, upper(target.skill()))) {
            issues.add(error(
                    "QUESTION_TYPE_NOT_ALLOWED_FOR_SKILL", "QUESTION",
                    path + "/questionType",
                    "Loại câu hỏi không phù hợp kỹ năng target.",
                    "EDIT_IN_REVIEW"));
        }
        if (question.path("prompt").asText("").isBlank()) {
            issues.add(error(
                    "QUESTION_PROMPT_REQUIRED", "FIELD",
                    path + "/prompt", "Câu hỏi phải có prompt.",
                    "EDIT_IN_REVIEW"));
        }
        requireMaximumLength(question.path("prompt").asText(""), 100_000,
                path + "/prompt", issues);
        requireMaximumLength(question.path("explanationVi").asText(""), 100_000,
                path + "/explanationVi", issues);
        if (!question.path("points").isNumber()
                || question.path("points").asDouble() <= 0) {
            issues.add(error(
                    "QUESTION_POINTS_INVALID", "FIELD", path + "/points",
                    "Điểm câu hỏi phải lớn hơn 0.", "EDIT_IN_REVIEW"));
        }
        if (!"ACCEPTED".equals(question.path("reviewState").asText())) {
            issues.add(error(
                    "QUESTION_REVIEW_REQUIRED", "QUESTION",
                    path + "/reviewState",
                    "Mọi câu hỏi còn lại phải được chấp nhận trước khi áp dụng.",
                    "EDIT_IN_REVIEW"));
        }
        validateTyped(question, type, path, issues);
        validateTypedBounds(question, path, issues);
        if (("READING".equals(upper(target.skill()))
                || "LISTENING".equals(upper(target.skill())))) {
            validateExplanationStrategy(
                    groupStimulus, question, type, path, issues);
        }
        if (sourceKind == SourceKind.QUICK_EXCEL) {
            validateQuickQuestion(target, question, type, path, issues);
        }
    }

    private void validateTyped(
            JsonNode question,
            CanonicalQuestionType type,
            String path,
            List<ValidationIssue> issues) {
        try {
            QuestionContent content = contractCodec.readQuestionContent(
                    candidateJson.write(question.path("questionContent")), type);
            AnswerSpec answer = contractCodec.readAnswerSpec(
                    candidateJson.write(question.path("answerSpec")), content);
            if (answer.questionType() != type) {
                throw new IllegalArgumentException("Question type mismatch");
            }
        } catch (IllegalArgumentException exception) {
            issues.add(error(
                    "CANDIDATE_TYPED_CONTRACT_INVALID", "QUESTION", path,
                    "Nội dung hoặc đáp án typed của câu hỏi không hợp lệ.",
                    "EDIT_IN_REVIEW"));
        }
    }

    private void validateTypedBounds(
            JsonNode question,
            String path,
            List<ValidationIssue> issues) {
        JsonNode content = question.path("questionContent");
        JsonNode options = content.path("options");
        if (options.isArray() && options.size() > 8) {
            issues.add(error(
                    "CANDIDATE_FIELD_LIMIT_EXCEEDED", "FIELD",
                    path + "/questionContent/options",
                    "Câu hỏi có quá nhiều lựa chọn.", "EDIT_IN_REVIEW"));
        }
        for (int index = 0; index < options.size(); index++) {
            JsonNode option = options.get(index);
            String optionPath = path + "/questionContent/options/" + index;
            requireStableId(option.path("id").asText(""),
                    optionPath + "/id", issues);
            requireNonBlankBounded(option.path("text").asText(""), 10_000,
                    optionPath + "/text", issues);
            requireMaximumLength(option.path("imageReference").asText(""), 512,
                    optionPath + "/imageReference", issues);
        }
        JsonNode blanks = content.path("blanks");
        for (int index = 0; index < blanks.size(); index++) {
            JsonNode blank = blanks.get(index);
            String blankPath = path + "/questionContent/blanks/" + index;
            requireStableId(blank.path("id").asText(""),
                    blankPath + "/id", issues);
            requireMaximumLength(blank.path("prompt").asText(""), 10_000,
                    blankPath + "/prompt", issues);
        }
        requireMaximumLength(content.path("imageReference").asText(""), 512,
                path + "/questionContent/imageReference", issues);
        requireMaximumLength(content.path("audioReference").asText(""), 512,
                path + "/questionContent/audioReference", issues);

        JsonNode answer = question.path("answerSpec");
        Set<String> correctIds = new HashSet<>();
        for (int index = 0; index < answer.path("correctOptionIds").size(); index++) {
            String id = answer.path("correctOptionIds").get(index).asText("");
            requireStableId(id,
                    path + "/answerSpec/correctOptionIds/" + index, issues);
            if (!correctIds.add(id)) {
                issues.add(error(
                        "CANDIDATE_TYPED_ID_DUPLICATED", "FIELD",
                        path + "/answerSpec/correctOptionIds/" + index,
                        "ID đáp án đúng bị trùng.", "EDIT_IN_REVIEW"));
            }
        }
        requireMaximumLength(answer.path("correctValue").asText(""), 10_000,
                path + "/answerSpec/correctValue", issues);
        for (int index = 0; index < answer.path("blanks").size(); index++) {
            JsonNode blank = answer.path("blanks").get(index);
            String blankPath = path + "/answerSpec/blanks/" + index;
            requireStableId(blank.path("blankId").asText(""),
                    blankPath + "/blankId", issues);
            Set<String> values = new HashSet<>();
            for (int valueIndex = 0;
                 valueIndex < blank.path("acceptedValues").size(); valueIndex++) {
                String value = blank.path("acceptedValues")
                        .get(valueIndex).asText("");
                requireNonBlankBounded(value, 10_000,
                        blankPath + "/acceptedValues/" + valueIndex, issues);
                if (!values.add(value)) {
                    issues.add(error(
                            "CANDIDATE_TYPED_VALUE_DUPLICATED", "FIELD",
                            blankPath + "/acceptedValues/" + valueIndex,
                            "Giá trị đáp án blank bị trùng.", "EDIT_IN_REVIEW"));
                }
            }
        }
    }

    private void validateSourceRefs(
            SourceKind sourceKind,
            JsonNode refs,
            String path,
            List<ValidationIssue> issues) {
        if (!refs.isArray()) {
            issues.add(error(
                    "SOURCE_REFERENCE_INVALID", "SOURCE", path,
                    "Source reference phải là một danh sách.", "FIX_SOURCE"));
            return;
        }
        Set<String> allowedKinds = sourceKind == SourceKind.PDF_AI
                ? Set.of("TEXT_SPAN", "PAGE", "REGION")
                : Set.of("SHEET_ROW");
        for (int index = 0; index < refs.size(); index++) {
            JsonNode ref = refs.get(index);
            String refPath = path + "/" + index;
            String kind = ref.path("kind").asText("");
            if (!allowedKinds.contains(kind)) {
                issues.add(error(
                        "SOURCE_REFERENCE_KIND_MISMATCH", "SOURCE",
                        refPath + "/kind",
                        "Source reference không thuộc loại source của candidate.",
                        "FIX_SOURCE"));
            }
            requireNonBlankBounded(ref.path("sourceId").asText(""), 200,
                    refPath + "/sourceId", issues);
            requireMaximumLength(ref.path("sheet").asText(""), 100,
                    refPath + "/sheet", issues);
            requireMaximumLength(ref.path("column").asText(""), 100,
                    refPath + "/column", issues);
            if ("SHEET_ROW".equals(kind)
                    && (ref.path("sheet").asText("").isBlank()
                    || !ref.path("row").canConvertToInt()
                    || ref.path("row").asInt() < 1)) {
                issues.add(error(
                        "SOURCE_REFERENCE_LOCATION_INVALID", "SOURCE", refPath,
                        "Source row phải có sheet và số dòng dương.", "FIX_SOURCE"));
            }
            if (("PAGE".equals(kind) || "REGION".equals(kind))
                    && (!ref.path("pageNumber").canConvertToInt()
                    || ref.path("pageNumber").asInt() < 1)) {
                issues.add(error(
                        "SOURCE_REFERENCE_LOCATION_INVALID", "SOURCE", refPath,
                        "Source page/region phải có số trang dương.", "FIX_SOURCE"));
            }
            if ("TEXT_SPAN".equals(kind)
                    && (!ref.path("start").canConvertToInt()
                    || !ref.path("end").canConvertToInt()
                    || ref.path("start").asInt() < 0
                    || ref.path("end").asInt() < ref.path("start").asInt())) {
                issues.add(error(
                        "SOURCE_REFERENCE_LOCATION_INVALID", "SOURCE", refPath,
                        "Text span phải có offset đầu/cuối hợp lệ.", "FIX_SOURCE"));
            }
        }
    }

    private void validateExplanationStrategy(
            JsonNode groupStimulus,
            JsonNode question,
            CanonicalQuestionType type,
            String path,
            List<ValidationIssue> issues) {
        try {
            JsonNode strategy = question.path("explanationStrategy");
            ObjectiveExplanationStrategyRegistry.Selection selection =
                    ObjectiveExplanationStrategyRegistry.requireSelection(
                            type,
                            strategy.path("registryVersion").asText(""),
                            strategy.path("strategyCode").asText(""),
                            strategy.path("strategyVersion").asText(""));
            QuestionContent content = contractCodec.readQuestionContent(
                    candidateJson.write(question.path("questionContent")), type);
            AnswerSpec answer = contractCodec.readAnswerSpec(
                    candidateJson.write(question.path("answerSpec")), content);
            AssessmentStimulus stimulus = explanationStimulus(
                    groupStimulus, question);
            ObjectiveExplanationStrategyRegistry.requireAllowed(
                    type, selection, stimulus, content, answer);
        } catch (IllegalArgumentException exception) {
            issues.add(error(
                    "EXPLANATION_STRATEGY_REVIEW_REQUIRED", "QUESTION",
                    path + "/explanationStrategy",
                    "Hãy chọn chiến lược giải thích phù hợp trước khi áp dụng.",
                    "EDIT_IN_REVIEW"));
        }
    }

    private static AssessmentStimulus explanationStimulus(
            JsonNode raw,
            JsonNode question) {
        String provenance = raw.path("provenance")
                .path("source").asText("CANDIDATE");
        return switch (raw.path("type").asText("NONE")) {
            case "READING_PASSAGE" -> AssessmentStimulus.readingPassage(
                    raw.path("passageText").asText(""), provenance);
            case "LISTENING_AUDIO" -> AssessmentStimulus.listeningAudio(
                    raw.path("mediaReference").asText(null),
                    raw.path("transcriptText").asText(""),
                    provenance,
                    raw.path("provenance").path("approved")
                            .asBoolean(false));
            default -> AssessmentStimulus.standalonePrompt(
                    question.path("prompt").asText(""), provenance);
        };
    }

    private void validateQuickQuestion(
            TargetRoute target,
            JsonNode question,
            CanonicalQuestionType type,
            String path,
            List<ValidationIssue> issues) {
        String skill = upper(target.skill());
        Set<CanonicalQuestionType> supported = switch (skill) {
            case "READING", "LISTENING" -> Set.of(
                    CanonicalQuestionType.SINGLE_CHOICE,
                    CanonicalQuestionType.MULTIPLE_ANSWER,
                    CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                    CanonicalQuestionType.FILL_BLANK);
            case "WRITING" -> Set.of(CanonicalQuestionType.ESSAY);
            case "SPEAKING" -> Set.of(CanonicalQuestionType.SPEAKING);
            default -> Set.of();
        };
        if (!supported.contains(type)) {
            issues.add(advanced(
                    "QUESTION_TYPE_NOT_SUPPORTED_BY_QUICK",
                    path + "/questionType",
                    "Dạng câu hỏi này phải dùng Advanced."));
        }
        JsonNode content = question.path("questionContent");
        if (content.hasNonNull("imageReference")
                || content.hasNonNull("audioReference")
                || hasOptionImage(content.path("options"))) {
            issues.add(advanced(
                    "ADVANCED_AUTHORING_REQUIRED", path + "/questionContent",
                    "Quick Excel không hỗ trợ media câu hỏi hoặc đáp án."));
        }
        if (type == CanonicalQuestionType.FILL_BLANK) {
            validateQuickBlank(question, path, issues);
        }
        if ("WRITING".equals(skill)) {
            validateQuickWriting(question, path, issues);
        }
        if ("SPEAKING".equals(skill)) {
            validateQuickSpeaking(question, path, issues);
        }
    }

    private void validateQuickBlank(
            JsonNode question,
            String path,
            List<ValidationIssue> issues) {
        JsonNode blanks = question.path("questionContent").path("blanks");
        JsonNode answers = question.path("answerSpec").path("blanks");
        String prompt = question.path("prompt").asText("");
        int tokenCount = count(prompt, "{{blank:blank_1}}");
        boolean simple = blanks.isArray() && blanks.size() == 1
                && "blank_1".equals(blanks.get(0).path("id").asText())
                && answers.isArray() && answers.size() == 1
                && "blank_1".equals(answers.get(0).path("blankId").asText())
                && tokenCount == 1;
        if (!simple) {
            issues.add(advanced(
                    "SIMPLE_BLANK_REQUIRED",
                    path + "/questionContent/blanks",
                    "Quick Excel chỉ hỗ trợ đúng một blank_1 trong prompt."));
        }
    }

    private void validateQuickWriting(
            JsonNode question,
            String path,
            List<ValidationIssue> issues) {
        String task = question.path("essayTaskType").asText("");
        Map<String, Integer> points = Map.of(
                "Q51", 10, "Q52", 10, "Q53", 30, "Q54", 50);
        if (!points.containsKey(task)
                || question.path("points").asInt(-1) != points.getOrDefault(task, -1)) {
            issues.add(error(
                    "WRITING_TASK_IDENTITY_INVALID", "QUESTION",
                    path + "/essayTaskType",
                    "Writing phải dùng Q51–Q54 và điểm cố định 10/10/30/50.",
                    "EDIT_IN_REVIEW"));
        }
        boolean structured = question.path("questionContent")
                .path("writingResponse").isObject()
                && question.path("answerSpec")
                .path("writingBlankAuthority").isObject();
        if (("Q51".equals(task) || "Q52".equals(task)) && !structured) {
            issues.add(error(
                    "WRITING_BLANK_AUTHORITY_INVALID", "QUESTION",
                    path + "/answerSpec/writingBlankAuthority",
                    "Q51/Q52 cần đúng hai blank và answer authority typed.",
                    "EDIT_IN_REVIEW"));
        }
        if (("Q53".equals(task) || "Q54".equals(task)) && structured) {
            issues.add(error(
                    "WRITING_BLANK_AUTHORITY_INVALID", "QUESTION",
                    path + "/answerSpec/writingBlankAuthority",
                    "Q53/Q54 không được có structured blank authority.",
                    "EDIT_IN_REVIEW"));
        }
    }

    private void validateQuickSpeaking(
            JsonNode question,
            String path,
            List<ValidationIssue> issues) {
        JsonNode delivery = question.path("questionContent")
                .path("speakingDelivery");
        boolean valid = "manual_text".equals(delivery.path("inputType").asText())
                && "text_only".equals(delivery.path("deliveryMode").asText())
                && "none".equals(delivery.path("audioOrigin").asText())
                && !delivery.hasNonNull("promptAudioReference")
                && !delivery.hasNonNull("promptPlayLimit");
        if (!valid) {
            issues.add(advanced(
                    "SPEAKING_MODE_QUICK_UNSUPPORTED",
                    path + "/questionContent/speakingDelivery",
                    "Quick Speaking chỉ hỗ trợ manual_text + text_only + none."));
        }
        if (!KOREAN.matcher(question.path("prompt").asText("")).matches()) {
            issues.add(error(
                    "SPEAKING_MANUAL_PROMPT_KOREAN_REQUIRED", "FIELD",
                    path + "/prompt",
                    "Prompt Speaking thủ công phải chứa tiếng Hàn.",
                    "EDIT_IN_REVIEW"));
        }
    }

    private static boolean allowedForSkill(
            CanonicalQuestionType type, String skill) {
        return switch (skill) {
            case "READING", "LISTENING" -> type != CanonicalQuestionType.ESSAY
                    && type != CanonicalQuestionType.SPEAKING;
            case "WRITING" -> type == CanonicalQuestionType.ESSAY;
            case "SPEAKING" -> type == CanonicalQuestionType.SPEAKING;
            default -> false;
        };
    }

    private static boolean hasOptionImage(JsonNode options) {
        if (!options.isArray()) return false;
        for (JsonNode option : options) {
            if (option.hasNonNull("imageReference")) return true;
        }
        return false;
    }

    private static int count(String value, String token) {
        int count = 0;
        int cursor = 0;
        while ((cursor = value.indexOf(token, cursor)) >= 0) {
            count++;
            cursor += token.length();
        }
        return count;
    }

    private static void requireStableId(
            String value, String path, List<ValidationIssue> issues) {
        if (!STABLE_ID.matcher(value).matches()) {
            issues.add(error(
                    "CANDIDATE_STABLE_ID_INVALID", "FIELD", path,
                    "ID candidate không đúng định dạng ổn định.",
                    "EDIT_IN_REVIEW"));
        }
    }

    private static void requireNonBlankBounded(
            String value,
            int maximum,
            String path,
            List<ValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(error(
                    "CANDIDATE_FIELD_REQUIRED", "FIELD", path,
                    "Trường candidate này không được để trống.",
                    "EDIT_IN_REVIEW"));
            return;
        }
        requireMaximumLength(value, maximum, path, issues);
    }

    private static void requireMaximumLength(
            String value,
            int maximum,
            String path,
            List<ValidationIssue> issues) {
        if (value != null && value.length() > maximum) {
            issues.add(error(
                    "CANDIDATE_FIELD_LIMIT_EXCEEDED", "FIELD", path,
                    "Trường candidate vượt quá độ dài cho phép.",
                    "EDIT_IN_REVIEW"));
        }
    }

    private static ValidationIssue advanced(
            String code, String path, String message) {
        return error(code, "FIELD", path, message, "USE_ADVANCED");
    }

    private static ValidationIssue error(
            String code,
            String scope,
            String path,
            String message,
            String remediation) {
        return ValidationIssue.error(
                code, scope, path, message, remediation);
    }

    private static ValidationResult result(List<ValidationIssue> raw) {
        Map<String, ValidationIssue> deduplicated = new LinkedHashMap<>();
        raw.forEach(issue -> deduplicated.putIfAbsent(
                issue.code() + "\n" + issue.path(), issue));
        List<ValidationIssue> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort(Comparator
                .comparingInt((ValidationIssue issue) -> switch (issue.severity()) {
                    case "ERROR" -> 0;
                    case "WARNING" -> 1;
                    default -> 2;
                })
                .thenComparingInt(issue -> pointerIndex(issue.path(), "groups"))
                .thenComparingInt(issue -> pointerIndex(issue.path(), "questions"))
                .thenComparing(issue -> fieldPointer(issue.path()))
                .thenComparing(ValidationIssue::code));
        return new ValidationResult(sorted);
    }

    private static int pointerIndex(String path, String collection) {
        if (path == null) return -1;
        String marker = "/" + collection + "/";
        int start = path.indexOf(marker);
        if (start < 0) return -1;
        start += marker.length();
        int end = path.indexOf('/', start);
        String value = end < 0 ? path.substring(start) : path.substring(start, end);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static String fieldPointer(String path) {
        if (path == null) return "";
        return path.replaceAll("/groups/\\d+", "/groups")
                .replaceAll("/questions/\\d+", "/questions");
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record ValidationResult(List<ValidationIssue> issues) {
        public ValidationResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public boolean hasBlocking() {
            return issues.stream().anyMatch(ValidationIssue::blocking);
        }

        public boolean hasWarnings() {
            return issues.stream().anyMatch(issue ->
                    "WARNING".equals(issue.severity()));
        }
    }
}
