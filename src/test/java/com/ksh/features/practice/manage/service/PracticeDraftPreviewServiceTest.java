package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeDraftPreviewServiceTest {

    @Test
    void deliveryPreviewOmitsAnswersProfilesExplanationsAndListeningTranscript() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(new PracticeContentRules());
        PracticeDraftContractService contract = new PracticeDraftContractService(
                objectMapper, catalog, resolver, codec);
        PracticeDraftPreviewService service = new PracticeDraftPreviewService(
                contract, codec, resolver, objectMapper);

        String serialized = objectMapper.writeValueAsString(service.preview(draftJson()));

        assertTrue(serialized.contains("question-content-v1"));
        assertTrue(serialized.contains("Nghe va chon"));
        assertTrue(serialized.contains("\"questionNo\":1"));
        assertTrue(serialized.contains("\"options\":[{\"id\":\"opt_1\",\"text\":\"A\""));
        assertTrue(serialized.contains("/uploads/practice-audio/audio.mp3"));
        assertTrue(serialized.contains("/uploads/questions/legacy-q1.png"));
        assertTrue(serialized.contains("/uploads/questions/legacy-q1.mp3"));
        assertFalse(serialized.contains("SECRET_CORRECT_ANSWER"));
        assertFalse(serialized.contains("SECRET_EXPLANATION"));
        assertFalse(serialized.contains("SECRET_TRANSCRIPT"));
        assertFalse(serialized.contains("SECRET_PROMPT_PROFILE"));
        assertFalse(serialized.contains("evil.example"));
        assertFalse(serialized.contains("answerSpec"));
        assertFalse(serialized.contains("correctOptionIds"));
    }

    private static String draftJson() {
        return """
                {
                  "document": {"title":"De nghe", "examTemplateCode":"CUSTOM_FLEXIBLE"},
                  "sections": [{
                    "skill":"LISTENING",
                    "groups":[{
                      "label":"Nhom nghe",
                      "instruction":"Nghe va chon",
                      "stimulus":{
                        "type":"LISTENING_AUDIO",
                        "transcriptText":"SECRET_TRANSCRIPT",
                        "mediaReference":"/uploads/practice-audio/audio.mp3",
                        "imageReference":"//evil.example/tracker.png",
                        "provenance":{"source":"MANUAL","approved":true}
                      },
                      "questions":[{
                        "questionType":"SINGLE_CHOICE",
                        "prompt":"Nghe va chon",
                        "points":1,
                        "imageUrl":"/uploads/questions/legacy-q1.png",
                        "audioUrl":"/uploads/questions/legacy-q1.mp3",
                        "options":[{"id":"opt_1","text":"A"},{"id":"opt_2","text":"B"}],
                        "answerKey":"SECRET_CORRECT_ANSWER",
                        "explanationVi":"SECRET_EXPLANATION",
                        "answerSpec":{
                          "schemaVersion":"answer-spec-v1",
                          "questionType":"SINGLE_CHOICE",
                          "correctOptionIds":["opt_1"],
                          "scoringPolicyCode":"ALL_OR_NOTHING",
                          "promptProfileCode":"SECRET_PROMPT_PROFILE"
                        },
                        "questionContent":{
                          "schemaVersion":"question-content-v1",
                          "options":[
                            {"id":"opt_1","text":"A","imageReference":"/uploads/options/a.png"},
                            {"id":"opt_2","text":"B","imageReference":"javascript:alert(1)"}
                          ]
                        }
                      }]
                    }]
                  }]
                }
                """;
    }
}
