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
}
