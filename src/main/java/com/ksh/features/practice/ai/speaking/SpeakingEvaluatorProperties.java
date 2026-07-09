package com.ksh.features.practice.ai.speaking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class SpeakingEvaluatorProperties {
    private final boolean enabled;
    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;
    private final String promptVersion;
    private final String rubricVersion;
    private final String schemaVersion;

    public SpeakingEvaluatorProperties(
            @Value("${app.practice.speaking-evaluator.enabled:false}") boolean enabled,
            @Value("${app.practice.speaking-evaluator.provider:openai-compatible}") String provider,
            @Value("${app.practice.speaking-evaluator.base-url:${openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai}}") String baseUrl,
            @Value("${app.practice.speaking-evaluator.api-key:${openai.api-key:}}") String apiKey,
            @Value("${app.practice.speaking-evaluator.model:${openai.evaluator-model:models/gemini-2.5-flash}}") String model,
            @Value("${app.practice.speaking-evaluator.timeout:30s}") Duration timeout,
            @Value("${app.practice.speaking-evaluator.max-retries:2}") int maxRetries,
            @Value("${app.practice.speaking-evaluator.prompt-version:speaking-eval-v1}") String promptVersion,
            @Value("${app.practice.speaking-evaluator.rubric-version:speaking-rubric-v1}") String rubricVersion,
            @Value("${app.practice.speaking-evaluator.schema-version:speaking-schema-v1}") String schemaVersion
    ) {
        this.enabled = enabled;
        this.provider = text(provider, "openai-compatible").toLowerCase(Locale.ROOT);
        this.baseUrl = trimTrailingSlash(text(baseUrl, "https://generativelanguage.googleapis.com/v1beta/openai"));
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = text(model, "models/gemini-2.5-flash");
        this.timeout = requirePositive(timeout, "timeout");
        this.maxRetries = Math.max(0, maxRetries);
        this.promptVersion = text(promptVersion, SpeakingEvaluationNormalizer.PROMPT_VERSION);
        this.rubricVersion = text(rubricVersion, SpeakingEvaluationNormalizer.RUBRIC_VERSION);
        this.schemaVersion = text(schemaVersion, SpeakingEvaluationNormalizer.SCHEMA_VERSION);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    public boolean enabled() { return enabled; }
    public String provider() { return provider; }
    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String model() { return model; }
    public Duration timeout() { return timeout; }
    public int maxRetries() { return maxRetries; }
    public String promptVersion() { return promptVersion; }
    public String rubricVersion() { return rubricVersion; }
    public String schemaVersion() { return schemaVersion; }

    @Override
    public String toString() {
        return "SpeakingEvaluatorProperties{"
                + "enabled=" + enabled
                + ", provider='" + provider + '\''
                + ", baseUrl='" + baseUrl + '\''
                + ", model='" + model + '\''
                + ", timeout=" + timeout
                + ", maxRetries=" + maxRetries
                + ", promptVersion='" + promptVersion + '\''
                + ", rubricVersion='" + rubricVersion + '\''
                + ", schemaVersion='" + schemaVersion + '\''
                + ", apiKeyPresent=" + !apiKey.isBlank()
                + '}';
    }
}
