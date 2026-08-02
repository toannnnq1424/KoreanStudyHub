package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionAuditService;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderTransport;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
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
import java.util.Map;

@Service
public class OpenAiSpeakingPromptSttAdapter implements SpeakingPromptSttPort {

    private final SpeakingPromptAuthoringAiProperties properties;
    private final ObjectMapper objectMapper;
    private final PracticeAiMetrics metrics;
    private final SttTransport overrideTransport;
    private final PracticeAiBindingResolver bindingResolver;
    private final PracticeAiExecutionAuditService auditService;
    private final PracticeAiProviderTransport providerTransport;

    @Autowired
    public OpenAiSpeakingPromptSttAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            ObjectMapper objectMapper,
            PracticeAiMetrics metrics,
            PracticeAiBindingResolver bindingResolver,
            PracticeAiExecutionAuditService auditService,
            PracticeAiProviderTransport providerTransport) {
        this(properties, objectMapper, metrics, null, bindingResolver,
                auditService, providerTransport);
    }

    OpenAiSpeakingPromptSttAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            ObjectMapper objectMapper,
            PracticeAiMetrics metrics,
            SttTransport overrideTransport) {
        this(properties, objectMapper, metrics, overrideTransport, null, null, null);
    }

    private OpenAiSpeakingPromptSttAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            ObjectMapper objectMapper,
            PracticeAiMetrics metrics,
            SttTransport overrideTransport,
            PracticeAiBindingResolver bindingResolver,
            PracticeAiExecutionAuditService auditService,
            PracticeAiProviderTransport providerTransport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.overrideTransport = overrideTransport;
        this.bindingResolver = bindingResolver;
        this.auditService = auditService;
        this.providerTransport = providerTransport;
    }

    @Override
    public SpeakingPromptAiContract.SttResult transcribe(
            SpeakingPromptAiContract.SttRequest request) {
        long started = PracticeAiMetrics.startNanos();
        Long executionAuditId = null;
        try {
            PracticeAiResolvedBinding binding = null;
            if (bindingResolver != null) {
                binding = bindingResolver.resolve(
                        PracticeAiPurpose.PRACTICE_SPEAKING_STT);
            }
            SpeakingPromptAuthoringAiProperties.SttConfig config =
                    properties.sttConfig();
            if (binding != null) {
                requireSameBinding(config, binding);
                executionAuditId = auditService.start(
                        binding.snapshot(),
                        "AUTHORING_PROMPT_STT",
                        request == null ? "missing-request" : request.contractVersion(),
                        "LECTURER_PROMPT_AUDIO");
                bindingResolver.assertCurrent(binding.snapshot());
            }
            requireEnabledOpenAi(config);
            validateRequest(request, config);
            ProviderResponse response = callOnce(request, config, binding);
            if (response.status() < 200 || response.status() >= 300) {
                throw httpFailure(response.status(), response.requestReference());
            }
            SpeakingPromptAiContract.SttResult result =
                    parse(response, config);
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    PracticeAiMetrics.ProviderOutcome.SUCCESS,
                    PracticeAiMetrics.elapsedSince(started));
            success(executionAuditId);
            return result;
        } catch (PracticeAiControlPlaneException failure) {
            failed(executionAuditId, failure.errorCode());
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    PracticeAiMetrics.ProviderOutcome.UNAVAILABLE,
                    PracticeAiMetrics.elapsedSince(started));
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                    false,
                    null,
                    failure);
        } catch (SpeakingPromptAiContract.ProviderFailure failure) {
            failed(executionAuditId, failure.publicCategory().name());
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    outcome(failure.publicCategory()),
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        } catch (ResourceAccessException exception) {
            failed(executionAuditId, "PROVIDER_TRANSPORT_ERROR");
            SpeakingPromptAiContract.ProviderFailure failure =
                    transportFailure(exception);
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_STT,
                    PracticeAiMetrics.ProviderOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        } catch (RuntimeException exception) {
            failed(executionAuditId, "PROVIDER_TRANSPORT_ERROR");
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

    private static void requireSameBinding(
            SpeakingPromptAuthoringAiProperties.SttConfig config,
            PracticeAiResolvedBinding binding) {
        if (!config.baseUrl().equals(binding.baseUrl().toString())
                || !config.apiKey().equals(binding.credentialSecret())
                || !config.model().equals(binding.snapshot().model())
                || !config.purposeCode().equals(binding.snapshot().purpose().name())
                || !config.retentionCode().equals(binding.snapshot().retentionCode())) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_BINDING_CHANGED", false);
        }
    }

    private void success(Long auditId) {
        if (auditId != null) {
            auditService.success(auditId);
        }
    }

    private void failed(Long auditId, String errorCode) {
        if (auditId != null) {
            auditService.failure(auditId, errorCode);
        }
    }

    private ProviderResponse callOnce(
            SpeakingPromptAiContract.SttRequest request,
            SpeakingPromptAuthoringAiProperties.SttConfig config,
            PracticeAiResolvedBinding binding) {
        if (binding != null) {
            MultiValueMap<String, Object> body = multipart(request, config);
            PracticeAiProviderTransport.ProviderResponse response =
                    providerTransport.exchange(
                            binding,
                            "/audio/transcriptions",
                            MediaType.MULTIPART_FORM_DATA,
                            MediaType.APPLICATION_JSON,
                            body,
                            Map.of());
            return new ProviderResponse(
                    response.status(),
                    new String(response.body(), StandardCharsets.UTF_8),
                    response.requestReference());
        }
        if (overrideTransport == null) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                    false, null, null);
        }
        return overrideTransport.post(request, config);
    }

    private static MultiValueMap<String, Object> multipart(
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
        return body;
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

}
