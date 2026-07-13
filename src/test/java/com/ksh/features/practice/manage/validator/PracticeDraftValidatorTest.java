package com.ksh.features.practice.manage.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PracticeDraftValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PracticeDraftValidator validator = new PracticeDraftValidator(mapper);

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
                      "points": 10
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
                    {"questionNo":51,"questionType":"ESSAY","prompt":"Q51","points":10,"essayTaskType":"Q51"},
                    {"questionNo":52,"questionType":"ESSAY","prompt":"Q52","points":10,"essayTaskType":"Q52"},
                    {"questionNo":53,"questionType":"ESSAY","prompt":"Q53","points":30,"essayTaskType":"Q53"},
                    {"questionNo":54,"questionType":"ESSAY","prompt":"Q54","points":50,"essayTaskType":"Q54"}
                """);
    }

    private String speakingDraft(String questionType) {
        return draft("SPEAKING", """
                    {
                      "questionNo": 1,
                      "questionType": "%s",
                      "prompt": "Prompt",
                      "answer": { "value": "" },
                      "explanationVi": "Explanation",
                      "points": 10
                    }
                """.formatted(questionType));
    }

    private String writingDraft(String questionJson) {
        return draft("WRITING", questionJson);
    }

    private String draft(String skill, String questionJson) {
        return """
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
