package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.SpeakingPromptDelivery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        assertTrue(serialized.contains("question-content-v3"));
        assertTrue(serialized.contains("\"languageTag\":\"ko\""));
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

    @Test
    void speakingPreviewUsesBackendPresentationForAllThreeV2Branches()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec =
                new AssessmentContractCodec(objectMapper, resolver);
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(
                        new PracticeContentRules());
        PracticeDraftContractService contract =
                new PracticeDraftContractService(
                        objectMapper, catalog, resolver, codec);
        PracticeDraftPreviewService service =
                new PracticeDraftPreviewService(
                        contract, codec, resolver, objectMapper);

        var questions = service.preview(speakingV2Draft())
                .sections().get(0)
                .groups().get(0)
                .questions();

        assertTrue(questions.get(0).speakingPresentation()
                .steps().contains(
                        SpeakingPromptDelivery.Step.PROMPT_PLAYBACK));
        assertTrue(questions.get(0).speakingPresentation()
                .promptText() == null);
        assertTrue(questions.get(1).speakingPresentation()
                .promptVisibleBeforePlayback());
        assertTrue(questions.get(1).speakingPresentation()
                .steps().get(0)
                == SpeakingPromptDelivery.Step.PROMPT_PLAYBACK);
        assertTrue(questions.get(2).speakingPresentation()
                .promptPlayLimit() == null);
        assertTrue(questions.get(2).speakingPresentation()
                .steps().equals(java.util.List.of(
                        SpeakingPromptDelivery.Step.PREPARATION,
                        SpeakingPromptDelivery.Step.RECORDING)));
    }

    @Test
    void invalidExplicitV2PreviewNeverDowngradesToLegacyAudio()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec =
                new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService contract =
                new PracticeDraftContractService(
                        objectMapper,
                        new AssessmentAuthoringCatalogService(
                                new PracticeContentRules()),
                        resolver,
                        codec);
        PracticeDraftPreviewService service =
                new PracticeDraftPreviewService(
                        contract, codec, resolver, objectMapper);

        IllegalArgumentException rejection = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(invalidSpeakingV2Draft()));

        assertTrue(rejection.getMessage().contains("Speaking v2"));
    }

    @Test
    void structuredWritingBlankPreviewNeverLeaksAcceptedAnswers() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec =
                new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService contract =
                new PracticeDraftContractService(
                        objectMapper,
                        new AssessmentAuthoringCatalogService(
                                new PracticeContentRules()),
                        resolver,
                        codec);
        PracticeDraftPreviewService service =
                new PracticeDraftPreviewService(
                        contract, codec, resolver, objectMapper);

        String serialized = objectMapper.writeValueAsString(
                service.preview(structuredWritingBlankDraft()));

        assertTrue(serialized.contains("writing-blanks.v1"));
        assertTrue(serialized.contains("STRUCTURED_BLANKS"));
        assertTrue(serialized.contains("q51-b1"));
        assertTrue(serialized.contains("q51-b2"));
        assertFalse(serialized.contains("writingBlankAuthority"));
        assertFalse(serialized.contains("acceptedAnswers"));
        assertFalse(serialized.contains("SECRET_ACCEPTED/ANSWER;LITERAL"));
        assertFalse(serialized.contains("answerSpec"));
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

    private static String speakingV2Draft() {
        return """
                {
                  "document":{
                    "title":"Speaking",
                    "examTemplateCode":"CUSTOM_FLEXIBLE"
                  },
                  "sections":[{
                    "skill":"SPEAKING",
                    "groups":[{
                      "label":"Nói",
                      "questions":[
                        {
                          "clientId":"audio",
                          "questionType":"SPEAKING",
                          "prompt":"SECRET_LECTURER_TRANSCRIPT",
                          "points":1,
                          "questionContent":{
                            "schemaVersion":"question-content-v2",
                            "speakingDelivery":{
                              "inputType":"audio_upload",
                              "deliveryMode":"audio_only",
                              "promptAudioReference":"/practice/manage/drafts/10/questions/audio/speaking-prompt/media/original",
                              "audioOrigin":"teacher_upload",
                              "promptPlayLimit":1,
                              "preparationSeconds":30,
                              "responseSeconds":60
                            }
                          }
                        },
                        {
                          "clientId":"tts",
                          "questionType":"SPEAKING",
                          "prompt":"자기소개를 하세요.",
                          "points":1,
                          "questionContent":{
                            "schemaVersion":"question-content-v2",
                            "speakingDelivery":{
                              "inputType":"manual_text",
                              "deliveryMode":"text_and_audio",
                              "promptAudioReference":"/practice/manage/drafts/10/questions/tts/speaking-prompt/media/generated",
                              "audioOrigin":"ai_tts",
                              "promptPlayLimit":2,
                              "preparationSeconds":20,
                              "responseSeconds":50
                            }
                          }
                        },
                        {
                          "clientId":"text",
                          "questionType":"SPEAKING",
                          "prompt":"주말에 무엇을 합니까?",
                          "points":1,
                          "questionContent":{
                            "schemaVersion":"question-content-v2",
                            "speakingDelivery":{
                              "inputType":"manual_text",
                              "deliveryMode":"text_only",
                              "audioOrigin":"none",
                              "preparationSeconds":10,
                              "responseSeconds":40
                            }
                          }
                        }
                      ]
                    }]
                  }]
                }
                """;
    }

    private static String invalidSpeakingV2Draft() {
        return """
                {
                  "document":{
                    "title":"Speaking",
                    "examTemplateCode":"CUSTOM_FLEXIBLE"
                  },
                  "sections":[{
                    "skill":"SPEAKING",
                    "groups":[{
                      "label":"Nói",
                      "audioUrl":"/practice/materials/99/content",
                      "questions":[{
                        "clientId":"invalid-v2",
                        "questionType":"SPEAKING",
                        "prompt":"질문입니다.",
                        "points":1,
                        "questionContent":{
                          "schemaVersion":"question-content-v2",
                          "speakingDelivery":{
                            "inputType":"manual_text",
                            "deliveryMode":"text_only",
                            "promptAudioReference":"/practice/materials/99/content",
                            "audioOrigin":"none",
                            "promptPlayLimit":1,
                            "preparationSeconds":30,
                            "responseSeconds":60
                          }
                        }
                      }]
                    }]
                  }]
                }
                """;
    }

    private static String structuredWritingBlankDraft() {
        return """
                {
                  "document":{
                    "title":"Q51 structured",
                    "examTemplateCode":"CUSTOM_FLEXIBLE"
                  },
                  "sections":[{
                    "skill":"WRITING",
                    "groups":[{
                      "label":"Viết câu 51",
                      "questions":[{
                        "clientId":"q51",
                        "questionType":"ESSAY",
                        "essayTaskType":"Q51",
                        "prompt":"문맥에 맞게 두 빈칸을 완성하십시오.",
                        "points":10,
                        "questionContent":{
                          "schemaVersion":"question-content-v3",
                          "languageTag":"ko",
                          "writingResponse":{
                            "responseMode":"STRUCTURED_BLANKS",
                            "responseSchemaVersion":"writing-blanks.v1",
                            "taskType":"Q51",
                            "blanks":[
                              {"blankId":"q51-b1","ordinal":1,"context":"첫째 빈칸"},
                              {"blankId":"q51-b2","ordinal":2,"context":"둘째 빈칸"}
                            ]
                          }
                        },
                        "answerSpec":{
                          "schemaVersion":"answer-spec-v1",
                          "questionType":"ESSAY",
                          "scoringPolicyCode":"PROFILE_BASED",
                          "writingBlankAuthority":{
                            "responseSchemaVersion":"writing-blanks.v1",
                            "taskType":"Q51",
                            "normalizationPolicy":{
                              "unicodeForm":"NFC",
                              "whitespacePolicy":"TRIM_AND_COLLAPSE"
                            },
                            "blanks":[
                              {
                                "blankId":"q51-b1",
                                "ordinal":1,
                                "acceptedAnswers":[
                                  {
                                    "text":"SECRET_ACCEPTED/ANSWER;LITERAL",
                                    "equivalence":"EXACT"
                                  }
                                ]
                              },
                              {
                                "blankId":"q51-b2",
                                "ordinal":2,
                                "acceptedAnswers":[
                                  {
                                    "text":"두 번째",
                                    "equivalence":"EXACT"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                      }]
                    }]
                  }]
                }
                """;
    }
}
