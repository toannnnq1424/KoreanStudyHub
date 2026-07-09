package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleSpeakingEvaluationClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingEvaluationPromptBuilder promptBuilder = new SpeakingEvaluationPromptBuilder(objectMapper);

    @Test
    @SuppressWarnings("unchecked")
    void sendsFixedSchemaChatCompletionRequest() {
        CapturingTransport transport = new CapturingTransport(envelope(validEvaluationJson()));
        OpenAiCompatibleSpeakingEvaluationClient client = client(properties("secret-key",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "models/gemini-2.5-flash"), transport);

        client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        Map<String, Object> body = transport.body();
        assertEquals("models/gemini-2.5-flash", body.get("model"));
        assertThat(body.get("messages").toString())
                .contains("system")
                .contains("user")
                .contains("KSH Korean Study Hub");
        Map<String, Object> responseFormat = (Map<String, Object>) body.get("response_format");
        assertEquals("json_schema", responseFormat.get("type"));
        assertThat(responseFormat.toString()).contains("S_CONTENT_TASK_FULFILLMENT");
    }

    @Test
    void usesSpeakingEvaluatorBaseUrlNotTranscriptionBaseUrl() {
        OpenAiCompatibleSpeakingEvaluationClient client = client(properties("secret-key",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "models/gemini-2.5-flash"),
                new CapturingTransport(envelope(validEvaluationJson()),
                        "https://generativelanguage.googleapis.com/v1beta/openai"));

        assertThat(client.transportBaseUrlForTest())
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai")
                .doesNotContain("audio/transcriptions")
                .doesNotContain("api.openai.com/v1");
    }

    @Test
    void missingApiKeyMapsEvaluationUnavailableWithoutProviderCall() {
        CapturingTransport transport = new CapturingTransport(envelope(validEvaluationJson()));
        OpenAiCompatibleSpeakingEvaluationClient client = client(properties("",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "models/gemini-2.5-flash"), transport);

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, result.failureStatus());
        assertEquals("MISSING_API_KEY", result.errorCategory());
        assertThat(result.retryable()).isFalse();
        assertThat(transport.calls()).isZero();
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
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithTransport(
                new CapturingTransport(new ResourceAccessException("timeout")));

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, result.failureStatus());
        assertEquals("PROVIDER_TRANSPORT_ERROR", result.errorCategory());
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void malformedProviderEnvelopeMapsContractFailure() {
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithTransport(new CapturingTransport("not-json"));

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED, result.failureStatus());
        assertEquals("PROVIDER_MALFORMED_JSON", result.errorCategory());
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void providerContentJsonParsesToEvaluationJson() {
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithTransport(
                new CapturingTransport(envelope(validEvaluationJson())));

        SpeakingEvaluationProviderResult result = client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertThat(result.success()).isTrue();
        assertThat(result.evaluationJson().path("evaluation_status").asText()).isEqualTo("EVALUATED");
        assertThat(result.toString()).doesNotContain("저는 학생").doesNotContain("secret-key");
    }

    @Test
    void noRealProviderCallCanHappenWithFakeTransport() {
        CapturingTransport transport = new CapturingTransport(envelope(validEvaluationJson()));
        OpenAiCompatibleSpeakingEvaluationClient client = clientWithTransport(transport);

        client.evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertThat(transport.calls()).isEqualTo(1);
    }

    private void assertHttp(HttpStatus status, boolean retryable) {
        RuntimeException ex = status.is4xxClientError()
                ? HttpClientErrorException.create(status, status.getReasonPhrase(), null, null, null)
                : HttpServerErrorException.create(status, status.getReasonPhrase(), null, null, null);
        SpeakingEvaluationProviderResult result = clientWithTransport(new CapturingTransport(ex))
                .evaluate(SpeakingEvaluationPromptBuilderTest.request(false));

        assertEquals(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, result.failureStatus());
        assertEquals("PROVIDER_HTTP_ERROR", result.errorCategory());
        assertThat(result.retryable()).isEqualTo(retryable);
    }

    private OpenAiCompatibleSpeakingEvaluationClient clientWithTransport(CapturingTransport transport) {
        return client(properties("secret-key",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "models/gemini-2.5-flash"), transport);
    }

    private OpenAiCompatibleSpeakingEvaluationClient client(
            SpeakingEvaluatorProperties properties,
            CapturingTransport transport
    ) {
        return new OpenAiCompatibleSpeakingEvaluationClient(properties, promptBuilder, objectMapper, transport);
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
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1");
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
                  "actually_heard_transcript":"저는 학생이에요.",
                  "interpreted_intent":"The learner introduces themself.",
                  "intent_confidence":0.8,
                  "transcript_confidence":0.81,
                  "listener_burden":"LOW",
                  "overall_score":78,
                  "level_label":"KSH internal",
                  "overall_summary":"Clear answer with minor language issues.",
                  "task_achievement_summary":"The learner introduces themself and stays on topic.",
                  "major_strengths":["Relevant answer","Understandable main idea"],
                  "major_needs_improvement":["Use particles more accurately","Add one specific example"],
                  "confidence_notes":"Độ tin cậy đủ để phản hồi tổng quát, nhưng phát âm vẫn chỉ là gợi ý.",
                  "action_plan":[
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","sub_criterion_id":"S_GRAMMAR_PARTICLES","title":"Particle drill","instruction":"Practice five self-introduction sentences with 은/는 and 이/가.","reason":"Particles affect clarity.","priority":"HIGH"},
                    {"criterion_id":"S_FLUENCY","sub_criterion_id":"S_FLUENCY_CONTINUITY","title":"Timed speaking","instruction":"Speak for 30 seconds without stopping.","reason":"Build continuity.","priority":"MEDIUM"}
                  ],
                  "criterion_feedback":[
                    {"criterion_id":"S_CONTENT_TASK_FULFILLMENT","display_name":"Content / Task Fulfillment","score":17,"max_score":20,"level_label":"Good","summary":"Relevant and on topic","strengths":["Answers the task"],"needs_improvement":["Add detail"],"subcriteria":[
                      {"sub_criterion_id":"S_CONTENT_RELEVANCE","display_name":"Relevance","level_label":"Good","summary":"On topic","strengths":["Clear topic"],"needs_improvement":[]}
                    ]},
                    {"criterion_id":"S_GRAMMAR_SENTENCE_CONTROL","display_name":"Grammar & Sentence Control","score":16,"max_score":20,"level_label":"Good","summary":"Mostly controlled","strengths":["Clear ending"],"needs_improvement":["Particles"],"subcriteria":[
                      {"sub_criterion_id":"S_GRAMMAR_PARTICLES","display_name":"Particles","level_label":"Developing","summary":"Some particle control needed","strengths":[],"needs_improvement":["Drill 은/는"]}
                    ]},
                    {"criterion_id":"S_VOCABULARY_EXPRESSIONS","display_name":"Vocabulary & Expressions","score":12,"max_score":15,"level_label":"Good","summary":"Adequate words","strengths":["Basic vocabulary"],"needs_improvement":["Add natural expression"],"subcriteria":[
                      {"sub_criterion_id":"S_VOCAB_NATURAL_EXPRESSIONS","display_name":"Natural expressions","level_label":"Developing","summary":"Could sound more natural","strengths":[],"needs_improvement":["Memorize reusable phrases"]}
                    ]},
                    {"criterion_id":"S_COHERENCE_ORGANIZATION","display_name":"Coherence & Organization","score":12,"max_score":15,"level_label":"Good","summary":"Easy to follow","strengths":["Logical order"],"needs_improvement":["Add connector"],"subcriteria":[
                      {"sub_criterion_id":"S_COHERENCE_LOGICAL_FLOW","display_name":"Logical flow","level_label":"Good","summary":"Clear order","strengths":["No topic jump"],"needs_improvement":[]}
                    ]},
                    {"criterion_id":"S_FLUENCY","display_name":"Fluency","score":11,"max_score":15,"level_label":"Developing","summary":"Some hesitation","strengths":["Continues speaking"],"needs_improvement":["Reduce pauses"],"subcriteria":[
                      {"sub_criterion_id":"S_FLUENCY_HESITATION","display_name":"Hesitation","level_label":"Developing","summary":"Some hesitation","strengths":[],"needs_improvement":["Timed speaking"]}
                    ]},
                    {"criterion_id":"S_PRONUNCIATION_DELIVERY","display_name":"Pronunciation & Delivery","score":10,"max_score":15,"level_label":"Advisory","summary":"Generally understandable","strengths":["Understandable"],"needs_improvement":["Possible clarity issue"],"subcriteria":[
                      {"sub_criterion_id":"S_PRONUNCIATION_INTELLIGIBILITY","display_name":"Intelligibility","level_label":"Advisory","summary":"Understandable","strengths":["Low listener burden"],"needs_improvement":[]}
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
                    {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":17,"max_score":20,"feedback":"Relevant"},
                    {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":16,"max_score":20,"feedback":"Controlled"},
                    {"criterion":"S_VOCABULARY_EXPRESSIONS","score":12,"max_score":15,"feedback":"Adequate"},
                    {"criterion":"S_COHERENCE_ORGANIZATION","score":12,"max_score":15,"feedback":"Clear"},
                    {"criterion":"S_FLUENCY","score":11,"max_score":15,"feedback":"Some hesitation"},
                    {"criterion":"S_PRONUNCIATION_DELIVERY","score":10,"max_score":15,"feedback":"Advisory only"}
                  ],
                  "findings":[{"category":"REGISTER","message":"Ending is consistent","recommendation":"Keep 요 style"}],
                  "evidence":[
                    {"source":"TRANSCRIPT","criterion":"S_GRAMMAR_SENTENCE_CONTROL","excerpt":"학생이에요","confidence":0.8},
                    {"source":"PROMPT","criterion":"S_CONTENT_TASK_FULFILLMENT","excerpt":"자기소개","confidence":1}
                  ],
                  "recommendations":["Keep practicing"],
                  "upgraded_answer":"저는 학생이에요.",
                  "sample_answer":"안녕하세요. 저는 한국어를 공부하는 학생입니다.",
                  "pronunciation_advisory":["possible clarity issue"],
                  "fluency_observations":["short hesitation"],
                  "error_category":"",
                  "retryable":false
                }
                """;
    }

    private String envelope(String content) {
        try {
            return "{\"choices\":[{\"message\":{\"content\":"
                    + objectMapper.writeValueAsString(content)
                    + "}}]}";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static class CapturingTransport implements OpenAiCompatibleSpeakingEvaluationClient.OpenAiCompatibleEvaluationTransport {
        private final String response;
        private final RuntimeException failure;
        private final String baseUrl;
        private final AtomicInteger calls = new AtomicInteger();
        private Map<String, Object> body;

        private CapturingTransport(String response) {
            this(response, "https://generativelanguage.googleapis.com/v1beta/openai");
        }

        private CapturingTransport(String response, String baseUrl) {
            this.response = response;
            this.failure = null;
            this.baseUrl = baseUrl;
        }

        private CapturingTransport(RuntimeException failure) {
            this.response = null;
            this.failure = failure;
            this.baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
        }

        @Override
        public String post(Map<String, Object> body) {
            this.body = body;
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public String baseUrl() {
            return baseUrl;
        }

        private Map<String, Object> body() {
            return body;
        }

        private int calls() {
            return calls.get();
        }
    }
}
