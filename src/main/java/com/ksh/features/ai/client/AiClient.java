package com.ksh.features.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.AiProvider;
import com.ksh.features.admin.settings.repository.AiProviderRepository;
import com.ksh.features.ai.log.AiRequestLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends chat completion requests to the admin-configured AI providers, falling back
 * down the list when a provider fails.
 *
 * <p><b>Fallback policy.</b> Providers are tried in {@code display_order} ascending:
 * <ul>
 *   <li>Each attempt is logged independently.</li>
 *   <li>Network, HTTP and embedded provider errors advance to the next provider.</li>
 *   <li>The chain fails only after every enabled provider has failed.</li>
 * </ul>
 * Credentials, models and API dialects are configured per provider, so a rejection from
 * one endpoint is not evidence that a later endpoint will reject the request.
 *
 * <p>Disabled providers are excluded by the repository query and are therefore never
 * contacted. When no enabled provider exists at all, the call fails fast with a message
 * telling the admin that AI is not configured.
 *
 * <p>The provider list is read on every call — it is intentionally not cached, so a
 * settings change takes effect on the very next request.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // Reasoning models and document-backed generation regularly need more than
    // 30 seconds even while progressing normally.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final int MAX_SUCCESS_BODY_BYTES = 1_048_576;
    private static final int MAX_ERROR_BODY_BYTES = 2_048;
    private static final int MAX_ERROR_DETAIL_CHARS = 300;
    private static final ObjectMapper RESPONSE_MAPPER = new ObjectMapper();

    private static final String MSG_NOT_CONFIGURED =
            "Chưa cấu hình AI provider nào đang bật. Vào Cài đặt hệ thống → AI để thêm provider.";
    private static final String MSG_ALL_FAILED_PREFIX =
            "Tất cả AI provider đều thất bại: ";

    private final AiProviderRepository repository;
    private final RestClient restClient;
    private final AiRequestLogger requestLogger;

    /**
     * Builds the client on top of Spring's auto-configured {@link RestClient.Builder}.
     *
     * <p>Spring Boot declares that builder as a {@code prototype}-scoped bean
     * (see {@code RestClientAutoConfiguration#restClientBuilder}), so every injection
     * point already receives its own cloned instance. Calling {@code clone()} here would
     * therefore be redundant — mutating this builder cannot leak into another consumer.
     *
     * <p>The connect/read timeouts are applied through
     * {@link #applyDefaultTimeouts(RestClient.Builder)}, which a test may skip by handing
     * in a builder that already carries a request factory.
     */
    @Autowired
    public AiClient(AiProviderRepository repository, RestClient.Builder builder,
                    AiRequestLogger requestLogger) {
        this(repository, builder, requestLogger, true);
    }

    private AiClient(AiProviderRepository repository, RestClient.Builder builder,
                     AiRequestLogger requestLogger, boolean applyTimeouts) {
        this.repository = repository;
        this.requestLogger = requestLogger;
        this.restClient = (applyTimeouts ? applyDefaultTimeouts(builder) : builder).build();
    }

    /**
     * Builds a client that uses the given builder's request factory as-is.
     *
     * <p>Test-only seam. {@code MockRestServiceServer.bindTo(builder)} installs its mock
     * request factory on the builder, and {@code RestClient.Builder#build()} snapshots that
     * factory — so a client that unconditionally re-set its own factory would silently
     * discard the mock and hit the network instead. This entry point skips the timeout
     * factory so the stubbed transport survives; production code must use the public
     * constructor, which always applies the timeouts.
     *
     * @param builder       a builder whose request factory is already configured by the caller
     * @param requestLogger the logger that records each provider attempt
     * @return a client wired to that transport
     */
    public static AiClient withPreconfiguredTransport(AiProviderRepository repository,
                                                      RestClient.Builder builder,
                                                      AiRequestLogger requestLogger) {
        return new AiClient(repository, builder, requestLogger, false);
    }

    /** Installs the production connect/read timeouts on the given builder. */
    private static RestClient.Builder applyDefaultTimeouts(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return builder.requestFactory(factory);
    }

    /**
     * Sends a single user message through the fallback chain and returns the assistant's
     * reply text.
     *
     * @param userMessage the message to send as the sole {@code user} turn
     * @param maxTokens   upper bound on the generated response length
     * @return the assistant reply from the first provider that answered successfully
     * @throws AiClientException when no provider is enabled or every provider failed
     */
    public String chat(String userMessage, int maxTokens) {
        return chat(userMessage, maxTokens, null);
    }

    /**
     * Same as {@link #chat(String, int)} but attributes the logged attempts to a user.
     *
     * @param userMessage the message to send as the sole {@code user} turn
     * @param maxTokens   upper bound on the generated response length
     * @param userId      the acting user recorded on each log row, or {@code null}
     * @return the assistant reply from the first provider that answered successfully
     */
    public String chat(String userMessage, int maxTokens, Long userId) {
        return chat(null, userMessage, maxTokens, userId, AiRequestLogger.SOURCE_CHAT);
    }

    /**
     * Sends a system instruction and user turn through the configured fallback chain.
     *
     * <p>A blank system prompt is omitted rather than sent as an empty turn. The source
     * value is recorded for every provider attempt so feature-specific usage can be
     * audited without creating a second AI transport.
     */
    public String chat(String systemPrompt, String userMessage, int maxTokens, Long userId,
                       String source) {
        return chat(systemPrompt, userMessage, maxTokens, userId, source, false);
    }

    /** Sends a chat completion and asks compatible providers to enforce one JSON object. */
    public String chatJsonObject(String systemPrompt, String userMessage, int maxTokens,
                                 Long userId, String source) {
        return chat(systemPrompt, userMessage, maxTokens, userId, source, true);
    }

    private String chat(String systemPrompt, String userMessage, int maxTokens, Long userId,
                        String source, boolean jsonObject) {
        List<AiProvider> providers = repository.findEnabledOrdered();
        if (providers.isEmpty()) {
            throw new AiClientException(MSG_NOT_CONFIGURED);
        }

        List<String> failures = new ArrayList<>();
        for (AiProvider provider : providers) {
            try {
                return callProvider(provider, buildMessages(systemPrompt, userMessage),
                        maxTokens, source, userId, jsonObject).content();
            } catch (RuntimeException e) {
                // Credentials, model names and API dialects are provider-specific. A 4xx
                // from one endpoint must not block a later, independent provider.
                log.warn("AI provider '{}' failed: {}", provider.getName(), e.toString());
                failures.add(provider.getName() + ": " + describe(e));
            }
        }
        throw new AiClientException(MSG_ALL_FAILED_PREFIX + String.join("; ", failures));
    }

    /**
     * Sends one chat message to a single named provider, bypassing the fallback chain.
     *
     * <p>Used by the admin "test connection" button, which must report the failure of
     * exactly the provider under test rather than silently succeeding through a different
     * one. Failures propagate as runtime exceptions carrying a human-readable message.
     *
     * @param provider    the provider to contact
     * @param userMessage the message to send
     * @param maxTokens   upper bound on the generated response length
     * @return the reply text when the call succeeded
     */
    public String callOne(AiProvider provider, String userMessage, int maxTokens) {
        return callOne(provider, userMessage, maxTokens, AiRequestLogger.SOURCE_CHAT, null);
    }

    /**
     * Same as {@link #callOne(AiProvider, String, int)} but tags the logged attempt with
     * what triggered it and who was acting.
     *
     * @param provider    the provider to contact
     * @param userMessage the message to send
     * @param maxTokens   upper bound on the generated response length
     * @param source      what triggered the call, e.g. {@code TEST_CONNECTION}
     * @param userId      the acting user recorded on the log row, or {@code null}
     * @return the reply text when the call succeeded
     */
    public String callOne(AiProvider provider, String userMessage, int maxTokens,
                          String source, Long userId) {
        return callProvider(provider, buildMessages(null, userMessage), maxTokens,
                source, userId, false).content();
    }

    // ─────────────────────────────────────────────────────────────────

    private static List<Map<String, Object>> buildMessages(String systemPrompt,
                                                            String userMessage) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return List.of(Map.of("role", "user", "content", userMessage));
        }
        return List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage));
    }

    /**
     * Performs one chat completion call against a single provider and records the
     * attempt in {@code ai_request_logs}, whether it succeeded or failed.
     *
     * <p>Exactly one log row is written per invocation. Failures are logged and then
     * rethrown unchanged, so the fallback policy in {@link #chat(String, int, Long)} is
     * unaffected by the logging.
     *
     * @throws ProviderResponseException when this provider rejects the request
     */
    private AiResult callProvider(AiProvider provider, List<Map<String, Object>> messages,
                                  int maxTokens,
                                  String source, Long userId, boolean jsonObject) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", provider.getModel());
        payload.put("max_tokens", maxTokens);
        payload.put("stream", false);
        payload.put("messages", messages);
        if (jsonObject) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        long startedAt = System.nanoTime();
        try {
            Map<?, ?> body = restClient.post()
                    .uri(normalizeBaseUrl(provider.getBaseUrl()) + CHAT_COMPLETIONS_PATH)
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            throw classify(status, readErrorBody(response.getBody()));
                        }
                        return readSuccessBody(response.getBody());
                    });

            AiResult result = new AiResult(extractContent(body), extractUsage(body));
            requestLogger.logSuccess(provider, result.usage(), elapsedMs(startedAt), source, userId);
            return result;
        } catch (RuntimeException e) {
            requestLogger.logFailure(provider, describe(e), elapsedMs(startedAt), source, userId);
            throw e;
        }
    }

    /** Wall-clock duration since the given {@code System.nanoTime()} reading. */
    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /**
     * Maps an HTTP rejection to a provider-scoped failure. The fallback loop decides
     * whether another independently configured provider should be tried.
     */
    private static RuntimeException classify(HttpStatusCode status, String body) {
        String detail = "HTTP " + status.value() + (body.isBlank() ? "" : " — " + body);
        return new ProviderResponseException(detail);
    }

    /** Reads a bounded prefix so a huge or endless provider body cannot exhaust memory. */
    private static String readErrorBody(java.io.InputStream in) {
        try (in) {
            byte[] prefix = in.readNBytes(MAX_ERROR_BODY_BYTES);
            String raw = new String(prefix, java.nio.charset.StandardCharsets.UTF_8).trim();
            return raw.length() > MAX_ERROR_DETAIL_CHARS
                    ? raw.substring(0, MAX_ERROR_DETAIL_CHARS) + "…"
                    : raw;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Parses a successful JSON response through a bounded byte buffer. Token limits do
     * not constrain a malicious or broken provider's HTTP body, so relying on
     * {@code bodyTo(Map.class)} alone could allocate an arbitrarily large response.
     */
    @SuppressWarnings("unchecked")
    private static Map<?, ?> readSuccessBody(java.io.InputStream in) {
        try (in) {
            byte[] body = in.readNBytes(MAX_SUCCESS_BODY_BYTES + 1);
            if (body.length > MAX_SUCCESS_BODY_BYTES) {
                throw new TransientProviderException(
                        "Phản hồi vượt quá giới hạn kích thước cho phép");
            }
            return RESPONSE_MAPPER.readValue(body, Map.class);
        } catch (TransientProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TransientProviderException("Phản hồi JSON không hợp lệ");
        }
    }

    /** Pulls {@code choices[0].message.content} out of an OpenAI-compatible response. */
    @SuppressWarnings("unchecked")
    private static String extractContent(Map<?, ?> body) {
        if (body == null) {
            throw new TransientProviderException("Phản hồi rỗng");
        }
        Object choices = body.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            throw new TransientProviderException("Phản hồi không có trường 'choices'");
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            throw new TransientProviderException("Phản hồi có định dạng không mong đợi");
        }
        failOnEmbeddedError(choice);
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) {
            throw new TransientProviderException("Phản hồi thiếu trường 'message'");
        }
        Object content = msg.get("content");
        return content == null ? "" : content.toString();
    }

    /** Rejects gateways that encode an upstream failure inside an HTTP 200 response. */
    private static void failOnEmbeddedError(Map<?, ?> choice) {
        Object error = choice.get("error");
        boolean finishedWithError = "error".equals(choice.get("finish_reason"));
        if (!finishedWithError && !(error instanceof Map<?, ?>)) {
            return;
        }
        Map<?, ?> details = error instanceof Map<?, ?> map ? map : Map.of();
        Object code = details.get("code");
        Object message = details.get("message");
        String detail = "Provider báo lỗi"
                + (code == null ? "" : " (code " + code + ")")
                + (message == null ? "" : ": " + message);
        throw new ProviderResponseException(detail);
    }

    /**
     * Pulls the OpenAI-compatible {@code usage} object out of a response.
     *
     * <p>Absent or malformed usage yields a record of three {@code null}s rather than
     * zeros — the call did consume tokens, we simply were not told how many, and
     * recording zero would misreport it as free.
     *
     * @param body the parsed response body
     * @return the token counts, each member possibly {@code null}
     */
    private static AiRequestLogger.TokenUsage extractUsage(Map<?, ?> body) {
        Object usage = body == null ? null : body.get("usage");
        if (!(usage instanceof Map<?, ?> map)) {
            return new AiRequestLogger.TokenUsage(null, null, null);
        }
        return new AiRequestLogger.TokenUsage(
                toInteger(map.get("prompt_tokens")),
                toInteger(map.get("completion_tokens")),
                toInteger(map.get("total_tokens")));
    }

    /**
     * Coerces a JSON number to {@link Integer}, tolerating the {@code Double} that a
     * lenient parser may hand back for an integral value.
     *
     * @return the value as an int, or {@code null} when absent or not numeric
     */
    private static Integer toInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    /**
     * Strips a single trailing slash so {@code baseUrl + path} never yields a double slash.
     *
     * <p>Public because the settings service normalizes the same way before persisting,
     * so a URL saved with a trailing slash and one saved without end up identical in the
     * database rather than differing only at call time.
     *
     * @param baseUrl the configured endpoint, possibly {@code null} or padded with spaces
     * @return the trimmed URL without a trailing slash; empty string when {@code null}
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** Renders a failure for the aggregated "all providers failed" message. */
    private static String describe(RuntimeException e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    /**
     * One successful provider answer: the reply text plus whatever token counts the
     * provider reported.
     *
     * <p>Internal to this class. The public {@code chat} and {@code callOne} entry points
     * still return a bare {@link String}, so adding token capture did not change any
     * caller's contract.
     */
    private record AiResult(String content, AiRequestLogger.TokenUsage usage) {
    }

    /** Failure another provider might succeed at — network, timeout, 5xx, 429. */
    static class TransientProviderException extends RuntimeException {
        TransientProviderException(String message) {
            super(message);
        }
    }

    /** A provider-specific HTTP or embedded response rejection. */
    static class ProviderResponseException extends RuntimeException {
        ProviderResponseException(String message) {
            super(message);
        }
    }
}
