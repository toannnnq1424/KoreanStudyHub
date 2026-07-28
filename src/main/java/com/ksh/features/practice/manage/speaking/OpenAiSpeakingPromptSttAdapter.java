package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import com.ksh.features.practice.service.audio.OpenAiAudioHttpTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class OpenAiSpeakingPromptSttAdapter implements SpeakingPromptSttPort {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final SpeakingPromptAuthoringAiProperties properties;
    private final ObjectMapper objectMapper;
    private final PracticeAiMetrics metrics;
    private final SttTransport overrideTransport;

    @Autowired
    public OpenAiSpeakingPromptSttAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            ObjectMapper objectMapper,
            PracticeAiMetrics metrics) {
        this(properties, objectMapper, metrics, null);
    }

    OpenAiSpeakingPromptSttAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            ObjectMapper objectMapper,
            PracticeAiMetrics metrics,
            SttTransport overrideTransport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.overrideTransport = overrideTransport;
    }

    @Override
    public SpeakingPromptAiContract.SttResult transcribe(
            SpeakingPromptAiContract.SttRequest request) {
        long started = PracticeAiMetrics.startNanos();
        SpeakingPromptAuthoringAiProperties.SttConfig config =
                properties.sttConfig();
        try {
            requireEnabledOpenAi(config);
            validateRequest(request, config);
            ProviderResponse response = callOnce(request, config);
            if (response.status() < 200 || response.status() >= 300) {
                throw httpFailure(response.status(), response.requestReference());
            }
            SpeakingPromptAiContract.SttResult result =
                    parse(response, config);
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    PracticeAiMetrics.ProviderOutcome.SUCCESS,
                    PracticeAiMetrics.elapsedSince(started));
            return result;
        } catch (SpeakingPromptAiContract.ProviderFailure failure) {
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    outcome(failure.publicCategory()),
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        } catch (ResourceAccessException exception) {
            SpeakingPromptAiContract.ProviderFailure failure =
                    transportFailure(exception);
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    PracticeAiMetrics.ProviderOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        } catch (RuntimeException exception) {
            SpeakingPromptAiContract.ProviderFailure failure =
                    new SpeakingPromptAiContract.ProviderFailure(
                            SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                            true,
                            null,
                            exception);
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    PracticeAiMetrics.ProviderOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        }
    }

    private ProviderResponse callOnce(
            SpeakingPromptAiContract.SttRequest request,
            SpeakingPromptAuthoringAiProperties.SttConfig config) {
        SttTransport transport = overrideTransport == null
                ? new RestClientSttTransport(config)
                : overrideTransport;
        return transport.post(request, config);
    }

    private SpeakingPromptAiContract.SttResult parse(
            ProviderResponse response,
            SpeakingPromptAuthoringAiProperties.SttConfig config) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                    false,
                    response.requestReference(),
                    exception);
        }
        JsonNode textNode = root.get("text");
        if (textNode == null
                || !textNode.isTextual()
                || textNode.asText().isBlank()) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.EMPTY_OUTPUT,
                    false,
                    response.requestReference(),
                    null);
        }
        BigDecimal confidence = null;
        JsonNode confidenceNode = root.get("confidence");
        if (confidenceNode != null && confidenceNode.isNumber()) {
            confidence = confidenceNode.decimalValue();
            if (confidence.compareTo(BigDecimal.ZERO) < 0
                    || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new SpeakingPromptAiContract.ProviderFailure(
                        SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                        false,
                        response.requestReference(),
                        null);
            }
        }
        try {
            return new SpeakingPromptAiContract.SttResult(
                    textNode.asText(),
                    confidence,
                    config.provider(),
                    config.model(),
                    config.language(),
                    response.requestReference(),
                    config.purposeCode(),
                    config.retentionCode());
        } catch (IllegalArgumentException exception) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                    false,
                    response.requestReference(),
                    exception);
        }
    }

    private static void validateRequest(
            SpeakingPromptAiContract.SttRequest request,
            SpeakingPromptAuthoringAiProperties.SttConfig config) {
        if (request == null
                || request.audio() == null
                || request.audio().bytes().length == 0
                || request.audio().bytes().length > config.maxInputBytes()
                || request.audio().durationMillis() <= 0
                || request.audio().durationMillis()
                    > config.maxInputDuration().toMillis()
                || !config.allowedMimeTypes().contains(
                        request.audio().mimeType().toLowerCase(Locale.ROOT))
                || !config.language().equalsIgnoreCase(request.languageTag())) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.INVALID_INPUT,
                    false,
                    null,
                    null);
        }
    }

    private static void requireEnabledOpenAi(
            SpeakingPromptAuthoringAiProperties.SttConfig config) {
        if (!config.enabled()
                || !"openai".equals(config.provider())
                || config.apiKey().isBlank()
                || config.baseUrl().isBlank()
                || config.model().isBlank()) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                    false,
                    null,
                    null);
        }
    }

    private static SpeakingPromptAiContract.ProviderFailure httpFailure(
            int status,
            String requestReference) {
        SpeakingPromptAiContract.PublicErrorCategory category = status == 429
                ? SpeakingPromptAiContract.PublicErrorCategory.RATE_LIMIT
                : status >= 500
                    ? SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT
                    : SpeakingPromptAiContract.PublicErrorCategory.PROVIDER_REJECTED;
        return new SpeakingPromptAiContract.ProviderFailure(
                category,
                retryableStatus(status),
                requestReference,
                null);
    }

    private static SpeakingPromptAiContract.ProviderFailure transportFailure(
            ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null && !(cause instanceof SocketTimeoutException)) {
            cause = cause.getCause();
        }
        return new SpeakingPromptAiContract.ProviderFailure(
                cause instanceof SocketTimeoutException
                        ? SpeakingPromptAiContract.PublicErrorCategory.TIMEOUT
                        : SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                true,
                null,
                exception);
    }

    private static boolean retryableStatus(int status) {
        return status == 429 || status >= 500 && status <= 599;
    }

    private static PracticeAiMetrics.ProviderOutcome outcome(
            SpeakingPromptAiContract.PublicErrorCategory category) {
        return switch (category) {
            case RATE_LIMIT -> PracticeAiMetrics.ProviderOutcome.RATE_LIMITED;
            case CONFIGURATION -> PracticeAiMetrics.ProviderOutcome.UNAVAILABLE;
            case EMPTY_OUTPUT, MALFORMED_OUTPUT ->
                    PracticeAiMetrics.ProviderOutcome.CONTRACT_FAILURE;
            default -> PracticeAiMetrics.ProviderOutcome.FAILURE;
        };
    }

    interface SttTransport {
        ProviderResponse post(
                SpeakingPromptAiContract.SttRequest request,
                SpeakingPromptAuthoringAiProperties.SttConfig config);
    }

    record ProviderResponse(int status, String body, String requestReference) {
        @Override
        public String toString() {
            return "ProviderResponse{status=" + status
                    + ", bodyLength=" + (body == null ? 0 : body.length())
                    + ", requestReferencePresent="
                    + (requestReference != null) + '}';
        }
    }

    private static final class RestClientSttTransport implements SttTransport {
        private final OpenAiAudioHttpTransport transport;

        private RestClientSttTransport(
                SpeakingPromptAuthoringAiProperties.SttConfig config) {
            transport = new OpenAiAudioHttpTransport(
                    config.baseUrl(),
                    config.apiKey(),
                    config.connectTimeout(),
                    config.readTimeout());
        }

        @Override
        public ProviderResponse post(
                SpeakingPromptAiContract.SttRequest request,
                SpeakingPromptAuthoringAiProperties.SttConfig config) {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", config.model());
            body.add("language", config.language());
            body.add("response_format", "json");
            body.add("file", new ByteArrayResource(request.audio().bytes()) {
                @Override
                public String getFilename() {
                    return request.audio().filename();
                }
            });
            try {
                OpenAiAudioHttpTransport.BoundedResponse response =
                        transport.postBounded(
                                "/audio/transcriptions",
                                MediaType.MULTIPART_FORM_DATA,
                                body,
                                MAX_RESPONSE_BYTES);
                return new ProviderResponse(
                        response.status(),
                        new String(response.body(), StandardCharsets.UTF_8),
                        null);
            } catch (OpenAiAudioHttpTransport.ResponseTooLargeException exception) {
                throw new SpeakingPromptAiContract.ProviderFailure(
                        SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                        false,
                        null,
                        exception);
            }
        }
    }
}
