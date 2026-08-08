package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.TestPracticeStructuredGenerationPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleSpeakingEvaluationClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingEvaluationPromptBuilder promptBuilder = new SpeakingEvaluationPromptBuilder(objectMapper);

    @Test
    void sendsTypedFixedSchemaRequestThroughPracticePort() {
        TestPracticeStructuredGenerationPort port = port();
        OpenAiCompatibleSpeakingEvaluationClient client = client(properties("secret-key",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "models/gemini-2.5-flash"), port);

        client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertThat(port.lastRequest().operation())
                .isEqualTo("speaking-transcript-evaluation");
        assertThat(port.lastRequest().systemInstruction())
                .contains("Korean Study Hub");
        assertThat(port.lastRequest().responseSchema().toString())
                .contains("S_CONTENT_TASK_FULFILLMENT");
        assertThat((java.util.List<?>) port.lastRequest().input()
                .get("allowed_subcriteria"))
                .hasSize(16);
        assertThat(port.lastRequest().input().toString())
                .contains(
                        "S_CONTENT_RELEVANCE",
                        "S_GRAMMAR_HONORIFIC_REGISTER",
                        "S_VOCAB_REPETITION_CONTROL",
                        "S_COHERENCE_DISCOURSE_MARKERS")
                .doesNotContain(
                        "S_FLUENCY_CONTINUITY",
                        "S_PRONUNCIATION_INTELLIGIBILITY");
        assertThat(port.lastRequest().authority().strategyCode())
                .isEqualTo("TRANSCRIPT_ONLY");
    }

    @Test
    void sendsGovernedQuestionImageAsTypedEvidence() {
        TestPracticeStructuredGenerationPort port = port();
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithPort(port);
        AiImageEvidence image = new AiImageEvidence(
                8L, "image/png", "data:image/png;base64,cG5n", "image-sha", 3);

        client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false, image));

        assertThat(port.lastRequest().images()).singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.role()).isEqualTo("QUESTION_IMAGE");
                    assertThat(evidence.dataUrl())
                            .isEqualTo("data:image/png;base64,cG5n");
                    assertThat(evidence.sha256()).isEqualTo("image-sha");
                });
        assertThat(port.lastRequest().input().toString())
                .doesNotContain("audio/webm");
    }

    @Test
    void unavailableCapabilityMapsEvaluationUnavailableWithoutProviderCall() {
        TestPracticeStructuredGenerationPort port =
                TestPracticeStructuredGenerationPort.unavailable(
                        "openai-primary",
                        "assessment-model");
        OpenAiCompatibleSpeakingEvaluationClient client = client(properties("",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "models/gemini-2.5-flash"), port);

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, result.failureStatus());
        assertEquals("MISSING_API_KEY", result.errorCategory());
        assertThat(result.retryable()).isFalse();
        assertThat(port.calls()).isZero();
    }

    @Test
    void http429And503MapRetryableTrue() {
        assertHttp(HttpStatus.TOO_MANY_REQUESTS, true);
        assertHttp(HttpStatus.SERVICE_UNAVAILABLE, true);
    }

    @Test
    void http400401413415MapRetryableFalse() {
        assertHttp(HttpStatus.BAD_REQUEST, false);
        assertHttp(HttpStatus.UNAUTHORIZED, false);
        assertHttp(HttpStatus.PAYLOAD_TOO_LARGE, false);
        assertHttp(HttpStatus.UNSUPPORTED_MEDIA_TYPE, false);
    }

    @Test
    void transportTimeoutMapsRetryableTrue() {
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithPort(
                TestPracticeStructuredGenerationPort.throwing(
                        "openai-primary",
                        "assessment-model",
                        new ResourceAccessException("timeout")));

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, result.failureStatus());
        assertEquals("PROVIDER_TRANSPORT_ERROR", result.errorCategory());
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void strictPortContractFailureMapsContractFailure() {
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithPort(
                TestPracticeStructuredGenerationPort.throwing(
                        "openai-primary",
                        "assessment-model",
                        new PracticeAiContractException(
                                "PROVIDER_MALFORMED_STRUCTURED_OUTPUT",
                                false)));

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED, result.failureStatus());
        assertEquals(
                "PROVIDER_MALFORMED_STRUCTURED_OUTPUT",
                result.errorCategory());
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void providerContentJsonParsesToEvaluationJson() {
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithPort(
                port());

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertThat(result.success()).isTrue();
        assertThat(result.evaluationJson().path("evaluation_status").asText()).isEqualTo("EVALUATED");
        assertThat(result.toString()).doesNotContain("저는 학생").doesNotContain("secret-key");
    }

    @Test
    void noRealProviderCallCanHappenWithFakePort() {
        TestPracticeStructuredGenerationPort port = port();
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithPort(port);

        client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertThat(port.calls()).isEqualTo(1);
    }

    @Test
    void textChatClientRejectsReservedDirectAudioCapabilityWithoutCallingProvider() {
        TestPracticeStructuredGenerationPort port = port();
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithPort(port);

        SpeakingEvaluationProviderResult result = client.evaluate(requestWithCapability(
                SpeakingEvaluatorCapability.AUDIO_DIRECT_FULL_RESERVED,
                SpeakingEvidenceMode.DIRECT_AUDIO_AND_TRANSCRIPT,
                SpeakingEvaluatorCapability.AUDIO_DIRECT_FULL_RESERVED.contractVersion()));

        assertThat(result.success()).isFalse();
        assertThat(result.failureStatus()).isEqualTo(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED);
        assertThat(result.errorCategory()).isEqualTo("UNSUPPORTED_EVALUATOR_CAPABILITY");
        assertThat(port.calls()).isZero();
    }

    @Test
    void transcriptScorerTransportOmitsAllLearnerAudioMetadata() {
        TestPracticeStructuredGenerationPort port = port();
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithPort(port);
        SpeakingEvaluationRequest base =
                SpeakingEvaluationPromptBuilderTest.request(false);
        SpeakingEvaluationRequest requestWithSensitiveMediaMetadata =
                new SpeakingEvaluationRequest(
                        base.attemptId(), base.questionId(),
                        base.questionVersionId(), base.promptContext(),
                        base.promptContextFingerprint(),
                        base.promptContextContractIdentity(),
                        base.questionText(), base.targetLevel(),
                        base.expectedAnswerGuidance(), base.imageEvidence(),
                        9_876_543_210L, 123_456_789L,
                        "audio/x-pre15-must-not-leave-process",
                        98_765_432L, 7_654_321L,
                        base.transcriptionProvider(),
                        base.transcriptionModel(), base.language(),
                        base.transcript(), base.normalizedTranscript(),
                        base.actuallyHeardTranscript(),
                        base.interpretedIntent(),
                        base.transcriptConfidence(), base.textFallback(),
                        base.promptVersion(), base.rubricVersion(),
                        base.schemaVersion(), base.policyBundleId(),
                        SpeakingEvaluatorCapability
                                .TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                        SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                        SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION);

        SpeakingEvaluationProviderResult result =
                client.evaluate(requestWithSensitiveMediaMetadata);

        assertThat(result.success()).isTrue();
        assertThat(port.calls()).isEqualTo(1);
        assertThat(port.lastRequest().authority().strategyCode())
                .isEqualTo("TRANSCRIPT_ONLY");
        assertThat(port.lastRequest().images())
                .allMatch(image -> "QUESTION_IMAGE".equals(image.role()));
        assertThat(port.lastRequest().input()).doesNotContainKeys(
                "audio_media_id", "media_version", "mime_type",
                "byte_size", "duration_ms", "audio", "audio_url");
        assertThat(port.lastRequest().toString()).doesNotContain(
                "9876543210", "123456789",
                "audio/x-pre15-must-not-leave-process",
                "98765432", "7654321");
    }

    @Test
    void stalePolicyBundleOrVersionCannotCallProvider() {
        TestPracticeStructuredGenerationPort port = port();
        OpenAiCompatibleSpeakingEvaluationClient client =
                clientWithPort(port);
        SpeakingEvaluationRequest base =
                SpeakingEvaluationPromptBuilderTest.request(false);

        SpeakingEvaluationProviderResult stale = client.evaluate(
                new SpeakingEvaluationRequest(
                        base.attemptId(), base.questionId(),
                        base.questionVersionId(), base.promptContext(),
                        base.promptContextFingerprint(),
                        base.promptContextContractIdentity(),
                        base.questionText(), base.targetLevel(),
                        base.expectedAnswerGuidance(), base.imageEvidence(),
                        base.audioMediaId(), base.mediaVersion(),
                        base.mimeType(), base.byteSize(), base.durationMs(),
                        base.transcriptionProvider(),
                        base.transcriptionModel(), base.language(),
                        base.transcript(), base.normalizedTranscript(),
                        base.actuallyHeardTranscript(),
                        base.interpretedIntent(),
                        base.transcriptConfidence(), base.textFallback(),
                        "stale-prompt", base.rubricVersion(),
                        base.schemaVersion(), "STALE_BUNDLE",
                        base.evaluatorCapability(), base.evidenceMode(),
                        base.evidenceContractVersion()));

        assertThat(stale.success()).isFalse();
        assertThat(stale.failureStatus()).isEqualTo(
                SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED);
        assertThat(port.calls()).isZero();
    }

    @Test
    void staleConfiguredPolicyVersionsCannotCallProvider() {
        TestPracticeStructuredGenerationPort port = port();
        SpeakingEvaluatorProperties staleProperties =
                new SpeakingEvaluatorProperties(
                        false,
                        "openai-compatible",
                        "https://generativelanguage.googleapis.com/v1beta/openai",
                        "secret-key",
                        "models/gemini-2.5-flash",
                        Duration.ofSeconds(30),
                        0,
                        "stale-prompt",
                        SpeakingPromptRules.RUBRIC_VERSION,
                        SpeakingPromptRules.SCHEMA_VERSION);
        OpenAiCompatibleSpeakingEvaluationClient client =
                client(staleProperties, port);

        SpeakingEvaluationProviderResult result = client.evaluate(
                SpeakingEvaluationPromptBuilderTest.request(false));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCategory()).isEqualTo(
                "STALE_EVALUATOR_POLICY_CONFIGURATION");
        assertThat(port.calls()).isZero();
    }

    private void assertHttp(HttpStatus status, boolean retryable) {
        RuntimeException ex = status.is4xxClientError()
                ? HttpClientErrorException.create(status, status.getReasonPhrase(), null, null, null)
                : HttpServerErrorException.create(status, status.getReasonPhrase(), null, null, null);
        SpeakingEvaluationProviderResult result = clientWithPort(
                TestPracticeStructuredGenerationPort.throwing(
                        "openai-primary",
                        "assessment-model",
                        ex))
                .evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, result.failureStatus());
        assertEquals("PROVIDER_HTTP_ERROR", result.errorCategory());
        assertThat(result.retryable()).isEqualTo(retryable);
    }

    private SpeakingEvaluationRequest requestWithCapability(
            SpeakingEvaluatorCapability capability,
            SpeakingEvidenceMode mode,
            String evidenceVersion
    ) {
        SpeakingEvaluationRequest base = SpeakingEvaluationPromptBuilderTest.request(false);
        return new SpeakingEvaluationRequest(
                base.attemptId(), base.questionId(), base.questionVersionId(),
                base.promptContext(), base.promptContextFingerprint(),
                base.promptContextContractIdentity(),
                base.questionText(), base.targetLevel(),
                base.expectedAnswerGuidance(), base.imageEvidence(), base.audioMediaId(),
                base.mediaVersion(), base.mimeType(), base.byteSize(), base.durationMs(),
                base.transcriptionProvider(), base.transcriptionModel(), base.language(),
                base.transcript(), base.normalizedTranscript(), base.actuallyHeardTranscript(),
                base.interpretedIntent(), base.transcriptConfidence(), base.textFallback(),
                base.promptVersion(), base.rubricVersion(), base.schemaVersion(),
                base.policyBundleId(),
                capability, mode, evidenceVersion);
    }

    private OpenAiCompatibleSpeakingEvaluationClient clientWithPort(
            TestPracticeStructuredGenerationPort port) {
        return client(properties("secret-key",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "models/gemini-2.5-flash"), port);
    }

    private OpenAiCompatibleSpeakingEvaluationClient client(
            SpeakingEvaluatorProperties properties,
            TestPracticeStructuredGenerationPort port
    ) {
        return new OpenAiCompatibleSpeakingEvaluationClient(
                properties,
                promptBuilder,
                port);
    }

    private TestPracticeStructuredGenerationPort port() {
        try {
            return TestPracticeStructuredGenerationPort.available(
                    "openai-primary",
                    "assessment-model",
                    objectMapper.readTree(validEvaluationJson()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private SpeakingEvaluatorProperties properties(String apiKey, String baseUrl, String model) {
        return new SpeakingEvaluatorProperties(
                false,
                "openai-compatible",
                baseUrl,
                apiKey,
                model,
                Duration.ofSeconds(30),
                0,
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION);
    }

    static String validEvaluationJson() {
        return """
                {
                  "evaluation_status":"EVALUATED",
                  "score_available":true,
                  "source":"PROVIDER",
                  "model":"models/gemini-2.5-flash",
                  "transcription_model":"gpt-4o-mini-transcribe",
                  "prompt_version":"speaking-eval-v1",
                  "rubric_version":"speaking-rubric-v1",
                  "schema_version":"speaking-schema-v1",
                  "audio_media_id":12,
                  "media_version":13,
                  "transcript":"저는 학생 이에요",
                  "normalized_transcript":"저는 학생이에요.",
                  "actually_heard_transcript":"저는 학생 이에요",
                  "interpreted_intent":null,
                  "intent_confidence":null,
                  "transcript_confidence":0.81,
                  "listener_burden":"LOW",
                  "overall_score":78,
                  "level_label":"Mức luyện tập nội bộ KSH",
                  "overall_summary":"Câu trả lời rõ ý và chỉ còn một số điểm ngôn ngữ cần chỉnh.",
                  "task_achievement_summary":"Học viên giới thiệu bản thân và bám đúng chủ đề.",
                  "major_strengths":["Câu trả lời đúng trọng tâm","Ý chính dễ hiểu"],
                  "major_needs_improvement":["Dùng tiểu từ chính xác hơn","Bổ sung một ví dụ cụ thể"],
                  "confidence_notes":"Độ tin cậy đủ để phản hồi tổng quát, nhưng phát âm vẫn chỉ là gợi ý.",
                  "action_plan":[
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","sub_criterion_id":"S_GRAMMAR_PARTICLES","title":"Luyện tiểu từ","instruction":"Luyện năm câu tự giới thiệu với 은/는 và 이/가.","reason":"Tiểu từ ảnh hưởng đến độ rõ nghĩa.","priority":"HIGH"},
                    {"criterion_id":"S_FLUENCY","sub_criterion_id":"S_FLUENCY_CONTINUITY","title":"Luyện nói có thời gian","instruction":"Nói liên tục trong 30 giây.","reason":"Rèn khả năng duy trì mạch nói.","priority":"MEDIUM"}
                  ],
                  "criterion_feedback":[
                    {"criterion_id":"S_CONTENT_TASK_FULFILLMENT","display_name":"Nội dung và hoàn thành nhiệm vụ","score":17,"max_score":20,"level_label":"Tốt","summary":"Phù hợp và đúng chủ đề","strengths":["Đáp ứng yêu cầu"],"needs_improvement":["Bổ sung chi tiết"],"subcriteria":[
                      {"sub_criterion_id":"S_CONTENT_RELEVANCE","display_name":"Mức độ liên quan","level_label":"Tốt","summary":"Đúng chủ đề","strengths":["Chủ đề rõ"],"needs_improvement":[]}
                    ]},
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","display_name":"Ngữ pháp và kiểm soát câu","score":16,"max_score":20,"level_label":"Tốt","summary":"Kiểm soát phần lớn cấu trúc","strengths":["Đuôi câu rõ"],"needs_improvement":["Tiểu từ"],"subcriteria":[
                      {"sub_criterion_id":"S_GRAMMAR_PARTICLES","display_name":"Tiểu từ","level_label":"Đang phát triển","summary":"Cần kiểm soát tiểu từ tốt hơn","strengths":[],"needs_improvement":["Luyện 은/는"]}
                    ]},
                    {"criterion_id":"S_VOCABULARY_EXPRESSIONS","display_name":"Từ vựng và biểu đạt","score":12,"max_score":15,"level_label":"Tốt","summary":"Từ ngữ đủ dùng","strengths":["Từ vựng nền tảng phù hợp"],"needs_improvement":["Bổ sung cách diễn đạt tự nhiên"],"subcriteria":[
                      {"sub_criterion_id":"S_VOCAB_NATURAL_EXPRESSIONS","display_name":"Biểu đạt tự nhiên","level_label":"Đang phát triển","summary":"Có thể diễn đạt tự nhiên hơn","strengths":[],"needs_improvement":["Ghi nhớ các cụm dùng lại được"]}
                    ]},
                    {"criterion_id":"S_COHERENCE_ORGANIZATION","display_name":"Mạch lạc và tổ chức","score":12,"max_score":15,"level_label":"Tốt","summary":"Dễ theo dõi","strengths":["Trình tự hợp lý"],"needs_improvement":["Bổ sung từ nối"],"subcriteria":[
                      {"sub_criterion_id":"S_COHERENCE_LOGICAL_FLOW","display_name":"Mạch logic","level_label":"Tốt","summary":"Trình tự rõ","strengths":["Không chuyển chủ đề đột ngột"],"needs_improvement":[]}
                    ]},
                    {"criterion_id":"S_FLUENCY","display_name":"Độ trôi chảy","score":11,"max_score":15,"level_label":"Đang phát triển","summary":"Có một số chỗ ngập ngừng","strengths":["Duy trì được lời nói"],"needs_improvement":["Giảm khoảng dừng"],"subcriteria":[
                      {"sub_criterion_id":"S_FLUENCY_HESITATION","display_name":"Mức độ ngập ngừng","level_label":"Đang phát triển","summary":"Có một số chỗ ngập ngừng","strengths":[],"needs_improvement":["Luyện nói có thời gian"]}
                    ]},
                    {"criterion_id":"S_PRONUNCIATION_DELIVERY","display_name":"Phát âm và thể hiện","score":10,"max_score":15,"level_label":"Chỉ tham khảo","summary":"Nhìn chung có thể hiểu","strengths":["Có thể hiểu"],"needs_improvement":["Có thể còn điểm chưa rõ"],"subcriteria":[
                      {"sub_criterion_id":"S_PRONUNCIATION_INTELLIGIBILITY","display_name":"Độ dễ hiểu","level_label":"Chỉ tham khảo","summary":"Có thể hiểu","strengths":["Gánh nặng nghe thấp"],"needs_improvement":[]}
                    ]}
                  ],
                  "transcript_annotations":[
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","sub_criterion_id":"S_GRAMMAR_PARTICLES","evidence_scope":"TEXT_SPAN","evidence":"학생 이에요","evidence_source":"TRANSCRIPT","start_offset":3,"end_offset":9,"annotation_type":"needs_improvement","explanation_vi":"Cần nói liền tự nhiên hơn.","suggestion_ko":"학생이에요","category":"GRAMMAR","severity":"LOW","confidence":0.8}
                  ],
                  "strengths":[
                    {"criterion_id":"S_CONTENT_TASK_FULFILLMENT","sub_criterion_id":"S_CONTENT_RELEVANCE","evidence_scope":"TEXT_SPAN","evidence":"저는 학생 이에요","evidence_source":"TRANSCRIPT","explanation_vi":"Câu trả lời đúng chủ đề giới thiệu bản thân.","correction":""}
                  ],
                  "needs_improvement":[
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","sub_criterion_id":"S_GRAMMAR_PARTICLES","evidence_scope":"TEXT_SPAN","evidence":"학생 이에요","evidence_source":"TRANSCRIPT","explanation_vi":"Cần chỉnh cách nói tự nhiên hơn.","correction":"학생이에요"}
                  ],
                  "rubric_scores":[
                    {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":17,"max_score":20,"feedback":"Đúng trọng tâm"},
                    {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":16,"max_score":20,"feedback":"Kiểm soát khá tốt"},
                    {"criterion":"S_VOCABULARY_EXPRESSIONS","score":12,"max_score":15,"feedback":"Đủ dùng"},
                    {"criterion":"S_COHERENCE_ORGANIZATION","score":12,"max_score":15,"feedback":"Rõ ràng"},
                    {"criterion":"S_FLUENCY","score":11,"max_score":15,"feedback":"Có một số chỗ ngập ngừng"},
                    {"criterion":"S_PRONUNCIATION_DELIVERY","score":10,"max_score":15,"feedback":"Chỉ tham khảo"}
                  ],
                  "findings":[{"category":"REGISTER","message":"Đuôi câu nhất quán","recommendation":"Duy trì văn phong 요"}],
                  "evidence":[
                    {"source":"TRANSCRIPT","criterion":"S_GRAMMAR_SENTENCE_CONTROL","excerpt":"학생이에요","confidence":0.8},
                    {"source":"PROMPT","criterion":"S_CONTENT_TASK_FULFILLMENT","excerpt":"자기소개","confidence":1}
                  ],
                  "recommendations":["Tiếp tục luyện tập"],
                  "upgraded_answer":"저는 학생이에요.",
                  "sample_answer":"안녕하세요. 저는 한국어를 공부하는 학생입니다.",
                  "pronunciation_advisory":["có thể còn điểm chưa rõ"],
                  "fluency_observations":["có một khoảng ngập ngừng ngắn"],
                  "error_category":"",
                  "retryable":false
                }
                """;
    }

}
