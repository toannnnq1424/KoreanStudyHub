package com.ksh.features.practice.ai.transport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(
        prefix = "app.practice.ai.openai-primary",
        name = "enabled",
        havingValue = "true")
public class OpenAiPrimaryStructuredGenerationAdapter
        implements PracticeStructuredGenerationPort {

    private static final String PROVIDER = "openai-primary";

    private final OpenAiPrimaryCapabilityProperties properties;
    private final CanonicalPracticeJson canonicalJson;
    private final StrictOpenAiStructuredResponseDecoder decoder;
    private final OpenAiChatTransport transport;

    public OpenAiPrimaryStructuredGenerationAdapter(
            OpenAiPrimaryCapabilityProperties properties,
            CanonicalPracticeJson canonicalJson,
            StrictOpenAiStructuredResponseDecoder decoder) {
        this(
                properties,
                canonicalJson,
                decoder,
                new RestClientOpenAiChatTransport(properties));
    }

    OpenAiPrimaryStructuredGenerationAdapter(
            OpenAiPrimaryCapabilityProperties properties,
            CanonicalPracticeJson canonicalJson,
            StrictOpenAiStructuredResponseDecoder decoder,
            OpenAiChatTransport transport) {
        this.properties = properties;
        this.canonicalJson = canonicalJson;
        this.decoder = decoder;
        this.transport = transport;
    }

    @Override
    public ProviderIdentity identity(PracticeAiCapability capability) {
        return new ProviderIdentity(
                PROVIDER,
                properties.modelFor(capability),
                DisabledPracticeStructuredGenerationAdapter.profileFor(capability),
                properties.available(capability));
    }

    @Override
    public PracticeStructuredGenerationResponse generate(
            PracticeStructuredGenerationRequest request) {
        requireSupported(request);
        ProviderIdentity identity = identity(request.capability());
        if (!identity.available()) {
            throw new PracticeAiContractException(
                    "PROVIDER_CAPABILITY_UNAVAILABLE",
                    false);
        }

        CanonicalPracticeJson.CanonicalPayload inputPayload =
                canonicalJson.serialize(structuredInput(request));
        String idempotencyKey = idempotencyKey(
                request.idempotencyKey(),
                inputPayload.sha256());
        CanonicalPracticeJson.CanonicalPayload requestBody =
                canonicalJson.serialize(wireBody(
                        request,
                        identity.model(),
                        inputPayload.json()));

        byte[] rawResponse = callWithRetry(
                requestBody.json(),
                idempotencyKey);
        StrictOpenAiStructuredResponseDecoder.DecodedResponse decoded =
                decoder.decode(rawResponse, properties.maxResponseBytes());
        return new PracticeStructuredGenerationResponse(
                decoded.output(),
                PROVIDER,
                identity.model(),
                decoded.finishReason(),
                decoded.providerRequestId());
    }

    private void requireSupported(PracticeStructuredGenerationRequest request) {
        if (request.capability()
                != PracticeAiCapability.ASSESSMENT_TEXT_VISION) {
            throw new PracticeAiContractException(
                    "UNSUPPORTED_STRUCTURED_CAPABILITY",
                    false);
        }
        PracticeModelCapabilityProfile requested =
                request.modelCapabilityProfile();
        if (!requested.equals(
                PracticeModelCapabilityProfile.openAiAssessmentV1())
                || !requested.nativeJsonSchema()
                || requested.toolCalls()
                || requested.plainJsonFallback()
                || requested.streaming()) {
            throw new PracticeAiContractException(
                    "MODEL_CAPABILITY_PROFILE_MISMATCH",
                    false);
        }
        if (!requested.imageInput() && !request.images().isEmpty()) {
            throw new PracticeAiContractException(
                    "IMAGE_INPUT_UNSUPPORTED",
                    false);
        }
    }

    private Map<String, Object> structuredInput(
            PracticeStructuredGenerationRequest request) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(
                "profileVersion",
                request.modelCapabilityProfile().profileVersion());
        profile.put(
                "nativeJsonSchema",
                request.modelCapabilityProfile().nativeJsonSchema());
        profile.put("toolCalls", request.modelCapabilityProfile().toolCalls());
        profile.put(
                "plainJsonFallback",
                request.modelCapabilityProfile().plainJsonFallback());
        profile.put(
                "imageInput",
                request.modelCapabilityProfile().imageInput());
        profile.put("streaming", request.modelCapabilityProfile().streaming());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", request.authority().schemaVersion());
        envelope.put("promptVersion", request.authority().promptVersion());
        envelope.put("strategyCode", request.authority().strategyCode());
        envelope.put("strategyVersion", request.authority().strategyVersion());
        envelope.put(
                "authorityIdentity",
                request.authority().authorityIdentity());
        envelope.put("modelCapabilityProfile", profile);
        envelope.put("operation", request.operation());
        envelope.put("input", request.input());
        return envelope;
    }

    private Map<String, Object> wireBody(
            PracticeStructuredGenerationRequest request,
            String model,
            String structuredInputJson) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", request.systemInstruction()));
        if (!request.developerInstruction().isBlank()) {
            messages.add(message(
                    "developer",
                    request.developerInstruction()));
        }
        messages.add(message(
                "user",
                userContent(structuredInputJson, request.images())));

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", request.responseSchemaName());
        jsonSchema.put("strict", Boolean.TRUE);
        jsonSchema.put("schema", request.responseSchema());

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.0);
        body.put("top_p", 1.0);
        body.put("max_tokens", request.maxOutputTokens());
        body.put("messages", messages);
        body.put("response_format", responseFormat);
        return body;
    }

    private static List<Map<String, Object>> userContent(
            String structuredInputJson,
            List<PracticeStructuredGenerationRequest.ImageEvidence> images) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type",
                "text",
                "text",
                structuredInputJson));
        for (PracticeStructuredGenerationRequest.ImageEvidence image : images) {
            content.add(Map.of(
                    "type",
                    "image_url",
                    "image_url",
                    Map.of(
                            "url",
                            image.dataUrl(),
                            "detail",
                            image.detail())));
        }
        return List.copyOf(content);
    }

    private byte[] callWithRetry(
            String canonicalRequestJson,
            String idempotencyKey) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
            requireActiveThread();
            try {
                return transport.post(canonicalRequestJson, idempotencyKey);
            } catch (HttpStatusCodeException exception) {
                last = exception;
                if (!retryable(exception.getStatusCode())
                        || attempt >= properties.maxRetries()) {
                    throw exception;
                }
            } catch (ResourceAccessException exception) {
                last = exception;
                if (attempt >= properties.maxRetries()) {
                    throw exception;
                }
            }
        }
        throw last == null
                ? new ResourceAccessException("OpenAI primary unavailable")
                : last;
    }

    private static Map<String, Object> message(String role, Object content) {
        return Map.of("role", role, "content", content);
    }

    private static String idempotencyKey(
            String requested,
            String payloadSha256) {
        if (requested == null || requested.isBlank()) {
            return "ksh-practice-" + payloadSha256;
        }
        if (requested.length() > 128
                || !requested.matches("[A-Za-z0-9._:-]+")) {
            throw new PracticeAiContractException(
                    "INVALID_IDEMPOTENCY_KEY",
                    false);
        }
        return requested;
    }

    private static boolean retryable(HttpStatusCode status) {
        int value = status.value();
        return value == 429
                || value == 500
                || value == 502
                || value == 503
                || value == 504;
    }

    private static void requireActiveThread() {
        if (Thread.currentThread().isInterrupted()) {
            throw new PracticeAiContractException(
                    "EVALUATION_INTERRUPTED",
                    false);
        }
    }

    interface OpenAiChatTransport {
        byte[] post(String canonicalRequestJson, String idempotencyKey);
    }

    private static final class RestClientOpenAiChatTransport
            implements OpenAiChatTransport {

        private final RestClient restClient;

        private RestClientOpenAiChatTransport(
                OpenAiPrimaryCapabilityProperties properties) {
            SimpleClientHttpRequestFactory requestFactory =
                    new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(
                    timeoutMillis(properties.connectTimeout()));
            requestFactory.setReadTimeout(
                    timeoutMillis(properties.readTimeout()));
            this.restClient = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .defaultHeader(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + properties.apiKey())
                    .requestFactory(requestFactory)
                    .build();
        }

        @Override
        public byte[] post(
                String canonicalRequestJson,
                String idempotencyKey) {
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(canonicalRequestJson)
                    .retrieve()
                    .body(byte[].class);
        }

        private static int timeoutMillis(Duration duration) {
            return Math.toIntExact(Math.min(
                    duration.toMillis(),
                    Integer.MAX_VALUE));
        }
    }
}
