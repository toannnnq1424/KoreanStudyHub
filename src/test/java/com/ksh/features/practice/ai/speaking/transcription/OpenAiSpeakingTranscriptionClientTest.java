package com.ksh.features.practice.ai.speaking.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenAiSpeakingTranscriptionClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsMultipartRequestWithModelFileLanguageJsonAndLogprobs() throws Exception {
        CapturingTransport transport = new CapturingTransport("{\"text\":\"안녕하세요\"}");
        OpenAiSpeakingTranscriptionClient client = client(properties("secret-key", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true), transport);

        client.transcribe(request("안녕하세요"));

        MultiValueMap<String, Object> body = transport.body();
        assertEquals("gpt-4o-mini-transcribe", body.getFirst("model"));
        assertEquals("ko", body.getFirst("language"));
        assertEquals("json", body.getFirst("response_format"));
        assertEquals("logprobs", body.getFirst("include[]"));
        assertThat(body.getFirst("file")).isInstanceOf(Resource.class);
        assertThat(((Resource) body.getFirst("file")).getFilename()).isEqualTo("speaking-audio-9.webm");
        assertThat(transport.calls()).isEqualTo(1);
    }

    @Test
    void usesTranscriptionBaseUrlNotGeminiEvaluatorBaseUrl() {
        CapturingTransport transport = new CapturingTransport("{\"text\":\"ok\"}", "https://api.openai.com/v1");
        OpenAiSpeakingTranscriptionClient client = client(properties("secret-key", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true), transport);

        assertThat(client.transportBaseUrlForTest()).isEqualTo("https://api.openai.com/v1");
        assertThat(client.transportBaseUrlForTest()).doesNotContain("generativelanguage.googleapis.com");
    }

    @Test
    void missingApiKeyMapsUnavailableWithoutProviderCall() {
        CapturingTransport transport = new CapturingTransport("{\"text\":\"should-not-call\"}");
        OpenAiSpeakingTranscriptionClient client = client(properties("", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true), transport);

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE, result.status());
        assertEquals(SpeakingTranscriptionErrorCategory.MISSING_API_KEY, result.errorCategory());
        assertThat(result.retryable()).isFalse();
        assertThat(transport.calls()).isZero();
    }

    @Test
    void http429And503MapRetryableTrue() {
        assertRetryableHttp(HttpStatus.TOO_MANY_REQUESTS);
        assertRetryableHttp(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void http400401413415MapRetryableFalse() {
        assertNonRetryableHttp(HttpStatus.BAD_REQUEST);
        assertNonRetryableHttp(HttpStatus.UNAUTHORIZED);
        assertNonRetryableHttp(HttpStatus.PAYLOAD_TOO_LARGE);
        assertNonRetryableHttp(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void transportTimeoutMapsRetryableTrue() {
        CapturingTransport transport = new CapturingTransport(new ResourceAccessException("timeout"));
        OpenAiSpeakingTranscriptionClient client = client(properties("secret-key", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true), transport);

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE, result.status());
        assertEquals(SpeakingTranscriptionErrorCategory.PROVIDER_TRANSPORT_ERROR, result.errorCategory());
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void malformedJsonMapsInvalidProviderResult() {
        OpenAiSpeakingTranscriptionClient client = clientWithResponse("not-json");

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.INVALID_PROVIDER_RESULT, result.status());
        assertEquals(SpeakingTranscriptionErrorCategory.PROVIDER_MALFORMED_JSON, result.errorCategory());
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void emptyTranscriptMapsSafeFailure() {
        OpenAiSpeakingTranscriptionClient client = clientWithResponse("{\"text\":\"   \"}");

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE, result.status());
        assertEquals(SpeakingTranscriptionErrorCategory.PROVIDER_EMPTY_TRANSCRIPT, result.errorCategory());
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void successfulTranscriptNormalizesWhitespace() {
        OpenAiSpeakingTranscriptionClient client = clientWithResponse("{\"text\":\" 안녕   하세요 \\n 여러분 \"}");

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.status());
        assertEquals("안녕   하세요 \n 여러분", result.transcript());
        assertEquals("안녕 하세요 여러분", result.normalizedTranscript());
    }

    @Test
    void logprobsPresentMapsTranscriptConfidence() {
        OpenAiSpeakingTranscriptionClient client = clientWithResponse("""
                {"text":"안녕하세요","logprobs":[{"token":"안","logprob":-0.1},{"token":"녕","logprob":-0.2}]}
                """);

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.status());
        assertThat(result.transcriptConfidence()).isNotNull();
        assertThat(result.transcriptConfidence()).isGreaterThan(new BigDecimal("0.80"));
        assertThat(result.logprobSummary().tokenCount()).isEqualTo(2);
    }

    @Test
    void missingConfidenceRemainsNullAndDoesNotBecomeLowConfidence() {
        OpenAiSpeakingTranscriptionClient client = clientWithResponse("{\"text\":\"안녕하세요\"}");

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.status());
        assertNull(result.transcriptConfidence());
        assertNull(result.logprobSummary());
    }

    @Test
    void lowConfidenceMapsLowConfidenceStatus() {
        OpenAiSpeakingTranscriptionClient client = clientWithResponse("""
                {"text":"안녕하세요","logprobs":[{"token":"안","logprob":-1.4},{"token":"녕","logprob":-1.2}]}
                """);

        SpeakingTranscriptionResult result = client.transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE, result.status());
        assertThat(result.transcriptConfidence()).isLessThan(new BigDecimal("0.50"));
    }

    @Test
    void toStringDoesNotExposeSecretsTranscriptOrProviderBody() {
        String transcript = "제 비밀 전화번호는 010입니다";
        SpeakingTranscriptionResult result = clientWithResponse("{\"text\":\"" + transcript + "\"}")
                .transcribe(request("storage-key-secret"));

        assertThat(result.toString())
                .doesNotContain(transcript)
                .doesNotContain("storage-key-secret")
                .doesNotContain("secret-key")
                .doesNotContain("{\"text\"");
        assertThat(request("storage-key-secret").toString())
                .doesNotContain("storage-key-secret")
                .doesNotContain("learner-speaking")
                .doesNotContain("private-storage");
    }

    @Test
    void noRealProviderCallCanHappenWithFakeTransport() {
        CapturingTransport transport = new CapturingTransport("{\"text\":\"안녕하세요\"}");
        OpenAiSpeakingTranscriptionClient client = client(properties("secret-key", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true), transport);

        client.transcribe(request("unused"));

        assertThat(transport.calls()).isEqualTo(1);
        assertThat(transport.baseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void retryRebuildsMultipartAndReopensAudioStream() {
        ResourceReadingRetryTransport transport = new ResourceReadingRetryTransport();
        OpenAiSpeakingTranscriptionClient client = client(properties("secret-key", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true, 1), transport);
        AtomicInteger opens = new AtomicInteger();
        byte[] payload = "fresh-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SpeakingTranscriptionRequest request = new SpeakingTranscriptionRequest(
                9L,
                10L,
                11L,
                12L,
                "audio/webm",
                (long) payload.length,
                1200L,
                "ko",
                () -> {
                    opens.incrementAndGet();
                    return new ByteArrayInputStream(payload);
                });

        SpeakingTranscriptionResult result = client.transcribe(request);

        assertEquals(SpeakingEvaluationStatus.EVALUATED, result.status());
        assertThat(transport.calls()).isEqualTo(2);
        assertThat(opens.get()).isEqualTo(2);
        assertThat(transport.payloads()).containsExactly("fresh-audio", "fresh-audio");
    }

    private void assertRetryableHttp(HttpStatus status) {
        CapturingTransport transport = status == HttpStatus.TOO_MANY_REQUESTS
                ? new CapturingTransport(HttpClientErrorException.create(
                        status, status.getReasonPhrase(), null, null, null))
                : new CapturingTransport(HttpServerErrorException.create(
                        status, status.getReasonPhrase(), null, null, null));
        SpeakingTranscriptionResult result = client(properties("secret-key", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true), transport).transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE, result.status());
        assertEquals(SpeakingTranscriptionErrorCategory.PROVIDER_HTTP_ERROR, result.errorCategory());
        assertThat(result.retryable()).isTrue();
    }

    private void assertNonRetryableHttp(HttpStatus status) {
        CapturingTransport transport = new CapturingTransport(HttpClientErrorException.create(
                status, status.getReasonPhrase(), null, null, null));
        SpeakingTranscriptionResult result = client(properties("secret-key", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", true), transport).transcribe(request("unused"));

        assertEquals(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE, result.status());
        assertEquals(SpeakingTranscriptionErrorCategory.PROVIDER_HTTP_ERROR, result.errorCategory());
        assertThat(result.retryable()).isFalse();
    }

    private OpenAiSpeakingTranscriptionClient clientWithResponse(String response) {
        return client(properties("secret-key", "https://api.openai.com/v1", "gpt-4o-mini-transcribe", true),
                new CapturingTransport(response));
    }

    private OpenAiSpeakingTranscriptionClient client(
            SpeakingTranscriptionProperties properties,
            OpenAiSpeakingTranscriptionClient.OpenAiTranscriptionTransport transport
    ) {
        return new OpenAiSpeakingTranscriptionClient(properties, objectMapper, transport);
    }

    private SpeakingTranscriptionProperties properties(String apiKey, String baseUrl, String model, boolean includeLogprobs) {
        return properties(apiKey, baseUrl, model, includeLogprobs, 0);
    }

    private SpeakingTranscriptionProperties properties(String apiKey, String baseUrl, String model,
                                                      boolean includeLogprobs, int maxRetries) {
        return new SpeakingTranscriptionProperties(
                false,
                "openai",
                baseUrl,
                apiKey,
                model,
                "ko",
                26214400L,
                Duration.ofSeconds(30),
                maxRetries,
                includeLogprobs,
                "audio/webm,audio/mp4");
    }

    private SpeakingTranscriptionRequest request(String bytes) {
        byte[] payload = bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new SpeakingTranscriptionRequest(
                9L,
                10L,
                11L,
                12L,
                "audio/webm",
                (long) payload.length,
                1200L,
                "ko",
                () -> new ByteArrayInputStream(payload));
    }

    private static class CapturingTransport implements OpenAiSpeakingTranscriptionClient.OpenAiTranscriptionTransport {
        private final String response;
        private final RuntimeException runtimeFailure;
        private final String baseUrl;
        private final AtomicInteger calls = new AtomicInteger();
        private MultiValueMap<String, Object> body;

        private CapturingTransport(String response) {
            this(response, "https://api.openai.com/v1");
        }

        private CapturingTransport(String response, String baseUrl) {
            this.response = response;
            this.runtimeFailure = null;
            this.baseUrl = baseUrl;
        }

        private CapturingTransport(RuntimeException runtimeFailure) {
            this.response = null;
            this.runtimeFailure = runtimeFailure;
            this.baseUrl = "https://api.openai.com/v1";
        }

        @Override
        public String post(MultiValueMap<String, Object> body) throws IOException {
            this.body = body;
            calls.incrementAndGet();
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            return response;
        }

        @Override
        public String baseUrl() {
            return baseUrl;
        }

        private MultiValueMap<String, Object> body() {
            return body;
        }

        private int calls() {
            return calls.get();
        }
    }

    private static class ResourceReadingRetryTransport implements OpenAiSpeakingTranscriptionClient.OpenAiTranscriptionTransport {
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> payloads = new ArrayList<>();

        @Override
        public String post(MultiValueMap<String, Object> body) throws IOException {
            calls.incrementAndGet();
            Resource file = (Resource) body.getFirst("file");
            payloads.add(new String(file.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            if (calls.get() == 1) {
                throw new ResourceAccessException("retryable timeout");
            }
            return "{\"text\":\"안녕하세요\"}";
        }

        @Override
        public String baseUrl() {
            return "https://api.openai.com/v1";
        }

        private int calls() {
            return calls.get();
        }

        private List<String> payloads() {
            return payloads;
        }
    }
}
