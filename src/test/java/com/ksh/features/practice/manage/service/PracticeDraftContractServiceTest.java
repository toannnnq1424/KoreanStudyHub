package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PracticeDraftContractServiceTest {

    @Test
    void speakingTimingAndPromptAudioAreNormalizedIntoCanonicalQuestionContent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService service = new PracticeDraftContractService(
                objectMapper,
                new AssessmentAuthoringCatalogService(new PracticeContentRules()),
                resolver,
                codec);

        JsonNode root = objectMapper.readTree(service.normalize(legacySpeakingDraft(), "MANUAL").json());
        JsonNode question = root.path("sections").get(0)
                .path("groups").get(0)
                .path("questions").get(0);
        JsonNode delivery = question.path("questionContent").path("speakingDelivery");

        assertEquals("/practice/materials/9/content", delivery.path("promptAudioReference").asText());
        assertEquals(3, delivery.path("promptPlayLimit").asInt());
        assertEquals(15, delivery.path("preparationSeconds").asInt());
        assertEquals(45, delivery.path("responseSeconds").asInt());
        assertEquals(3, question.path("speakingPromptPlayLimit").asInt());
        assertEquals(15, question.path("prepTimeSeconds").asInt());
        assertEquals(45, question.path("respTimeSeconds").asInt());
    }

    private static String legacySpeakingDraft() {
        return """
                {
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[{
                    "title":"Speaking",
                    "skill":"SPEAKING",
                    "testNo":1,
                    "testClientId":"test-1",
                    "lessonCode":"S1",
                    "groups":[{
                      "label":"S1.1",
                      "groupCode":"S1.1",
                      "questions":[{
                        "questionNo":1,
                        "questionType":"SPEAKING",
                        "prompt":"자기소개를 해 보십시오.",
                        "points":100,
                        "speakingPromptAudioUrl":"/practice/materials/9/content",
                        "speakingPromptPlayLimit":3,
                        "prepTimeSeconds":15,
                        "respTimeSeconds":45
                      }]
                    }]
                  }]
                }
                """;
    }
}
