package com.ksh.features.practice.manage.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PracticeDraftValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PracticeDraftValidator validator = new PracticeDraftValidator(mapper);

    @Test
    void publishValidationRejectsNotGivenStrategyForFalseAnswer() {
        PracticeDraftValidator.ValidationResult falseResult =
                validator.validate(tfngStrategyDraft("FALSE"));
        assertTrue(falseResult.messages().stream().anyMatch(message ->
                "EXPLANATION_STRATEGY_ANSWER_AUTHORITY_INVALID"
                        .equals(message.code())));

        PracticeDraftValidator.ValidationResult notGivenResult =
                validator.validate(tfngStrategyDraft("NOT_GIVEN"));
        assertFalse(notGivenResult.messages().stream().anyMatch(message ->
                "EXPLANATION_STRATEGY_ANSWER_AUTHORITY_INVALID"
                        .equals(message.code())));
    }

    @Test
    public void testValidDraft() {
        String draftJson = """
        {
          "document": {
            "detectedCategory": "TOPIK_II"
          },
          "tests": [{"clientId":"test-1","testNo":1,"title":"Test 1"}],
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "testNo": 1,
              "testClientId": "test-1",
              "lessonCode": "R1",
              "durationMinutes": 40,
              "groups": [
                {
                  "label": "1-2",
                  "groupCode": "R1.1",
                  "questionFrom": 1,
                  "questionTo": 2,
                  "instruction": "Chọn đáp án đúng",
                  "questions": [
                    {
                      "questionNo": 1,
                      "questionType": "SINGLE_CHOICE",
                      "prompt": "Câu hỏi số 1",
                      "options": ["A", "B", "C", "D"],
                      "answer": { "value": "1" },
                      "explanationStrategy": {
                        "registryVersion": "rl-explanation-strategy-registry-v1",
                        "strategyCode": "EVIDENCE_ONLY",
                        "strategyVersion": "v1"
                      },
                      "explanationVi": "Vì A đúng"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
        
        PracticeDraftValidator.ValidationResult result = validator.validate(draftJson);
        assertFalse(result.hasBlocking());
        assertEquals(1, result.sectionCount());
        assertEquals(1, result.groupCount());
        assertEquals(1, result.questionCount());
    }

    @Test
    public void testBlockingNoSections() {
        String draftJson = "{ \"sections\": [] }";
        PracticeDraftValidator.ValidationResult result = validator.validate(draftJson);
        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m -> "BLOCKING".equals(m.type()) && m.content().contains("Section")));
    }

    @Test
    public void testBlockingLessOptions() {
        String draftJson = """
        {
          "document": {
            "detectedCategory": "TOPIK_II"
          },
          "tests": [{"clientId":"test-1","testNo":1,"title":"Test 1"}],
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "testNo": 1,
              "testClientId": "test-1",
              "lessonCode": "R1",
              "groups": [
                {
                  "label": "1",
                  "groupCode": "R1.1",
                  "questions": [
                    {
                      "questionNo": 1,
                      "questionType": "SINGLE_CHOICE",
                      "prompt": "Câu hỏi",
                      "options": ["Một option duy nhất"],
                      "answer": { "value": "1" }
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
        PracticeDraftValidator.ValidationResult result = validator.validate(draftJson);
        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m -> "OPTION_COUNT_OUTSIDE_TEMPLATE".equals(m.code())));
    }
    @Test
    public void writingEssayBlankTaskIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("\"\""));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "BLOCKING".equals(m.type())
                        && m.content().equals("Vui lòng chọn loại bài Writing cho câu tự luận.")));
    }

    @Test
    public void writingEssayMissingTaskIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithoutTask());

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "WRITING_TASK_REQUIRED".equals(m.code())));
    }

    @Test
    public void writingEssayNullTaskIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("null"));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "WRITING_TASK_REQUIRED".equals(m.code())));
    }

    @Test
    public void pdfAiStimulusAndQuestionRequireExplicitLecturerReview() {
        PracticeDraftValidator.ValidationResult result = validator.validate(pdfAiDraft(false, true));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(message ->
                "STIMULUS_REVIEW_REQUIRED".equals(message.code())));
        assertTrue(result.messages().stream().anyMatch(message ->
                "AI_QUESTION_REVIEW_REQUIRED".equals(message.code())));
    }

    @Test
    public void reviewedPdfAiContentPassesReviewGate() {
        PracticeDraftValidator.ValidationResult result = validator.validate(pdfAiDraft(true, false));

        assertFalse(result.messages().stream().anyMatch(message ->
                "STIMULUS_REVIEW_REQUIRED".equals(message.code())
                        || "AI_QUESTION_REVIEW_REQUIRED".equals(message.code())));
    }

    private String pdfAiDraft(boolean stimulusApproved, boolean questionReviewRequired) {
        return """
                {
                  "document":{"detectedCategory":"TOPIK_II"},
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[{
                    "title":"Reading",
                    "skill":"READING",
                    "testNo":1,
                    "testClientId":"test-1",
                    "lessonCode":"R1",
                    "groups":[{
                      "label":"1",
                      "groupCode":"R1.1",
                      "stimulus":{
                        "type":"READING_PASSAGE",
                        "passageText":"본문",
                        "provenance":{"source":"PDF_AI","approved":%s}
                      },
                      "questions":[{
                        "questionNo":1,
                        "questionType":"SINGLE_CHOICE",
                        "prompt":"질문",
                        "options":["A","B"],
                        "answer":{"value":"1"},
                        "points":2,
                        "importSource":"PDF_AI",
                        "reviewRequired":%s
                      }]
                    }]
                  }]
                }
                """.formatted(stimulusApproved, questionReviewRequired);
    }

    @Test
    public void writingEssayGeneralTaskIsBlocked() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("\"GENERAL\""));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "WRITING_TASK_UNSUPPORTED".equals(m.code())));
    }

    @Test
    public void writingEssayInvalidTaskIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("\"Q51_52\""));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "WRITING_TASK_UNSUPPORTED".equals(m.code())
                        && m.content().contains("Q51, Q52, Q53 và Q54")));
    }

    @Test
    public void writingEssayNonTextTaskIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("53"));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "BLOCKING".equals(m.type())
                        && m.content().equals("Loại bài Writing không hợp lệ.")));
    }

    @Test
    public void nonWritingInvalidTaskIsIgnored() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                readingQuestionWithStaleTask("\"NOT_A_TASK\""));

        assertFalse(result.hasBlocking());
        assertFalse(result.messages().stream().anyMatch(m -> m.content().contains("Writing")));
    }

    @Test
    public void writingCompleteQ51ToQ54SetIsValid() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                completeWritingDraft());

        assertFalse(result.hasBlocking());
    }

    @Test
    void legacyEssayShapedQ51RequiresExplicitStructuredConversion() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                writingDraftWithTask("\"Q51\""));

        assertTrue(result.messages().stream().anyMatch(message ->
                "WRITING_STRUCTURED_BLANKS_CONVERSION_REQUIRED"
                        .equals(message.code())));
    }

    @Test
    void validQ51StructuredBlankAuthorityPassesAuthoringGate() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                writingDraft(structuredWritingQuestion(
                        "Q51", 51, 10, "값/표현;그대로", "둘째 답")));

        assertFalse(result.messages().stream().anyMatch(message ->
                message.code() != null
                        && message.code().startsWith(
                        "WRITING_STRUCTURED_BLANKS")));
    }

    @Test
    void mismatchedQ51AuthorityFailsClosed() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                writingDraft(structuredWritingQuestion(
                        "Q51", 51, 10, "첫째 답", "둘째 답")
                        .replace(
                                "\"taskType\":\"Q51\"",
                                "\"taskType\":\"Q52\"")));

        assertTrue(result.messages().stream().anyMatch(message ->
                "WRITING_STRUCTURED_BLANKS_INVALID"
                        .equals(message.code())));
    }

    @Test
    void writingTaskWithWrongFixedPointsIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                completeWritingDraft().replace(
                        "\"points\":30,\"essayTaskType\":\"Q53\"",
                        "\"points\":10,\"essayTaskType\":\"Q53\""));

        assertTrue(result.messages().stream().anyMatch(message ->
                "WRITING_TASK_POINTS_MISMATCH".equals(message.code())
                        && message.content().contains("30")));
    }

    @Test
    public void speakingQuestionTypeIsValidForSpeakingSection() {
        PracticeDraftValidator.ValidationResult result = validator.validate(speakingDraft("SPEAKING"));

        assertFalse(result.hasBlocking());
    }

    @Test
    public void speakingEssayIsBlockingForNewDrafts() {
        PracticeDraftValidator.ValidationResult result = validator.validate(speakingDraft("ESSAY"));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "BLOCKING".equals(m.type())
                        && m.content().contains("question type SPEAKING")));
    }

    @Test
    void speakingPromptAudioIsRequiredForNewDrafts() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                speakingDraft("SPEAKING").replace("/practice/materials/7/content", ""));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(message ->
                "SPEAKING_PROMPT_AUDIO_REQUIRED".equals(message.code())));
    }

    @Test
    void speakingPromptAudioMustUseGovernedMaterialReference() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                speakingDraft("SPEAKING").replace(
                        "/practice/materials/7/content", "https://example.test/question.mp3"));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(message ->
                "SPEAKING_PROMPT_AUDIO_REQUIRED".equals(message.code())));
    }

    @Test
    void speakingV2AcceptsOnlyTheThreeCoherentDeliveryCombinations() {
        for (String delivery : java.util.List.of(
                """
                "inputType":"audio_upload","deliveryMode":"audio_only",
                "promptAudioReference":"/practice/manage/drafts/10/questions/q/speaking-prompt/media/original",
                "audioOrigin":"teacher_upload","promptPlayLimit":1
                """,
                """
                "inputType":"manual_text","deliveryMode":"text_only",
                "audioOrigin":"none"
                """,
                """
                "inputType":"manual_text","deliveryMode":"text_and_audio",
                "promptAudioReference":"/practice/manage/drafts/10/questions/q/speaking-prompt/media/generated",
                "audioOrigin":"ai_tts","promptPlayLimit":1
                """)) {
            PracticeDraftValidator.ValidationResult result =
                    validator.validate(speakingV2Draft(delivery));

            assertFalse(result.messages().stream().anyMatch(message ->
                    "SPEAKING_MODE_COMBINATION_INVALID".equals(message.code())
                            || "SPEAKING_TEXT_ONLY_AUDIO_FORBIDDEN"
                            .equals(message.code())));
        }
    }

    @Test
    void speakingV2RejectsCrossModeAndInventedTextOnlyPlayback() {
        PracticeDraftValidator.ValidationResult cross = validator.validate(
                speakingV2Draft("""
                        "inputType":"audio_upload",
                        "deliveryMode":"text_and_audio",
                        "promptAudioReference":"/practice/materials/7/content",
                        "audioOrigin":"ai_tts",
                        "promptPlayLimit":1
                        """));
        PracticeDraftValidator.ValidationResult textOnlyAudio =
                validator.validate(speakingV2Draft("""
                        "inputType":"manual_text",
                        "deliveryMode":"text_only",
                        "promptAudioReference":"/practice/materials/7/content",
                        "audioOrigin":"none",
                        "promptPlayLimit":1
                        """));

        assertTrue(cross.messages().stream().anyMatch(message ->
                "SPEAKING_MODE_COMBINATION_INVALID".equals(message.code())));
        assertTrue(textOnlyAudio.messages().stream().anyMatch(message ->
                "SPEAKING_TEXT_ONLY_AUDIO_FORBIDDEN".equals(message.code())));
    }

    @Test
    void speakingManualV2RequiresKoreanCapablePrompt() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                speakingV2Draft("""
                        "inputType":"manual_text",
                        "deliveryMode":"text_only",
                        "audioOrigin":"none"
                        """).replace(
                        "주말에 무엇을 합니까?",
                        "Describe your weekend"));

        assertTrue(result.messages().stream().anyMatch(message ->
                "SPEAKING_MANUAL_PROMPT_KOREAN_REQUIRED"
                        .equals(message.code())));
    }

    @Test
    void fillBlankTokenPlacedExactlyOnceIsValid() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                fillBlankDraft("도시는 {{blank:blank_1}}입니다."));

        assertFalse(result.messages().stream().anyMatch(message ->
                message.code() != null && message.code().startsWith("FILL_BLANK_TOKEN_")));
    }

    @Test
    void fillBlankWithoutPlacedTokenIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                fillBlankDraft("도시는 어디입니까?"));

        assertTrue(result.messages().stream().anyMatch(message ->
                "FILL_BLANK_TOKEN_REQUIRED".equals(message.code())));
    }

    @Test
    void duplicatedFillBlankTokenIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                fillBlankDraft("{{blank:blank_1}} / {{blank:blank_1}}"));

        assertTrue(result.messages().stream().anyMatch(message ->
                "FILL_BLANK_TOKEN_DUPLICATED".equals(message.code())));
    }

    @Test
    void unknownFillBlankTokenIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(
                fillBlankDraft("{{blank:blank_1}} {{blank:missing}}"));

        assertTrue(result.messages().stream().anyMatch(message ->
                "FILL_BLANK_TOKEN_UNKNOWN".equals(message.code())));
    }

    @Test
    void questionNumberResetsInsideEverySkillSection() {
        PracticeDraftValidator.ValidationResult result = validator.validate(twoSkillDraft(1));

        assertFalse(result.messages().stream().anyMatch(message ->
                "QUESTION_NUMBER_NOT_LOCAL_SEQUENTIAL".equals(message.code())));
    }

    @Test
    void globalQuestionNumberContinuationAcrossSkillsIsBlocked() {
        PracticeDraftValidator.ValidationResult result = validator.validate(twoSkillDraft(2));

        assertTrue(result.messages().stream().anyMatch(message ->
                "QUESTION_NUMBER_NOT_LOCAL_SEQUENTIAL".equals(message.code())
                        && message.content().contains("L1")));
    }

    private String writingDraftWithTask(String rawTaskValue) {
        return writingDraft("""
                    {
                      "questionNo": 51,
                      "questionType": "ESSAY",
                      "prompt": "Prompt",
                      "answer": { "value": "" },
                      "explanationVi": "Explanation",
                      "points": 10,
                      "essayTaskType": %s
                    }
                """.formatted(rawTaskValue));
    }

    private String twoSkillDraft(int listeningQuestionNo) {
        return """
                {
                  "document":{"detectedCategory":"TOPIK_II"},
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[
                    {"title":"Reading","skill":"READING","testNo":1,"testClientId":"test-1","lessonCode":"R1",
                     "groups":[{"label":"R1.1","groupCode":"R1.1","questions":[
                       {"questionNo":1,"questionType":"SINGLE_CHOICE","prompt":"읽기 질문","points":1,
                        "options":["A","B"],"answer":{"value":"1"},"explanationVi":"Giải thích"}
                     ]}]},
                    {"title":"Listening","skill":"LISTENING","testNo":1,"testClientId":"test-1","lessonCode":"L1",
                     "groups":[{"label":"L1.1","groupCode":"L1.1","questions":[
                       {"questionNo":%d,"questionType":"SINGLE_CHOICE","prompt":"듣기 질문","points":1,
                        "options":["A","B"],"answer":{"value":"1"},"explanationVi":"Giải thích"}
                     ]}]}
                  ]
                }
                """.formatted(listeningQuestionNo);
    }

    private String writingDraftWithoutTask() {
        return writingDraft("""
                    {
                      "questionNo": 51,
                      "questionType": "ESSAY",
                      "prompt": "Prompt",
                      "answer": { "value": "" },
                      "explanationVi": "Explanation",
                      "points": 10,
                      "questionContent": {
                        "schemaVersion": "question-content-v1",
                        "options": [],
                        "blanks": [],
                        "speakingDelivery": {
                          "promptAudioReference": "/practice/materials/7/content",
                          "promptPlayLimit": 2,
                          "preparationSeconds": 30,
                          "responseSeconds": 60
                        }
                      }
                    }
                """);
    }

    private String readingQuestionWithStaleTask(String rawTaskValue) {
        return draft("READING", """
                    {
                      "questionNo": 1,
                      "questionType": "SINGLE_CHOICE",
                      "prompt": "Prompt",
                      "options": ["A", "B"],
                      "answer": { "value": "1" },
                      "explanationVi": "Explanation",
                      "points": 10,
                      "essayTaskType": %s
                    }
                """.formatted(rawTaskValue));
    }

    private String completeWritingDraft() {
        return writingDraft("""
                    %s,
                    %s,
                    {"questionNo":53,"questionType":"ESSAY","prompt":"Q53","points":30,"essayTaskType":"Q53"},
                    {"questionNo":54,"questionType":"ESSAY","prompt":"Q54","points":50,"essayTaskType":"Q54"}
                """.formatted(
                structuredWritingQuestion(
                        "Q51", 51, 10, "첫째 답", "둘째 답"),
                structuredWritingQuestion(
                        "Q52", 52, 10, "첫째 답", "둘째 답")));
    }

    private static String structuredWritingQuestion(
            String taskType,
            int questionNo,
            int points,
            String firstAnswer,
            String secondAnswer
    ) {
        String prefix = taskType.toLowerCase();
        return """
                {
                  "questionNo":%d,
                  "questionType":"ESSAY",
                  "prompt":"%s",
                  "points":%d,
                  "essayTaskType":"%s",
                  "questionContent":{
                    "schemaVersion":"question-content-v3",
                    "options":[],
                    "blanks":[],
                    "writingResponse":{
                      "responseSchemaVersion":"writing-blanks.v1",
                      "responseMode":"STRUCTURED_BLANKS",
                      "taskType":"%s",
                      "blanks":[
                        {"blankId":"%s-b1","ordinal":1,
                         "context":"첫 번째 문맥"},
                        {"blankId":"%s-b2","ordinal":2,
                         "context":"두 번째 문맥"}
                      ]
                    },
                    "languageTag":"ko"
                  },
                  "answerSpec":{
                    "schemaVersion":"answer-spec-v1",
                    "questionType":"ESSAY",
                    "correctOptionIds":[],
                    "blanks":[],
                    "scoringPolicyCode":"PROFILE_BASED",
                    "writingBlankAuthority":{
                      "contractVersion":"writing-blank-authority.v1",
                      "taskType":"%s",
                      "normalization":"NFC",
                      "whitespacePolicy":"TRIM_COLLAPSE",
                      "blanks":[
                        {"blankId":"%s-b1","ordinal":1,
                         "acceptedAnswers":[
                           {"text":"%s","equivalence":"EXACT",
                            "evidenceIds":[]}
                         ]},
                        {"blankId":"%s-b2","ordinal":2,
                         "acceptedAnswers":[
                           {"text":"%s","equivalence":"EXACT",
                            "evidenceIds":[]}
                         ]}
                      ]
                    }
                  }
                }
                """.formatted(
                questionNo,
                taskType,
                points,
                taskType,
                taskType,
                prefix,
                prefix,
                taskType,
                prefix,
                firstAnswer,
                prefix,
                secondAnswer);
    }

    private String speakingDraft(String questionType) {
        return draft("SPEAKING", """
                    {
                      "questionNo": 1,
                      "questionType": "%s",
                      "prompt": "Prompt",
                      "answer": { "value": "" },
                      "explanationVi": "Explanation",
                      "points": 10,
                      "questionContent": {
                        "schemaVersion": "question-content-v1",
                        "options": [],
                        "blanks": [],
                        "speakingDelivery": {
                          "promptAudioReference": "/practice/materials/7/content",
                          "promptPlayLimit": 2,
                          "preparationSeconds": 30,
                          "responseSeconds": 60
                        }
                      }
                    }
                """.formatted(questionType));
    }

    private String speakingV2Draft(String deliveryFields) {
        return draft("SPEAKING", """
                    {
                      "clientId": "q",
                      "questionNo": 1,
                      "questionType": "SPEAKING",
                      "prompt": "주말에 무엇을 합니까?",
                      "answer": { "value": "" },
                      "explanationVi": "Giải thích",
                      "points": 10,
                      "questionContent": {
                        "schemaVersion": "question-content-v2",
                        "speakingDelivery": {
                          %s,
                          "preparationSeconds": 30,
                          "responseSeconds": 60
                        }
                      }
                    }
                """.formatted(deliveryFields));
    }

    private String fillBlankDraft(String prompt) {
        return draft("READING", """
                    {
                      "questionNo": 1,
                      "questionType": "FILL_BLANK",
                      "prompt": "%s",
                      "explanationVi": "Giải thích",
                      "points": 10,
                      "questionContent": {
                        "schemaVersion": "question-content-v1",
                        "options": [],
                        "blanks": [{"id":"blank_1","prompt":"Thành phố"}]
                      },
                      "answerSpec": {
                        "schemaVersion": "answer-spec-v1",
                        "questionType": "FILL_BLANK",
                        "correctOptionIds": [],
                        "blanks": [{"blankId":"blank_1","acceptedValues":["서울"]}],
                        "scoringPolicyCode": "NORMALIZED_EXACT"
                      }
                    }
                """.formatted(prompt));
    }

    private static String tfngStrategyDraft(String correctValue) {
        return """
                {
                  "tests":[
                    {"clientId":"test-1","testNo":1,"title":"Test 1"}
                  ],
                  "sections":[{
                    "title":"Đọc",
                    "skill":"READING",
                    "testNo":1,
                    "testClientId":"test-1",
                    "lessonCode":"R1",
                    "groups":[{
                      "label":"1",
                      "groupCode":"R1.1",
                      "questions":[{
                        "questionNo":1,
                        "questionType":"TRUE_FALSE_NOT_GIVEN",
                        "prompt":"민수는 매일 공부합니까?",
                        "points":1,
                        "questionContent":{
                          "schemaVersion":"question-content-v1",
                          "options":[],
                          "blanks":[]
                        },
                        "answerSpec":{
                          "schemaVersion":"answer-spec-v1",
                          "questionType":"TRUE_FALSE_NOT_GIVEN",
                          "correctOptionIds":[],
                          "correctValue":"%s",
                          "blanks":[],
                          "scoringPolicyCode":"ALL_OR_NOTHING"
                        },
                        "explanationStrategy":{
                          "registryVersion":
                            "rl-explanation-strategy-registry-v2",
                          "strategyCode":"NOT_GIVEN_BOUNDARY",
                          "strategyVersion":"v1"
                        }
                      }]
                    }]
                  }]
                }
                """.formatted(correctValue);
    }

    private String writingDraft(String questionJson) {
        return draft("WRITING", questionJson);
    }

    private String draft(String skill, String questionJson) {
        String raw = """
        {
          "document": {
            "detectedCategory": "TOPIK_II"
          },
          "tests": [{"clientId":"test-1","testNo":1,"title":"Test 1"}],
          "sections": [
            {
              "title": "Writing",
              "skill": "%s",
              "testNo": 1,
              "testClientId": "test-1",
              "lessonCode": "%s",
              "groups": [
                {
                  "label": "1",
                  "groupCode": "%s.1",
                  "questions": [%s]
                }
              ]
            }
          ]
        }
        """.formatted(skill, lessonCode(skill), lessonCode(skill), questionJson);
        if (!"READING".equals(skill) && !"LISTENING".equals(skill)) {
            return raw;
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            mapper.readTree(raw);
            for (com.fasterxml.jackson.databind.JsonNode section :
                    root.path("sections")) {
                for (com.fasterxml.jackson.databind.JsonNode group :
                        section.path("groups")) {
                    for (com.fasterxml.jackson.databind.JsonNode node :
                            group.path("questions")) {
                        com.fasterxml.jackson.databind.node.ObjectNode question =
                                (com.fasterxml.jackson.databind.node.ObjectNode)
                                        node;
                        String type = question.path("questionType").asText();
                        String strategyCode = switch (type) {
                            case "FILL_BLANK", "GAP_FILL" ->
                                    "CONSTRAINTS_AND_EVIDENCE";
                            case "TRUE_FALSE_NOT_GIVEN", "TFNG" ->
                                    "CLAIM_EVIDENCE_RELATION";
                            default -> "EVIDENCE_ONLY";
                        };
                        com.fasterxml.jackson.databind.node.ObjectNode strategy =
                                question.putObject("explanationStrategy");
                        strategy.put(
                                "registryVersion",
                                "rl-explanation-strategy-registry-v1");
                        strategy.put("strategyCode", strategyCode);
                        strategy.put("strategyVersion", "v1");
                    }
                }
            }
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể tạo fixture strategy R/L.", exception);
        }
    }

    private static String lessonCode(String skill) {
        return switch (skill) {
            case "LISTENING" -> "L1";
            case "WRITING" -> "W1";
            case "SPEAKING" -> "S1";
            default -> "R1";
        };
    }
}
