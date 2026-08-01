package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PracticeDraftContractServiceTest {

    @Test
    void saveNormalizationRejectsNotGivenStrategyForFalseAnswer() {
        PracticeDraftContractService service = service();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.normalize(tfngDraft("FALSE"), "MANUAL"));

        service.normalize(tfngDraft("NOT_GIVEN"), "MANUAL");
    }

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

        assertEquals("question-content-v3",
                question.path("questionContent").path("schemaVersion").asText());
        assertEquals("ko", question.path("questionContent").path("languageTag").asText());
        assertEquals("ko", question.path("promptLanguageTag").asText());
        assertEquals("/practice/materials/9/content", delivery.path("promptAudioReference").asText());
        assertEquals("audio_upload", delivery.path("inputType").asText());
        assertEquals("audio_only", delivery.path("deliveryMode").asText());
        assertEquals("teacher_upload", delivery.path("audioOrigin").asText());
        assertEquals(3, delivery.path("promptPlayLimit").asInt());
        assertEquals(15, delivery.path("preparationSeconds").asInt());
        assertEquals(45, delivery.path("responseSeconds").asInt());
        assertEquals("/practice/materials/9/content",
                question.path("speakingPromptAudioUrl").asText());
        assertEquals(3, question.path("speakingPromptPlayLimit").asInt());
        assertEquals(15, question.path("prepTimeSeconds").asInt());
        assertEquals(45, question.path("respTimeSeconds").asInt());
    }

    @Test
    void editableQuestionPersistsLecturerSelectedPromptLanguageRegion() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService service = new PracticeDraftContractService(
                objectMapper,
                new AssessmentAuthoringCatalogService(new PracticeContentRules()),
                resolver,
                codec);

        JsonNode root = objectMapper.readTree(service.normalize("""
                {
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[{
                    "skill":"READING","testNo":1,"testClientId":"test-1","lessonCode":"R1",
                    "groups":[{"groupCode":"R1.1","questions":[{
                      "questionNo":1,
                      "questionType":"SINGLE_CHOICE",
                      "prompt":"Chọn câu trả lời đúng.",
                      "promptLanguageTag":"vi",
                      "options":["Một","Hai"],
                      "answerKey":"1"
                    }]}]
                  }]
                }
                """, "MANUAL").json());
        JsonNode question = root.path("sections").get(0)
                .path("groups").get(0).path("questions").get(0);

        assertEquals("question-content-v3",
                question.path("questionContent").path("schemaVersion").asText());
        assertEquals("vi", question.path("questionContent").path("languageTag").asText());
        assertEquals("vi", question.path("promptLanguageTag").asText());
    }

    @Test
    void writingTaskForcesItsQuestionNumberAndFixedPoints() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService service = new PracticeDraftContractService(
                objectMapper,
                new AssessmentAuthoringCatalogService(new PracticeContentRules()),
                resolver,
                codec);

        JsonNode root = objectMapper.readTree(service.normalize("""
                {
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[{
                    "skill":"WRITING","testNo":1,"testClientId":"test-1","lessonCode":"W1",
                    "groups":[{"groupCode":"W1.1","questions":[{
                      "questionNo":1,"questionType":"ESSAY","prompt":"쓰기",
                      "points":999,"essayTaskType":"Q54"
                    }]}]
                  }]
                }
                """, "MANUAL").json());
        JsonNode question = root.path("sections").get(0)
                .path("groups").get(0).path("questions").get(0);

        assertEquals("ESSAY", question.path("questionType").asText());
        assertEquals(54, question.path("questionNo").asInt());
        assertEquals(0, question.path("points").decimalValue()
                .compareTo(java.math.BigDecimal.valueOf(50)));
    }

    @Test
    void writingSectionTotalPointsIsRebuiltFromCanonicalTaskWeights() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService service = new PracticeDraftContractService(
                objectMapper,
                new AssessmentAuthoringCatalogService(new PracticeContentRules()),
                resolver,
                codec);

        JsonNode root = objectMapper.readTree(service.normalize("""
                {
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[{
                    "skill":"WRITING","testNo":1,"testClientId":"test-1",
                    "lessonCode":"W1","totalPoints":999,
                    "groups":[{"groupCode":"W1.1","questions":[
                      {"questionType":"ESSAY","prompt":"Q51","points":1,"essayTaskType":"Q51"},
                      {"questionType":"ESSAY","prompt":"Q52","points":1,"essayTaskType":"Q52"},
                      {"questionType":"ESSAY","prompt":"Q53","points":1,"essayTaskType":"Q53"},
                      {"questionType":"ESSAY","prompt":"Q54","points":1,"essayTaskType":"Q54"}
                    ]}]
                  }]
                }
                """, "MANUAL").json());

        JsonNode section = root.path("sections").get(0);
        assertEquals(0, section.path("totalPoints").decimalValue()
                .compareTo(java.math.BigDecimal.valueOf(100)));
    }

    @Test
    void legacyQ51IsExplicitlyReadOnlyAndDelimiterTextIsNotSplit()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        PracticeDraftContractService service = service(objectMapper);

        JsonNode question = firstQuestion(objectMapper.readTree(
                service.normalize("""
                        {
                          "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                          "sections":[{
                            "skill":"WRITING","testNo":1,
                            "testClientId":"test-1","lessonCode":"W1",
                            "groups":[{"groupCode":"W1.1","questions":[{
                              "questionType":"ESSAY",
                              "essayTaskType":"Q51",
                              "prompt":"슬래시 / 와 세미콜론 ; 을 보존합니다.",
                              "answerKey":"A/B;C"
                            }]}]
                          }]
                        }
                        """, "MANUAL").json()));

        assertEquals(
                "LEGACY_ESSAY_READ_ONLY",
                question.path("writingCompatibilityMode").asText());
        assertEquals("A/B;C", question.path("answerKey").asText());
        assertFalse(question.path("questionContent")
                .path("writingResponse").isObject());
    }

    @Test
    void structuredQ51AuthorityIsPreservedWithoutDelimiterParsing()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        PracticeDraftContractService service = service(objectMapper);

        JsonNode question = firstQuestion(objectMapper.readTree(
                service.normalize(structuredQ51Draft(), "MANUAL").json()));

        assertEquals(
                "STRUCTURED_BLANKS",
                question.path("writingCompatibilityMode").asText());
        assertEquals(
                "값/표현;그대로",
                question.path("answerSpec")
                        .path("writingBlankAuthority")
                        .path("blanks").get(0)
                        .path("acceptedAnswers").get(0)
                        .path("text").asText());
        assertEquals(
                2,
                question.path("questionContent")
                        .path("writingResponse")
                        .path("blanks").size());
    }

    private static PracticeDraftContractService service(
            ObjectMapper objectMapper
    ) {
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec =
                new AssessmentContractCodec(objectMapper, resolver);
        return new PracticeDraftContractService(
                objectMapper,
                new AssessmentAuthoringCatalogService(
                        new PracticeContentRules()),
                resolver,
                codec);
    }

    private static JsonNode firstQuestion(JsonNode root) {
        return root.path("sections").get(0)
                .path("groups").get(0)
                .path("questions").get(0);
    }

    private static PracticeDraftContractService service() {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec =
                new AssessmentContractCodec(objectMapper, resolver);
        return new PracticeDraftContractService(
                objectMapper,
                new AssessmentAuthoringCatalogService(
                        new PracticeContentRules()),
                resolver,
                codec);
    }

    private static String tfngDraft(String correctValue) {
        return """
                {
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[{
                    "skill":"READING","testNo":1,
                    "testClientId":"test-1","lessonCode":"R1",
                    "groups":[{"groupCode":"R1.1","questions":[{
                      "questionNo":1,
                      "questionType":"TRUE_FALSE_NOT_GIVEN",
                      "prompt":"민수는 매일 공부합니까?",
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
                        "registryVersion":"rl-explanation-strategy-registry-v2",
                        "strategyCode":"NOT_GIVEN_BOUNDARY",
                        "strategyVersion":"v1"
                      }
                    }]}]
                  }]
                }
                """.formatted(correctValue);
    }

    private static String structuredQ51Draft() {
        return """
                {
                  "tests":[{"clientId":"test-1","testNo":1,"title":"Test 1"}],
                  "sections":[{
                    "skill":"WRITING","testNo":1,
                    "testClientId":"test-1","lessonCode":"W1",
                    "groups":[{"groupCode":"W1.1","questions":[{
                      "questionType":"ESSAY",
                      "essayTaskType":"Q51",
                      "prompt":"두 칸을 완성하십시오.",
                      "questionContent":{
                        "schemaVersion":"question-content-v3",
                        "options":[],
                        "blanks":[],
                        "writingResponse":{
                          "responseSchemaVersion":"writing-blanks.v1",
                          "responseMode":"STRUCTURED_BLANKS",
                          "taskType":"Q51",
                          "blanks":[
                            {"blankId":"q51-b1","ordinal":1,
                             "context":"첫 번째 문맥"},
                            {"blankId":"q51-b2","ordinal":2,
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
                          "taskType":"Q51",
                          "normalization":"NFC",
                          "whitespacePolicy":"TRIM_COLLAPSE",
                          "blanks":[
                            {"blankId":"q51-b1","ordinal":1,
                             "acceptedAnswers":[
                               {"text":"값/표현;그대로",
                                "equivalence":"EXACT","evidenceIds":[]}
                             ]},
                            {"blankId":"q51-b2","ordinal":2,
                             "acceptedAnswers":[
                               {"text":"두 번째 답",
                                "equivalence":"EXACT","evidenceIds":[]}
                             ]}
                          ]
                        }
                      }
                    }]}]
                  }]
                }
                """;
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
