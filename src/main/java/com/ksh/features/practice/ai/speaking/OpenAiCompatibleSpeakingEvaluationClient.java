package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiCompatibleSpeakingEvaluationClient implements SpeakingEvaluationClient {
    private static final String PROVIDER = "openai-compatible";

    private final SpeakingEvaluatorProperties properties;
    private final SpeakingEvaluationPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleEvaluationTransport transport;

    public OpenAiCompatibleSpeakingEvaluationClient(
            SpeakingEvaluatorProperties properties,
            SpeakingEvaluationPromptBuilder promptBuilder,
            ObjectMapper objectMapper
    ) {
        this(properties, promptBuilder, objectMapper, new RestClientEvaluationTransport(properties));
    }

    OpenAiCompatibleSpeakingEvaluationClient(
            SpeakingEvaluatorProperties properties,
            SpeakingEvaluationPromptBuilder promptBuilder,
            ObjectMapper objectMapper,
            OpenAiCompatibleEvaluationTransport transport
    ) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Override
    public SpeakingEvaluationProviderResult evaluate(SpeakingEvaluationRequest request) {
        long startNanos = System.nanoTime();
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, "MISSING_API_KEY", false, startNanos);
        }
        try {
            String raw = callWithRetry(requestBody(request));
            return parse(raw, startNanos);
        } catch (HttpStatusCodeException ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                    "PROVIDER_HTTP_ERROR", isRetryable(ex.getStatusCode()), startNanos);
        } catch (ResourceAccessException ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                    "PROVIDER_TRANSPORT_ERROR", true, startNanos);
        } catch (RuntimeException ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                    "PROVIDER_TRANSPORT_ERROR", true, startNanos);
        }
    }

    private Map<String, Object> requestBody(SpeakingEvaluationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("temperature", 0.0);
        body.put("top_p", 1.0);
        body.put("max_tokens", 4096);
        body.put("response_format", promptBuilder.responseFormat(request));
        body.put("messages", List.of(
                message("system", promptBuilder.systemPrompt(request)),
                message("user", promptBuilder.userPayload(request))));
        return body;
    }

    private String callWithRetry(Map<String, Object> body) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
            try {
                return transport.post(body);
            } catch (HttpStatusCodeException ex) {
                last = ex;
                if (isRetryable(ex.getStatusCode()) && attempt < properties.maxRetries()) {
                    continue;
                }
                throw ex;
            } catch (ResourceAccessException ex) {
                last = ex;
                if (attempt < properties.maxRetries()) {
                    continue;
                }
                throw ex;
            }
        }
        throw last == null ? new ResourceAccessException("Speaking evaluator unavailable") : last;
    }

    private SpeakingEvaluationProviderResult parse(String raw, long startNanos) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                    "PROVIDER_MALFORMED_JSON", false, startNanos);
        }
        String content = extractOutputText(root);
        if (content == null || content.isBlank()) {
            return failure(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                    "PROVIDER_EMPTY_RESPONSE", false, startNanos);
        }
        try {
            return SpeakingEvaluationProviderResult.success(
                    objectMapper.readTree(content), PROVIDER, properties.model(), elapsedMillis(startNanos));
        } catch (Exception ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                    "PROVIDER_MALFORMED_JSON", false, startNanos);
        }
    }

    private SpeakingEvaluationProviderResult failure(
            SpeakingEvaluationStatus status,
            String errorCategory,
            boolean retryable,
            long startNanos
    ) {
        return SpeakingEvaluationProviderResult.failure(
                status, PROVIDER, properties.model(), errorCategory, retryable, elapsedMillis(startNanos));
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private static String extractOutputText(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        if (choice.path("message").hasNonNull("content")) {
            return choice.path("message").path("content").asText();
        }
        if (root.hasNonNull("output_text")) {
            return root.path("output_text").asText();
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode contentItem : content) {
                        if (contentItem.has("text")) {
                            builder.append(contentItem.path("text").asText());
                        }
                    }
                }
            }
            if (!builder.isEmpty()) {
                return builder.toString();
            }
        }
        return null;
    }

    private static boolean isRetryable(HttpStatusCode status) {
        int value = status.value();
        return value == 429 || value == 500 || value == 502 || value == 503 || value == 504;
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    String transportBaseUrlForTest() {
        return transport.baseUrl();
    }

    interface OpenAiCompatibleEvaluationTransport {
        String post(Map<String, Object> body);

        String baseUrl();
    }

    private static class RestClientEvaluationTransport implements OpenAiCompatibleEvaluationTransport {
        private final SpeakingEvaluatorProperties properties;
        private final RestClient restClient;

        private RestClientEvaluationTransport(SpeakingEvaluatorProperties properties) {
            this.properties = properties;
            this.restClient = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                    .requestFactory(requestFactory(properties.timeout()))
                    .build();
        }

        @Override
        public String post(Map<String, Object> body) {
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
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
}
