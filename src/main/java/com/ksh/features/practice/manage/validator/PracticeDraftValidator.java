package com.ksh.features.practice.manage.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PracticeDraftValidator {

    private final ObjectMapper objectMapper;
    private final AssessmentAuthoringCatalogService catalogService;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver questionTypeResolver;
    private final PracticeContentRules contentRules;

    public PracticeDraftValidator(ObjectMapper objectMapper) {
        this(objectMapper, null, null, new QuestionTypeResolver(), new PracticeContentRules());
    }

    @Autowired
    public PracticeDraftValidator(ObjectMapper objectMapper,
                                  AssessmentAuthoringCatalogService catalogService,
                                  AssessmentContractCodec contractCodec,
                                  QuestionTypeResolver questionTypeResolver,
                                  PracticeContentRules contentRules) {
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
        this.contractCodec = contractCodec;
        this.questionTypeResolver = questionTypeResolver;
        this.contentRules = contentRules;
    }

    public ValidationResult validate(String draftJson) {
        List<ValidationMsg> messages = new ArrayList<>();
        int sectionCount = 0;
        int groupCount = 0;
        int questionCount = 0;
        double totalPoints = 0.0;

        try {
            JsonNode root = objectMapper.readTree(draftJson);
            
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template =
                    catalogService == null ? null : catalogService.defaultTemplate();

            TestIndex testIndex = validateTests(messages, root.path("tests"), template);
            JsonNode sections = root.path("sections");

            if (!sections.isArray() || sections.size() == 0) {
                messages.add(new ValidationMsg("BLOCKING", "Đề thi bắt buộc phải có ít nhất một Phần thi (Section)."));
            } else {
                sectionCount = sections.size();
                Set<String> sectionKeys = new HashSet<>();
                for (int sIdx = 0; sIdx < sections.size(); sIdx++) {
                    JsonNode sec = sections.get(sIdx);
                    String sTitle = sec.path("title").asText("Phần thi " + (sIdx + 1));
                    String skill = sec.path("skill").asText("").trim().toUpperCase(Locale.ROOT);

                    if (skill.isBlank()) {
                        messages.add(new ValidationMsg("BLOCKING", String.format("Phần thi '%s' chưa được cấu hình kỹ năng (Reading/Listening...).", sTitle), sIdx, null, null));
                    }
                    validateTemplateSkill(messages, template, skill, sIdx);
                    validateSectionHierarchy(messages, sec, skill, testIndex, sectionKeys, sIdx);
                    AssessmentAuthoringCatalogService.SkillAuthoringPolicy skillPolicy = resolveSkillPolicy(
                            template, skill);

                    JsonNode groups = sec.path("groups");
                    int localQuestionNo = "WRITING".equals(skill) ? 51 : 1;
                    Set<String> groupCodes = new HashSet<>();
                    if (!groups.isArray() || groups.size() == 0) {
                        messages.add(new ValidationMsg("BLOCKING", String.format("Phần thi '%s' trống rỗng, không chứa Nhóm câu hỏi nào.", sTitle), sIdx, null, null));
                    } else {
                        groupCount += groups.size();
                        for (int gIdx = 0; gIdx < groups.size(); gIdx++) {
                            JsonNode grp = groups.get(gIdx);
                            String gLabel = grp.path("label").asText("Nhóm " + (gIdx + 1));
                            validateGroupCode(messages, sec, grp, groupCodes, sIdx, gIdx);
                            validateImportedStimulusReview(messages, grp, sIdx, gIdx);
                            
                            JsonNode questions = grp.path("questions");
                            if (!questions.isArray() || questions.size() == 0) {
                                messages.add(new ValidationMsg("BLOCKING", String.format("Nhóm '%s' trong phần '%s' chưa có câu hỏi nào.", gLabel, sTitle), sIdx, gIdx, null));
                            } else {
                                questionCount += questions.size();
                                for (int qIdx = 0; qIdx < questions.size(); qIdx++) {
                                    JsonNode q = questions.get(qIdx);
                                    int qNo = q.path("questionNo").asInt(qIdx + 1);
                                    if (qNo != localQuestionNo) {
                                        messages.add(new ValidationMsg(
                                                "BLOCKING",
                                                "QUESTION_NUMBER_NOT_LOCAL_SEQUENTIAL",
                                                "Số câu trong " + sec.path("lessonCode").asText(sTitle)
                                                        + " phải liên tục từ 1; vị trí này phải là câu "
                                                        + localQuestionNo + " thay vì " + qNo + ".",
                                                sIdx, gIdx, qIdx));
                                    }
                                    localQuestionNo++;
                                    String type = q.path("questionType").asText("SINGLE_CHOICE");
                                    String prompt = q.path("prompt").asText("");
                                    double points = q.path("points").asDouble(1.0);
                                    totalPoints += points;

                                    if (points <= 0) {
                                        messages.add(new ValidationMsg("BLOCKING", "POINTS_INVALID",
                                                String.format("Câu hỏi số %d phải có điểm tối đa lớn hơn 0.", qNo),
                                                sIdx, gIdx, qIdx));
                                    }

                                    CanonicalQuestionType canonicalType = resolveQuestionType(
                                            messages, type, sIdx, gIdx, qIdx);
                                    if (canonicalType != null) {
                                        validatePolicy(messages, skill, canonicalType, sIdx, gIdx, qIdx);
                                    }

                                    if (prompt.isBlank()) {
                                        messages.add(new ValidationMsg("WARNING", String.format("Câu hỏi số %d trong nhóm '%s' trống tiêu đề (prompt).", qNo, gLabel), sIdx, gIdx, qIdx));
                                    }
                                    if ("PDF_AI".equalsIgnoreCase(q.path("importSource").asText())
                                            && q.path("reviewRequired").asBoolean(false)) {
                                        messages.add(new ValidationMsg(
                                                "BLOCKING",
                                                "AI_QUESTION_REVIEW_REQUIRED",
                                                "Câu hỏi AI số " + qNo + " cần được giáo viên xác nhận.",
                                                sIdx, gIdx, qIdx));
                                    }

                                    // Answer and Option validation
                                    JsonNode options = q.path("options");
                                    JsonNode answer = q.path("answer");

                                    if (canonicalType == CanonicalQuestionType.SINGLE_CHOICE) {
                                        validateOptionCount(messages, skillPolicy, canonicalType, options,
                                                qNo, sIdx, gIdx, qIdx);
                                        if (!hasLegacyOrTypedAnswer(q, answer)) {
                                            messages.add(new ValidationMsg("BLOCKING", String.format("Câu hỏi số %d chưa chọn đáp án đúng.", qNo), sIdx, gIdx, qIdx));
                                        }
                                    }

                                    validateCanonicalAnswer(messages, template, skill, canonicalType, q,
                                            sIdx, gIdx, qIdx);

                                    validateSpeakingQuestionType(messages, skill, type, sIdx, gIdx, qIdx);
                                    validateWritingTaskMetadata(messages, skill, type, q, sIdx, gIdx, qIdx);

                                    String explanation = q.path("explanationVi").asText("");
                                    if (explanation.isBlank()) {
                                        messages.add(new ValidationMsg("WARNING", String.format("Câu hỏi số %d chưa có bài dịch hoặc giải thích tiếng Việt.", qNo), sIdx, gIdx, qIdx));
                                    }
                                }
                            }
                        }
                    }
                    validateWritingSection(messages, sec, skill, sIdx);
                }
            }
        } catch (Exception e) {
            messages.add(new ValidationMsg("BLOCKING", "Dữ liệu bản nháp không đúng cấu trúc JSON hợp lệ."));
        }

        boolean hasBlocking = messages.stream().anyMatch(m -> "BLOCKING".equals(m.type()));
        return new ValidationResult(hasBlocking, messages, sectionCount, groupCount, questionCount, totalPoints);
    }

    private TestIndex validateTests(List<ValidationMsg> messages,
                                    JsonNode tests,
                                    AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        Map<Integer, String> clientIdByNumber = new LinkedHashMap<>();
        Map<String, Integer> numberByClientId = new HashMap<>();
        if (!tests.isArray() || tests.isEmpty()) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "TEST_REQUIRED",
                    "Bộ đề phải có ít nhất một Test trước các phần L/R/W/S."));
            return new TestIndex(clientIdByNumber, numberByClientId);
        }
        for (int index = 0; index < tests.size(); index++) {
            JsonNode test = tests.get(index);
            int testNo = test.path("testNo").asInt(0);
            String clientId = test.path("clientId").asText("").trim();
            if (testNo <= 0) {
                messages.add(new ValidationMsg("BLOCKING", "TEST_NUMBER_INVALID",
                        "Test tại vị trí " + (index + 1) + " phải có testNo lớn hơn 0."));
                continue;
            }
            if (clientId.isBlank()) {
                messages.add(new ValidationMsg("BLOCKING", "TEST_CLIENT_ID_REQUIRED",
                        "Test " + testNo + " thiếu clientId ổn định."));
                continue;
            }
            if (clientIdByNumber.putIfAbsent(testNo, clientId) != null) {
                messages.add(new ValidationMsg("BLOCKING", "TEST_NUMBER_DUPLICATED",
                        "Test " + testNo + " bị khai báo trùng."));
            }
            if (numberByClientId.putIfAbsent(clientId, testNo) != null) {
                messages.add(new ValidationMsg("BLOCKING", "TEST_CLIENT_ID_DUPLICATED",
                        "clientId của Test " + testNo + " bị trùng."));
            }
        }
        return new TestIndex(clientIdByNumber, numberByClientId);
    }

    private void validateSectionHierarchy(List<ValidationMsg> messages,
                                          JsonNode section,
                                          String skill,
                                          TestIndex testIndex,
                                          Set<String> sectionKeys,
                                          int sIdx) {
        int testNo = section.path("testNo").asInt(0);
        String testClientId = section.path("testClientId").asText("").trim();
        String knownClientId = testIndex.clientIdByNumber().get(testNo);
        if (testNo <= 0 || knownClientId == null || !knownClientId.equals(testClientId)) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "SECTION_TEST_REFERENCE_INVALID",
                    "Phần thi phải tham chiếu đúng một Test bằng testNo và testClientId.",
                    sIdx, null, null));
        }

        String lessonCode = section.path("lessonCode").asText("").trim().toUpperCase(Locale.ROOT);
        String expectedLessonCode = expectedLessonCode(skill, testNo);
        if (expectedLessonCode == null || !expectedLessonCode.equals(lessonCode)) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "LESSON_CODE_INVALID",
                    "Mã phần phải là " + (expectedLessonCode == null ? "L/R/W/S + số Test" : expectedLessonCode)
                            + " theo kỹ năng và Test đã chọn.",
                    sIdx, null, null));
        }
        String sectionKey = testNo + ":" + skill;
        if (!skill.isBlank() && !sectionKeys.add(sectionKey)) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "TEST_SKILL_SECTION_DUPLICATED",
                    "Test " + testNo + " chỉ được có một phần " + skill + ".",
                    sIdx, null, null));
        }
    }

    private void validateGroupCode(List<ValidationMsg> messages,
                                   JsonNode section,
                                   JsonNode group,
                                   Set<String> groupCodes,
                                   int sIdx,
                                   int gIdx) {
        String lessonCode = section.path("lessonCode").asText("").trim().toUpperCase(Locale.ROOT);
        String groupCode = group.path("groupCode").asText("").trim().toUpperCase(Locale.ROOT);
        boolean formatValid = !lessonCode.isBlank()
                && groupCode.matches(Pattern.quote(lessonCode) + "\\.\\d+");
        if (!formatValid) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "GROUP_CODE_INVALID",
                    "Mã nhóm phải theo dạng " + (lessonCode.isBlank() ? "R1.1/L1.1" : lessonCode + ".1") + ".",
                    sIdx, gIdx, null));
        } else if (!groupCodes.add(groupCode)) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "GROUP_CODE_DUPLICATED",
                    "Mã nhóm " + groupCode + " bị trùng trong cùng phần.",
                    sIdx, gIdx, null));
        }
    }

    private AssessmentAuthoringCatalogService.SkillAuthoringPolicy resolveSkillPolicy(
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            String skill) {
        if (template == null || skill == null || skill.isBlank()) return null;
        try {
            return template.requireSkill(skill);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void validateOptionCount(List<ValidationMsg> messages,
                                     AssessmentAuthoringCatalogService.SkillAuthoringPolicy skillPolicy,
                                     CanonicalQuestionType type,
                                     JsonNode options,
                                     int qNo,
                                     int sIdx,
                                     int gIdx,
                                     int qIdx) {
        int minOptions = 2;
        int maxOptions = 8;
        if (skillPolicy != null) {
            AssessmentAuthoringCatalogService.QuestionAuthoringPolicy questionPolicy =
                    skillPolicy.questionPolicy(type.name());
            if (questionPolicy != null) {
                minOptions = questionPolicy.minOptions();
                maxOptions = questionPolicy.maxOptions();
            }
        }
        int optionCount = options.isArray() ? options.size() : 0;
        if (optionCount >= minOptions && optionCount <= maxOptions) return;
        String rule = minOptions == maxOptions
                ? "đúng " + minOptions
                : "từ " + minOptions + " đến " + maxOptions;
        messages.add(new ValidationMsg(
                "BLOCKING",
                "OPTION_COUNT_OUTSIDE_TEMPLATE",
                "Câu hỏi trắc nghiệm số " + qNo + " bắt buộc có " + rule
                        + " đáp án lựa chọn theo mẫu đề.",
                sIdx, gIdx, qIdx));
    }

    private static boolean hasLegacyOrTypedAnswer(JsonNode question, JsonNode answer) {
        if (question.path("answerSpec").isObject()) return true;
        if (answer.isObject()) {
            JsonNode value = answer.path("value");
            return value.isArray() ? !value.isEmpty() : !value.asText("").isBlank();
        }
        return !answer.asText("").isBlank() || !question.path("answerKey").asText("").isBlank();
    }

    private static String expectedLessonCode(String skill, int testNo) {
        if (testNo <= 0) return null;
        String prefix = switch (skill == null ? "" : skill.toUpperCase(Locale.ROOT)) {
            case "LISTENING" -> "L";
            case "READING" -> "R";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> null;
        };
        return prefix == null ? null : prefix + testNo;
    }

    private record TestIndex(Map<Integer, String> clientIdByNumber,
                             Map<String, Integer> numberByClientId) {
    }

    private void validateImportedStimulusReview(List<ValidationMsg> messages,
                                                JsonNode group,
                                                int sIdx,
                                                int gIdx) {
        JsonNode stimulus = group.path("stimulus");
        JsonNode provenance = stimulus.path("provenance");
        String source = provenance.path("source").asText("");
        String type = stimulus.path("type").asText("NONE");
        if (!"NONE".equals(type)
                && !"MANUAL".equalsIgnoreCase(source)
                && !"EXCEL".equalsIgnoreCase(source)
                && !provenance.path("approved").asBoolean(false)) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "STIMULUS_REVIEW_REQUIRED",
                    "Nội dung dùng chung từ AI/import cần được giáo viên kiểm tra và xác nhận.",
                    sIdx, gIdx, null));
        }
    }

    private void validateTemplateSkill(List<ValidationMsg> messages,
                                       AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                                       String skill,
                                       int sIdx) {
        if (template == null || skill == null || skill.isBlank()) {
            return;
        }
        try {
            template.requireSkill(skill);
        } catch (IllegalArgumentException exception) {
            messages.add(new ValidationMsg("BLOCKING", "SKILL_NOT_ALLOWED_BY_TEMPLATE",
                    "Kỹ năng " + skill + " không được phép trong mẫu " + template.displayName() + ".",
                    sIdx, null, null));
        }
    }

    private CanonicalQuestionType resolveQuestionType(List<ValidationMsg> messages,
                                                      String rawType,
                                                      int sIdx,
                                                      int gIdx,
                                                      int qIdx) {
        try {
            return questionTypeResolver.resolve(rawType);
        } catch (IllegalArgumentException exception) {
            messages.add(new ValidationMsg("BLOCKING", "QUESTION_TYPE_UNSUPPORTED",
                    "Dạng câu hỏi không được hỗ trợ: " + rawType, sIdx, gIdx, qIdx));
            return null;
        }
    }

    private void validatePolicy(List<ValidationMsg> messages,
                                String rawSkill,
                                CanonicalQuestionType type,
                                int sIdx,
                                int gIdx,
                                int qIdx) {
        try {
            AssessmentSkill skill = AssessmentSkill.valueOf(rawSkill.toUpperCase(Locale.ROOT));
            contentRules.requireAllowed(skill, type);
        } catch (RuntimeException exception) {
            messages.add(new ValidationMsg("BLOCKING", "QUESTION_TYPE_NOT_ALLOWED_FOR_SKILL",
                    "Dạng " + type + " không được phép cho kỹ năng " + rawSkill + ".",
                    sIdx, gIdx, qIdx));
        }
    }

    private void validateCanonicalAnswer(List<ValidationMsg> messages,
                                         AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                                         String skill,
                                         CanonicalQuestionType type,
                                         JsonNode question,
                                         int sIdx,
                                         int gIdx,
                                         int qIdx) {
        if (type == null) {
            return;
        }
        JsonNode typedContent = question.get("questionContent");
        JsonNode typedSpec = question.get("answerSpec");
        if (typedContent != null && typedContent.isObject() && typedSpec != null && typedSpec.isObject()
                && contractCodec != null) {
            try {
                QuestionContent content = contractCodec.readQuestionContent(typedContent.toString(), type);
                AnswerSpec spec = contractCodec.readAnswerSpec(typedSpec.toString(), content);
                validateAuthoringScoringPolicy(messages, template, skill, type, spec, sIdx, gIdx, qIdx);
            } catch (IllegalArgumentException exception) {
                messages.add(new ValidationMsg("BLOCKING", "ANSWER_SPEC_INVALID",
                        "Cấu hình nội dung hoặc đáp án typed không hợp lệ.", sIdx, gIdx, qIdx));
            }
            return;
        }

        String legacyAnswer = legacyAnswer(question);
        switch (type) {
            case TRUE_FALSE_NOT_GIVEN -> {
                if (!java.util.Set.of("TRUE", "FALSE", "NOT_GIVEN").contains(legacyAnswer.toUpperCase())) {
                    messages.add(new ValidationMsg("BLOCKING", "TFNG_ANSWER_REQUIRED",
                            "Câu Đúng/Sai/Không có thông tin phải chọn một đáp án chuẩn.", sIdx, gIdx, qIdx));
                }
            }
            case FILL_BLANK -> {
                if (legacyAnswer.isBlank()) {
                    messages.add(new ValidationMsg("BLOCKING", "FILL_BLANK_ANSWER_REQUIRED",
                            "Câu điền từ phải có ít nhất một đáp án được chấp nhận.", sIdx, gIdx, qIdx));
                }
            }
            case SINGLE_CHOICE, ESSAY, SPEAKING -> {
                // Existing checks or profile-based policy cover these types.
            }
        }
    }

    private void validateAuthoringScoringPolicy(
            List<ValidationMsg> messages,
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            String skill,
            CanonicalQuestionType type,
            AnswerSpec spec,
            int sIdx,
            int gIdx,
            int qIdx) {
        if (template == null || skill == null || skill.isBlank()) return;
        AssessmentAuthoringCatalogService.QuestionAuthoringPolicy policy;
        try {
            policy = template.requireSkill(skill).questionPolicy(type.name());
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (policy == null || policy.allowedScoringPolicyCodes().isEmpty()) return;
        if (!policy.allowedScoringPolicyCodes().contains(spec.scoringPolicyCode().name())) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "SCORING_POLICY_NOT_ALLOWED_BY_TEMPLATE",
                    "Chính sách chấm điểm không được phép cho dạng " + type + ".",
                    sIdx, gIdx, qIdx));
        }
    }

    private static String legacyAnswer(JsonNode question) {
        JsonNode answer = question.path("answer");
        if (answer.isObject()) {
            JsonNode value = answer.path("value");
            if (value.isArray()) {
                java.util.List<String> values = new java.util.ArrayList<>();
                value.forEach(item -> values.add(item.asText("")));
                return String.join(",", values);
            }
            return value.asText("").trim();
        }
        String direct = answer.asText("").trim();
        return direct.isBlank() ? question.path("answerKey").asText("").trim() : direct;
    }

    private void validateSpeakingQuestionType(List<ValidationMsg> messages,
                                              String skill,
                                              String questionType,
                                              int sIdx,
                                              int gIdx,
                                              int qIdx) {
        if ("SPEAKING".equalsIgnoreCase(skill) && PracticeQuestion.TYPE_ESSAY.equals(questionType)) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "Câu Speaking mới phải dùng question type SPEAKING; không dùng ESSAY cho bài nói.",
                    sIdx,
                    gIdx,
                    qIdx
            ));
        }
    }

    private void validateWritingTaskMetadata(List<ValidationMsg> messages,
                                             String skill,
                                             String questionType,
                                             JsonNode question,
                                             int sIdx,
                                             int gIdx,
                                             int qIdx) {
        if (!"WRITING".equalsIgnoreCase(skill) || !PracticeQuestion.TYPE_ESSAY.equals(questionType)) {
            return;
        }
        JsonNode taskNode = question.get("essayTaskType");
        if (taskNode == null || taskNode.isNull()) {
            messages.add(new ValidationMsg("BLOCKING", "WRITING_TASK_REQUIRED",
                    "Mỗi câu Writing phải chọn một task Q51, Q52, Q53 hoặc Q54.",
                    sIdx, gIdx, qIdx));
            return;
        }
        if (!taskNode.isTextual()) {
            messages.add(new ValidationMsg("BLOCKING", "Loại bài Writing không hợp lệ.", sIdx, gIdx, qIdx));
            return;
        }
        String value = taskNode.asText();
        if (value.isBlank()) {
            messages.add(new ValidationMsg("BLOCKING", "Vui lòng chọn loại bài Writing cho câu tự luận.", sIdx, gIdx, qIdx));
            return;
        }
        try {
            WritingTaskType taskType = WritingTaskType.valueOf(value);
            if (!contentRules.requiredWritingTasks().contains(taskType)) {
                messages.add(new ValidationMsg("BLOCKING", "WRITING_TASK_UNSUPPORTED",
                        "Writing chỉ hỗ trợ Q51, Q52, Q53 và Q54.", sIdx, gIdx, qIdx));
            }
        } catch (IllegalArgumentException e) {
            messages.add(new ValidationMsg(
                    "BLOCKING",
                    "WRITING_TASK_UNSUPPORTED",
                    "Loại bài Writing không hợp lệ; chỉ hỗ trợ Q51, Q52, Q53 và Q54.",
                    sIdx,
                    gIdx,
                    qIdx));
        }
    }

    private void validateWritingSection(List<ValidationMsg> messages,
                                        JsonNode section,
                                        String skill,
                                        int sIdx) {
        if (!"WRITING".equalsIgnoreCase(skill)) {
            return;
        }
        Map<WritingTaskType, Integer> counts = new java.util.EnumMap<>(WritingTaskType.class);
        JsonNode groups = section.path("groups");
        if (groups.isArray()) {
            for (JsonNode group : groups) {
                JsonNode questions = group.path("questions");
                if (!questions.isArray()) continue;
                for (JsonNode question : questions) {
                    String rawTask = question.path("essayTaskType").asText("").trim();
                    if (rawTask.isBlank()) continue;
                    try {
                        WritingTaskType task = WritingTaskType.valueOf(rawTask);
                        if (contentRules.requiredWritingTasks().contains(task)) {
                            counts.merge(task, 1, Integer::sum);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Per-question validation reports the invalid value.
                    }
                }
            }
        }
        for (WritingTaskType required : contentRules.requiredWritingTasksInOrder()) {
            int count = counts.getOrDefault(required, 0);
            if (count != 1) {
                messages.add(new ValidationMsg(
                        "BLOCKING",
                        "WRITING_TASK_CARDINALITY_INVALID",
                        "Phần Writing phải có đúng một câu " + required.name()
                                + "; hiện có " + count + ".",
                        sIdx, null, null));
            }
        }
    }

    public record ValidationMsg(String type, String code, String content, Integer sIdx, Integer gIdx, Integer qIdx) {
        public ValidationMsg(String type, String content) {
            this(type, "GENERAL", content, null, null, null);
        }

        public ValidationMsg(String type, String code, String content) {
            this(type, code, content, null, null, null);
        }

        public ValidationMsg(String type, String content, Integer sIdx, Integer gIdx, Integer qIdx) {
            this(type, "GENERAL", content, sIdx, gIdx, qIdx);
        }
    }

    public record ValidationResult(
            boolean hasBlocking,
            List<ValidationMsg> messages,
            int sectionCount,
            int groupCount,
            int questionCount,
            double totalPoints
    ) {}
}
