package com.ksh.features.practice.ai.speaking.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationSource;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OpenAiSpeakingTranscriptionClient implements SpeakingTranscriptionClient {
    private static final String PROVIDER = "openai";
    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.50");

    private final SpeakingTranscriptionProperties properties;
    private final ObjectMapper objectMapper;
    private final OpenAiTranscriptionTransport transport;

    public OpenAiSpeakingTranscriptionClient(SpeakingTranscriptionProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new RestClientOpenAiTranscriptionTransport(properties));
    }

    OpenAiSpeakingTranscriptionClient(
            SpeakingTranscriptionProperties properties,
            ObjectMapper objectMapper,
            OpenAiTranscriptionTransport transport
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Override
    public SpeakingTranscriptionResult transcribe(SpeakingTranscriptionRequest request) {
        long startNanos = System.nanoTime();
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.MISSING_API_KEY, false, startNanos);
        }
        if (request == null || request.inputStreamSupplier() == null) {
            return failure(SpeakingEvaluationStatus.AUDIO_MISSING,
                    request, SpeakingTranscriptionErrorCategory.AUDIO_MISSING, false, startNanos);
        }
        try {
            MultiValueMap<String, Object> multipart = multipart(request);
            String raw = callWithRetry(multipart);
            return parse(raw, request, startNanos);
        } catch (AudioOpenException ex) {
            return failure(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE, false, startNanos);
        } catch (HttpStatusCodeException ex) {
            boolean retryable = isRetryable(ex.getStatusCode());
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_HTTP_ERROR, retryable, startNanos);
        } catch (ResourceAccessException ex) {
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_TRANSPORT_ERROR, true, startNanos);
        } catch (IOException ex) {
            return failure(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE, false, startNanos);
        } catch (RuntimeException ex) {
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_TRANSPORT_ERROR, true, startNanos);
        }
    }

    private MultiValueMap<String, Object> multipart(SpeakingTranscriptionRequest request) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", properties.model());
        body.add("file", audioResource(request));
        body.add("language", language(request));
        body.add("response_format", "json");
        if (properties.includeLogprobs() && supportsLogprobs(properties.model())) {
            body.add("include[]", "logprobs");
        }
        return body;
    }

    private InputStreamResource audioResource(SpeakingTranscriptionRequest request) {
        return new InputStreamResource(open(request)) {
            @Override
            public String getFilename() {
                return "speaking-audio-" + safeId(request.mediaId()) + extension(request.mimeType());
            }

            @Override
            public long contentLength() {
                return request.byteSize() == null ? -1L : request.byteSize();
            }
        };
    }

    private InputStream open(SpeakingTranscriptionRequest request) {
        try {
            return request.inputStreamSupplier().open();
        } catch (IOException ex) {
            throw new AudioOpenException(ex);
        }
    }

    private String callWithRetry(MultiValueMap<String, Object> body) throws IOException {
        int maxRetries = properties.maxRetries();
        RuntimeException lastRuntime = null;
        IOException lastIo = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return transport.post(body);
            } catch (HttpStatusCodeException ex) {
                lastRuntime = ex;
                if (isRetryable(ex.getStatusCode()) && attempt < maxRetries) {
                    continue;
                }
                throw ex;
            } catch (ResourceAccessException ex) {
                lastRuntime = ex;
                if (attempt < maxRetries) {
                    continue;
                }
                throw ex;
            } catch (IOException ex) {
                lastIo = ex;
                throw ex;
            }
        }
        if (lastIo != null) {
            throw lastIo;
        }
        if (lastRuntime != null) {
            throw lastRuntime;
        }
        throw new ResourceAccessException("Transcription provider unavailable");
    }

    private SpeakingTranscriptionResult parse(String raw, SpeakingTranscriptionRequest request, long startNanos) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            return failure(SpeakingEvaluationStatus.INVALID_PROVIDER_RESULT,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_MALFORMED_JSON, false, startNanos);
        }
        String transcript = text(root.get("text"));
        if (transcript == null) {
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_EMPTY_TRANSCRIPT, false, startNanos);
        }
        LogprobStats stats = logprobStats(root.path("logprobs"));
        BigDecimal confidence = stats == null ? null : stats.confidence();
        SpeakingEvaluationStatus status = confidence != null
                && confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0
                ? SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE
                : SpeakingEvaluationStatus.EVALUATED;
        return new SpeakingTranscriptionResult(
                status,
                SpeakingEvaluationSource.PROVIDER,
                PROVIDER,
                properties.model(),
                language(request),
                transcript,
                normalizeTranscript(transcript),
                confidence,
                stats == null ? null : stats.summary(),
                request == null ? null : request.durationMs(),
                elapsedMillis(startNanos),
                null,
                false);
    }

    private LogprobStats logprobStats(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        List<BigDecimal> values = new ArrayList<>();
        for (JsonNode item : node) {
            JsonNode logprob = item.get("logprob");
            if (logprob != null && logprob.isNumber()) {
                values.add(logprob.decimalValue());
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
        BigDecimal minimum = values.stream().min(BigDecimal::compareTo).orElse(average);
        double confidenceDouble = Math.exp(average.doubleValue());
        BigDecimal confidence = BigDecimal.valueOf(confidenceDouble).round(new MathContext(4, RoundingMode.HALF_UP));
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            return null;
        }
        SpeakingTranscriptionResult.LogprobSummary summary =
                new SpeakingTranscriptionResult.LogprobSummary(values.size(), average, minimum);
        return new LogprobStats(confidence, summary);
    }

    private SpeakingTranscriptionResult failure(
            SpeakingEvaluationStatus status,
            SpeakingTranscriptionRequest request,
            SpeakingTranscriptionErrorCategory category,
            boolean retryable,
            long startNanos
    ) {
        return new SpeakingTranscriptionResult(
                status,
                SpeakingEvaluationSource.PROVIDER,
                PROVIDER,
                properties.model(),
                request == null ? properties.language() : language(request),
                null,
                null,
                null,
                null,
                request == null ? null : request.durationMs(),
                elapsedMillis(startNanos),
                category,
                retryable);
    }

    private String language(SpeakingTranscriptionRequest request) {
        String value = request == null ? null : request.language();
        return value == null || value.isBlank() ? properties.language() : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTranscript(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean supportsLogprobs(String model) {
        return "gpt-4o-transcribe".equals(model) || "gpt-4o-mini-transcribe".equals(model);
    }

    private static boolean isRetryable(HttpStatusCode status) {
        int value = status.value();
        return value == 429 || value == 500 || value == 502 || value == 503 || value == 504;
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static String extension(String mimeType) {
        if ("audio/mp4".equalsIgnoreCase(mimeType)) {
            return ".mp4";
        }
        return ".webm";
    }

    private static String safeId(Long id) {
        return id == null ? "unknown" : String.valueOf(id);
    }

    String transportBaseUrlForTest() {
        return transport.baseUrl();
    }

    interface OpenAiTranscriptionTransport {
        String post(MultiValueMap<String, Object> body) throws IOException;

        String baseUrl();
    }

    private static class RestClientOpenAiTranscriptionTransport implements OpenAiTranscriptionTransport {
        private final SpeakingTranscriptionProperties properties;
        private final RestClient restClient;

        private RestClientOpenAiTranscriptionTransport(SpeakingTranscriptionProperties properties) {
            this.properties = properties;
            this.restClient = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                    .requestFactory(requestFactory(properties.timeout()))
                    .build();
        }

        @Override
        public String post(MultiValueMap<String, Object> body) {
            return restClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        }

        @Override
        public String baseUrl() {
            return properties.baseUrl();
        }

        private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            int timeoutMs = Math.toIntExact(Math.min(timeout.toMillis(), Integer.MAX_VALUE));
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
            return factory;
        }
    }

    private record LogprobStats(
            BigDecimal confidence,
            SpeakingTranscriptionResult.LogprobSummary summary
    ) {}

    private static class AudioOpenException extends RuntimeException {
        private AudioOpenException(IOException cause) {
            super(cause);
        }
    }
}
