package com.ksh.features.practice.ai.speaking.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionAuditService;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderTransport;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationSource;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

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
    private final PracticeAiBindingResolver bindingResolver;
    private final PracticeAiExecutionAuditService auditService;
    private final PracticeAiProviderTransport providerTransport;

    @Autowired
    public OpenAiSpeakingTranscriptionClient(
            SpeakingTranscriptionProperties properties,
            ObjectMapper objectMapper,
            PracticeAiBindingResolver bindingResolver,
            PracticeAiExecutionAuditService auditService,
            PracticeAiProviderTransport providerTransport) {
        this(properties, objectMapper, null, bindingResolver, auditService, providerTransport);
    }

    OpenAiSpeakingTranscriptionClient(
            SpeakingTranscriptionProperties properties,
            ObjectMapper objectMapper,
            OpenAiTranscriptionTransport transport
    ) {
        this(properties, objectMapper, transport, null, null, null);
    }

    private OpenAiSpeakingTranscriptionClient(
            SpeakingTranscriptionProperties properties,
            ObjectMapper objectMapper,
            OpenAiTranscriptionTransport transport,
            PracticeAiBindingResolver bindingResolver,
            PracticeAiExecutionAuditService auditService,
            PracticeAiProviderTransport providerTransport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.bindingResolver = bindingResolver;
        this.auditService = auditService;
        this.providerTransport = providerTransport;
    }

    @Override
    public SpeakingTranscriptionResult transcribe(SpeakingTranscriptionRequest request) {
        long startNanos = System.nanoTime();
        PracticeAiResolvedBinding binding = null;
        Long executionAuditId = null;
        String provider = properties.provider();
        String model = properties.model();
        if (bindingResolver == null
                && (properties.apiKey() == null || properties.apiKey().isBlank())) {
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.MISSING_API_KEY,
                    false, startNanos, provider, model);
        }
        if (request == null || request.inputStreamSupplier() == null) {
            return failure(SpeakingEvaluationStatus.AUDIO_MISSING,
                    request, SpeakingTranscriptionErrorCategory.AUDIO_MISSING,
                    false, startNanos, provider, model);
        }
        try {
            if (bindingResolver != null) {
                binding = bindingResolver.resolve(PracticeAiPurpose.PRACTICE_SPEAKING_STT);
                provider = binding.snapshot().providerProfileCode();
                model = binding.snapshot().model();
                validateBoundRequest(request, binding);
                executionAuditId = auditService.start(
                        binding.snapshot(),
                        "LEARNER_RESPONSE_STT",
                        requestIdentity(request),
                        "LEARNER_RESPONSE_AUDIO");
                bindingResolver.assertCurrent(binding.snapshot());
            }
            String raw = binding == null
                    ? callWithRetry(request)
                    : callWithRetry(request, binding);
            SpeakingTranscriptionResult result = parse(
                    raw, request, startNanos, provider, model);
            auditSuccess(executionAuditId);
            return result;
        } catch (PracticeAiControlPlaneException ex) {
            auditFailure(executionAuditId, ex.errorCode());
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.MISSING_API_KEY,
                    false, startNanos, provider, model);
        } catch (AudioOpenException ex) {
            auditFailure(executionAuditId, "AUDIO_UNAVAILABLE");
            return failure(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE,
                    false, startNanos, provider, model);
        } catch (HttpStatusCodeException ex) {
            auditFailure(executionAuditId, "PROVIDER_HTTP_ERROR");
            boolean retryable = isRetryable(ex.getStatusCode());
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_HTTP_ERROR,
                    retryable, startNanos, provider, model);
        } catch (ResourceAccessException ex) {
            auditFailure(executionAuditId, "PROVIDER_TRANSPORT_ERROR");
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_TRANSPORT_ERROR,
                    true, startNanos, provider, model);
        } catch (IOException ex) {
            auditFailure(executionAuditId, "AUDIO_UNAVAILABLE");
            return failure(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.AUDIO_UNAVAILABLE,
                    false, startNanos, provider, model);
        } catch (RuntimeException ex) {
            auditFailure(executionAuditId, "PROVIDER_TRANSPORT_ERROR");
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_TRANSPORT_ERROR,
                    true, startNanos, provider, model);
        }
    }

    @Override
    public SpeakingTranscriptionClient.ProviderIdentity identity() {
        if (bindingResolver == null) {
            return new SpeakingTranscriptionClient.ProviderIdentity(
                    properties.provider(),
                    properties.model(),
                    -1L,
                    -1L,
                    "LEGACY_TEST",
                    properties.apiKey() != null && !properties.apiKey().isBlank());
        }
        return bindingResolver.availableSnapshot(PracticeAiPurpose.PRACTICE_SPEAKING_STT)
                .map(snapshot -> new SpeakingTranscriptionClient.ProviderIdentity(
                        snapshot.providerFamily(),
                        snapshot.model(),
                        snapshot.bindingRevision(),
                        snapshot.providerProfileRevision(),
                        snapshot.providerProfileCode(),
                        true))
                .orElseGet(() -> new SpeakingTranscriptionClient.ProviderIdentity(
                        "UNBOUND", "", -1L, -1L, "UNBOUND", false));
    }

    private MultiValueMap<String, Object> multipart(SpeakingTranscriptionRequest request) {
        return multipart(request, properties.model());
    }

    private MultiValueMap<String, Object> multipart(
            SpeakingTranscriptionRequest request,
            String model) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", model);
        body.add("file", audioResource(request));
        body.add("language", language(request));
        body.add("response_format", "json");
        if (properties.includeLogprobs() && supportsLogprobs(model)) {
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

    private String callWithRetry(SpeakingTranscriptionRequest request) throws IOException {
        int maxRetries = properties.maxRetries();
        RuntimeException lastRuntime = null;
        IOException lastIo = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            requireEvaluationThreadActive();
            try {
                MultiValueMap<String, Object> body = multipart(request);
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

    private String callWithRetry(
            SpeakingTranscriptionRequest request,
            PracticeAiResolvedBinding binding) throws IOException {
        PracticeAiProviderTransport.ProviderResponse last = null;
        for (int attempt = 0;
                attempt <= binding.snapshot().limits().maxRetries();
                attempt++) {
            requireEvaluationThreadActive();
            MultiValueMap<String, Object> body = multipart(
                    request, binding.snapshot().model());
            last = providerTransport.exchange(
                    binding,
                    "/audio/transcriptions",
                    MediaType.MULTIPART_FORM_DATA,
                    MediaType.APPLICATION_JSON,
                    body,
                    java.util.Map.of());
            if (last.successful()) {
                return new String(last.body(), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (!retryable(last.status())
                    || attempt >= binding.snapshot().limits().maxRetries()) {
                break;
            }
        }
        throw new PracticeAiControlPlaneException(
                "PROVIDER_HTTP_ERROR",
                last != null && retryable(last.status()));
    }

    private static void requireEvaluationThreadActive() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException(
                    "Speaking transcription was interrupted.");
        }
    }

    private SpeakingTranscriptionResult parse(
            String raw,
            SpeakingTranscriptionRequest request,
            long startNanos,
            String provider,
            String model) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            return failure(SpeakingEvaluationStatus.INVALID_PROVIDER_RESULT,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_MALFORMED_JSON,
                    false, startNanos, provider, model);
        }
        String transcript = text(root.get("text"));
        if (transcript == null) {
            return failure(SpeakingEvaluationStatus.TRANSCRIPTION_UNAVAILABLE,
                    request, SpeakingTranscriptionErrorCategory.PROVIDER_EMPTY_TRANSCRIPT,
                    false, startNanos, provider, model);
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
                provider,
                model,
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
            long startNanos,
            String provider,
            String model
    ) {
        return new SpeakingTranscriptionResult(
                status,
                SpeakingEvaluationSource.PROVIDER,
                provider,
                model,
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

    private static void validateBoundRequest(
            SpeakingTranscriptionRequest request,
            PracticeAiResolvedBinding binding) {
        if (!binding.snapshot().capabilities().batchTranscription()
                || request.byteSize() == null
                || request.byteSize() <= 0
                || request.byteSize() > binding.snapshot().limits().maxRequestBytes()) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_CAPABILITY_INCOMPATIBLE", false);
        }
    }

    private static String requestIdentity(SpeakingTranscriptionRequest request) {
        return String.join("|",
                "media=" + request.mediaId(),
                "attempt=" + request.attemptId(),
                "question=" + request.questionId(),
                "version=" + request.mediaVersion(),
                "mime=" + request.mimeType(),
                "bytes=" + request.byteSize(),
                "duration=" + request.durationMs());
    }

    private void auditSuccess(Long auditId) {
        if (auditId != null) {
            auditService.success(auditId);
        }
    }

    private void auditFailure(Long auditId, String errorCode) {
        if (auditId != null) {
            auditService.failure(auditId, errorCode);
        }
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

    private static boolean retryable(int status) {
        return status == 429 || status == 500 || status == 502
                || status == 503 || status == 504;
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
        return transport == null ? "CONTROL_PLANE" : transport.baseUrl();
    }

    interface OpenAiTranscriptionTransport {
        String post(MultiValueMap<String, Object> body) throws IOException;

        String baseUrl();
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
