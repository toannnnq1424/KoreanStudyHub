package com.ksh.features.practice.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
public class OpenAiProperties {

    private static final int DEFAULT_EVALUATOR_MAX_OUTPUT_TOKENS = 4096;
    private static final int MIN_EVALUATOR_MAX_OUTPUT_TOKENS = 256;
    private static final int MAX_EVALUATOR_MAX_OUTPUT_TOKENS = 32768;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration MIN_CONNECT_TIMEOUT = Duration.ofMillis(100);
    private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MIN_READ_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_READ_TIMEOUT = Duration.ofMinutes(2);

    private final String apiKey;
    private final String evaluatorModel;
    private final String transcriptionModel;
    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final int evaluatorMaxOutputTokens;
    private final boolean evaluatorStructuredOutputEnabled;
    private final boolean evaluatorReasoningEffortEnabled;
    private final String evaluatorReasoningEffort;

    @Autowired
    public OpenAiProperties(@Value("${openai.api-key:}") String apiKey,
                            @Value("${openai.evaluator-model:models/gemini-2.5-flash-lite}") String evaluatorModel,
                            @Value("${openai.transcription-model:gpt-4o-transcribe}") String transcriptionModel,
                            @Value("${openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl,
                            @Value("${openai.connect-timeout:5s}") Duration connectTimeout,
                            @Value("${openai.read-timeout:60s}") Duration readTimeout,
                            @Value("${openai.evaluator-max-output-tokens:4096}") int evaluatorMaxOutputTokens,
                            @Value("${openai.evaluator-structured-output-enabled:true}") boolean evaluatorStructuredOutputEnabled,
                            @Value("${openai.evaluator-reasoning-effort-enabled:false}") boolean evaluatorReasoningEffortEnabled,
                            @Value("${openai.evaluator-reasoning-effort:}") String evaluatorReasoningEffort) {
        this.apiKey = apiKey;
        this.evaluatorModel = evaluatorModel;
        this.transcriptionModel = transcriptionModel;
        this.baseUrl = baseUrl;
        this.connectTimeout = bounded(
                connectTimeout, DEFAULT_CONNECT_TIMEOUT, MIN_CONNECT_TIMEOUT, MAX_CONNECT_TIMEOUT);
        this.readTimeout = bounded(
                readTimeout, DEFAULT_READ_TIMEOUT, MIN_READ_TIMEOUT, MAX_READ_TIMEOUT);
        this.evaluatorMaxOutputTokens = Math.max(
                MIN_EVALUATOR_MAX_OUTPUT_TOKENS,
                Math.min(
                        MAX_EVALUATOR_MAX_OUTPUT_TOKENS,
                        evaluatorMaxOutputTokens));
        this.evaluatorStructuredOutputEnabled =
                evaluatorStructuredOutputEnabled;
        this.evaluatorReasoningEffortEnabled =
                evaluatorReasoningEffortEnabled;
        this.evaluatorReasoningEffort = normalizeReasoningEffort(
                evaluatorReasoningEffortEnabled,
                evaluatorReasoningEffort);
    }

    public OpenAiProperties(String apiKey,
                            String evaluatorModel,
                            String transcriptionModel,
                            String baseUrl,
                            Duration connectTimeout,
                            Duration readTimeout) {
        this(
                apiKey,
                evaluatorModel,
                transcriptionModel,
                baseUrl,
                connectTimeout,
                readTimeout,
                DEFAULT_EVALUATOR_MAX_OUTPUT_TOKENS,
                true,
                false,
                "");
    }

    public String apiKey() {
        return apiKey;
    }

    public String evaluatorModel() {
        return evaluatorModel;
    }

    public String transcriptionModel() {
        return transcriptionModel;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public int evaluatorMaxOutputTokens() {
        return evaluatorMaxOutputTokens;
    }

    public boolean evaluatorStructuredOutputEnabled() {
        return evaluatorStructuredOutputEnabled;
    }

    public boolean evaluatorReasoningEffortEnabled() {
        return evaluatorReasoningEffortEnabled;
    }

    public String evaluatorReasoningEffort() {
        return evaluatorReasoningEffort;
    }

    public boolean hasEvaluatorCredential() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String normalizeReasoningEffort(
            boolean enabled,
            String value) {
        if (!enabled) {
            return "";
        }
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("low", "medium", "high").contains(normalized)) {
            throw new IllegalArgumentException(
                    "openai.evaluator-reasoning-effort must be low, medium, or high when enabled.");
        }
        return normalized;
    }

    private static Duration bounded(
            Duration value,
            Duration defaultValue,
            Duration minimum,
            Duration maximum
    ) {
        Duration candidate = value == null ? defaultValue : value;
        if (candidate.compareTo(minimum) < 0) {
            return minimum;
        }
        if (candidate.compareTo(maximum) > 0) {
            return maximum;
        }
        return candidate;
    }
}
