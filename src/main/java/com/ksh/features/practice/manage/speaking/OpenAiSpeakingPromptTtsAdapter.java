package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import com.ksh.features.practice.service.audio.OpenAiAudioHttpTransport;
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

    @Autowired
    public OpenAiSpeakingPromptTtsAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            SpeakingPromptAudioVerifier audioVerifier,
            PracticeAiMetrics metrics) {
        this(properties, audioVerifier, metrics, null);
    }

    OpenAiSpeakingPromptTtsAdapter(
            SpeakingPromptAuthoringAiProperties properties,
            SpeakingPromptAudioVerifier audioVerifier,
            PracticeAiMetrics metrics,
            TtsTransport overrideTransport) {
        this.properties = properties;
        this.audioVerifier = audioVerifier;
        this.metrics = metrics;
        this.overrideTransport = overrideTransport;
    }

    @Override
    public SpeakingPromptAiContract.TtsResult synthesize(
            SpeakingPromptAiContract.TtsRequest request) {
        long started = PracticeAiMetrics.startNanos();
        SpeakingPromptAuthoringAiProperties.TtsConfig config =
                properties.ttsConfig();
        try {
            requireEnabledOpenAi(config);
            validateRequest(request, config);
            ProviderResponse response = callOnce(request, config);
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
            return result;
        } catch (SpeakingPromptAiContract.ProviderFailure failure) {
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
                    outcome(failure.publicCategory()),
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        } catch (ResourceAccessException exception) {
            SpeakingPromptAiContract.ProviderFailure failure =
                    transportFailure(exception);
            metrics.recordProviderOperation(
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
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
                    PracticeAiMetrics.ProviderFeature.SPEAKING_PROMPT_TTS,
                    PracticeAiMetrics.ProviderOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(started));
            throw failure;
        }
    }

    private ProviderResponse callOnce(
            SpeakingPromptAiContract.TtsRequest request,
            SpeakingPromptAuthoringAiProperties.TtsConfig config) {
        TtsTransport transport = overrideTransport == null
                ? new RestClientTtsTransport(config)
                : overrideTransport;
        return transport.post(request, config);
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

    private static final class RestClientTtsTransport implements TtsTransport {
        private final OpenAiAudioHttpTransport transport;

        private RestClientTtsTransport(
                SpeakingPromptAuthoringAiProperties.TtsConfig config) {
            transport = new OpenAiAudioHttpTransport(
                    config.baseUrl(),
                    config.apiKey(),
                    config.connectTimeout(),
                    config.readTimeout());
        }

        @Override
        public ProviderResponse post(
                SpeakingPromptAiContract.TtsRequest request,
                SpeakingPromptAuthoringAiProperties.TtsConfig config) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("input", request.promptText());
            body.put("voice", request.voiceCode());
            body.put("response_format", request.outputFormat());
            body.put("speed", request.speed());
            try {
                OpenAiAudioHttpTransport.BoundedResponse response =
                        transport.postBounded(
                                "/audio/speech",
                                MediaType.APPLICATION_JSON,
                                body,
                                config.maxOutputBytes());
                return new ProviderResponse(
                        response.status(),
                        response.body(),
                        response.contentType(),
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
