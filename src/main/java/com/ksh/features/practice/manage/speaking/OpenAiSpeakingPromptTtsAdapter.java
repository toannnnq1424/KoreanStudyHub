package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionAuditService;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderTransport;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class OpenAiSpeakingPromptTtsAdapter implements SpeakingPromptTtsPort {

    private final SpeakingPromptAuthoringAiProperties properties;
    private final SpeakingPromptAudioVerifier audioVerifier;
    private final PracticeAiMetrics metrics;
    private final TtsTransport overrideTransport;
    private final PracticeAiBindingResolver bindingResolver;
    private final PracticeAiExecutionAuditService auditService;
    private final PracticeAiProviderTransport providerTransport;

    @Autowired
    public OpenAiSpeakingPromptTtsAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            SpeakingPromptAudioVerifier audioVerifier,
            PracticeAiMetrics metrics,
            PracticeAiBindingResolver bindingResolver,
            PracticeAiExecutionAuditService auditService,
            PracticeAiProviderTransport providerTransport) {
        this(properties, audioVerifier, metrics, null, bindingResolver,
                auditService, providerTransport);
    }

    OpenAiSpeakingPromptTtsAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            SpeakingPromptAudioVerifier audioVerifier,
            PracticeAiMetrics metrics,
            TtsTransport overrideTransport) {
        this(properties, audioVerifier, metrics, overrideTransport, null, null, null);
    }

    private OpenAiSpeakingPromptTtsAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            SpeakingPromptAudioVerifier audioVerifier,
            PracticeAiMetrics metrics,
            TtsTransport overrideTransport,
            PracticeAiBindingResolver bindingResolver,
            PracticeAiExecutionAuditService auditService,
            PracticeAiProviderTransport providerTransport) {
        this.properties = properties;
        this.audioVerifier = audioVerifier;
        this.metrics = metrics;
        this.overrideTransport = overrideTransport;
        this.bindingResolver = bindingResolver;
        this.auditService = auditService;
        this.providerTransport = providerTransport;
    }

    @Override
    public SpeakingPromptAiContract.TtsResult synthesize(
            SpeakingPromptAiContract.TtsRequest request) {
        long started = PracticeAiMetrics.startNanos();
        Long executionAuditId = null;
        try {
            PracticeAiResolvedBinding binding = null;
            if (bindingResolver != null) {
                binding = bindingResolver.resolve(
                        PracticeAiPurpose.PRACTICE_SPEAKING_TTS);
            }
            SpeakingPromptAuthoringAiProperties.TtsConfig config =
                    properties.ttsConfig();
            if (binding != null) {
                requireSameBinding(config, binding);
                executionAuditId = auditService.start(
                        binding.snapshot(),
                        "AUTHORING_PROMPT_TTS",
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
            if (response.body().length == 0) {
                throw new SpeakingPromptAiContract.ProviderFailure(
                        SpeakingPromptAiContract.PublicErrorCategory.EMPTY_OUTPUT,
                        false,
                        response.requestReference(),
                        null);
            }
            String contentType = normalized(response.contentType());
            if (!config.allowedOutputMimeTypes().contains(contentType)) {
                throw new SpeakingPromptAiContract.ProviderFailure(
                        SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                        false,
                        response.requestReference(),
                        null);
            }
            SpeakingPromptAiContract.VerifiedAudio audio;
            try {
                audio = audioVerifier.verifyTtsOutput(
                        response.body(),
                        "speaking-prompt-ai." + request.outputFormat(),
                        contentType);
            } catch (RuntimeException exception) {
                throw new SpeakingPromptAiContract.ProviderFailure(
                        SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                        false,
                        response.requestReference(),
                        exception);
            }
            SpeakingPromptAiContract.TtsResult result =
                    new SpeakingPromptAiContract.TtsResult(
                            audio,
                            config.provider(),
                            config.model(),
                            config.language(),
                            request.voiceCode(),
                            request.speed(),
                            request.outputFormat(),
                            response.requestReference(),
                            config.purposeCode(),
                            config.retentionCode());
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
                    PracticeAiMetrics.ProviderOutcome.SUCCESS,
                    PracticeAiMetrics.elapsedSince(started));
            success(executionAuditId);
            return result;
        } catch (PracticeAiControlPlaneException failure) {
            failed(executionAuditId, failure.errorCode());
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
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
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
                    outcome(failure.publicCategory()),
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        } catch (ResourceAccessException exception) {
            failed(executionAuditId, "PROVIDER_TRANSPORT_ERROR");
            SpeakingPromptAiContract.ProviderFailure failure =
                    transportFailure(exception);
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
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
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
                    PracticeAiMetrics.ProviderOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        }
    }

    private static void requireSameBinding(
            SpeakingPromptAuthoringAiProperties.TtsConfig config,
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
            SpeakingPromptAiContract.TtsRequest request,
            SpeakingPromptAuthoringAiProperties.TtsConfig config,
            PracticeAiResolvedBinding binding) {
        if (binding != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("input", request.promptText());
            body.put("voice", request.voiceCode());
            body.put("response_format", request.outputFormat());
            body.put("speed", request.speed());
            PracticeAiProviderTransport.ProviderResponse response =
                    providerTransport.exchange(
                            binding,
                            "/audio/speech",
                            MediaType.APPLICATION_JSON,
                            MediaType.ALL,
                            body,
                            Map.of());
            return new ProviderResponse(
                    response.status(),
                    response.body(),
                    response.contentType(),
                    response.requestReference());
        }
        if (overrideTransport == null) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                    false, null, null);
        }
        return overrideTransport.post(request, config);
    }

    private static void validateRequest(
            SpeakingPromptAiContract.TtsRequest request,
            SpeakingPromptAuthoringAiProperties.TtsConfig config) {
        if (request == null
                || request.promptText() == null
                || request.promptText().isEmpty()
                || request.promptText().length() > config.maxInputCharacters()
                || !config.language().equalsIgnoreCase(request.languageTag())
                || !config.allowedOutputFormats().contains(
                        request.outputFormat().toLowerCase(Locale.ROOT))) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.INVALID_INPUT,
                    false,
                    null,
                    null);
        }
    }

    private static void requireEnabledOpenAi(
            SpeakingPromptAuthoringAiProperties.TtsConfig config) {
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

    private static String normalized(String value) {
        if (value == null) {
            return "";
        }
        int separator = value.indexOf(';');
        String base = separator < 0 ? value : value.substring(0, separator);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    interface TtsTransport {
        ProviderResponse post(
                SpeakingPromptAiContract.TtsRequest request,
                SpeakingPromptAuthoringAiProperties.TtsConfig config);
    }

    record ProviderResponse(
            int status,
            byte[] body,
            String contentType,
            String requestReference) {
        public ProviderResponse {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        @Override
        public String toString() {
            return "ProviderResponse{status=" + status
                    + ", byteSize=" + body.length
                    + ", contentType='" + contentType + '\''
                    + '}';
        }
    }

}
