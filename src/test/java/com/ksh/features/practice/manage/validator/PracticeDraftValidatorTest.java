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
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "durationMinutes": 40,
              "groups": [
                {
                  "label": "1-2",
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
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "groups": [
                {
                  "label": "1",
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
        assertTrue(result.messages().stream().anyMatch(m -> m.content().contains("ít nhất 2 đáp án")));
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
    public void writingEssayMissingTaskIsWarningOnly() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithoutTask());

        assertFalse(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "WARNING".equals(m.type())
                        && m.content().contains("chưa có loại bài rõ ràng")));
    }

    @Test
    public void writingEssayNullTaskIsWarningOnly() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("null"));

        assertFalse(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "WARNING".equals(m.type())
                        && m.content().contains("chưa có loại bài rõ ràng")));
    }

    @Test
    public void writingEssayGeneralTaskIsValid() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("\"GENERAL\""));

        assertFalse(result.hasBlocking());
        assertFalse(result.messages().stream().anyMatch(m -> m.content().contains("Writing")));
    }

    @Test
    public void writingEssayInvalidTaskIsBlocking() {
        PracticeDraftValidator.ValidationResult result = validator.validate(writingDraftWithTask("\"Q51_52\""));

        assertTrue(result.hasBlocking());
        assertTrue(result.messages().stream().anyMatch(m ->
                "BLOCKING".equals(m.type())
                        && m.content().equals("Loại bài Writing không hợp lệ.")));
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
        PracticeDraftValidator.ValidationResult result = validator.validate(readingEssayWithTask("\"NOT_A_TASK\""));

        assertFalse(result.hasBlocking());
        assertFalse(result.messages().stream().anyMatch(m -> m.content().contains("Writing")));
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

    private String writingDraftWithTask(String rawTaskValue) {
        return writingDraft("""
                    {
                      "questionNo": 1,
                      "questionType": "ESSAY",
                      "prompt": "Prompt",
                      "answer": { "value": "" },
                      "explanationVi": "Explanation",
                      "points": 10,
                      "essayTaskType": %s
                    }
                """.formatted(rawTaskValue));
    }

    private String writingDraftWithoutTask() {
        return writingDraft("""
                    {
                      "questionNo": 1,
                      "questionType": "ESSAY",
                      "prompt": "Prompt",
                      "answer": { "value": "" },
                      "explanationVi": "Explanation",
                      "points": 10
                    }
                """);
    }

    private String readingEssayWithTask(String rawTaskValue) {
        return draft("READING", """
                    {
                      "questionNo": 1,
                      "questionType": "ESSAY",
                      "prompt": "Prompt",
                      "answer": { "value": "" },
                      "explanationVi": "Explanation",
                      "points": 10,
                      "essayTaskType": %s
                    }
                """.formatted(rawTaskValue));
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
          "sections": [
            {
              "title": "Writing",
              "skill": "%s",
              "groups": [
                {
                  "label": "1",
                  "questions": [%s]
                }
              ]
            }
          ]
        }
        """.formatted(skill, questionJson);
    }
}
