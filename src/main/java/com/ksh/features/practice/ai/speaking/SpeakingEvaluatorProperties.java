package com.ksh.features.practice.ai.speaking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class SpeakingEvaluatorProperties {
    private static final Duration DEFAULT_TIMEOUT =
            Duration.ofSeconds(30);
    private static final Duration MIN_TIMEOUT =
            Duration.ofSeconds(1);
    private static final Duration MAX_TIMEOUT =
            Duration.ofMinutes(2);
    private static final int MAX_RETRIES = 3;

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
            @Value("${app.practice.speaking-evaluator.provider:openai-primary}") String provider,
            @Value("${app.practice.speaking-evaluator.base-url:}") String baseUrl,
            @Value("${app.practice.speaking-evaluator.api-key:}") String apiKey,
            @Value("${app.practice.speaking-evaluator.model:}") String model,
            @Value("${app.practice.speaking-evaluator.timeout:30s}") Duration timeout,
            @Value("${app.practice.speaking-evaluator.max-retries:2}") int maxRetries,
            @Value("${app.practice.speaking-evaluator.prompt-version:}") String promptVersion,
            @Value("${app.practice.speaking-evaluator.rubric-version:}") String rubricVersion,
            @Value("${app.practice.speaking-evaluator.schema-version:}") String schemaVersion
    ) {
        this.enabled = enabled;
        this.provider = text(provider, "openai-primary").toLowerCase(Locale.ROOT);
        this.baseUrl = trimTrailingSlash(text(baseUrl, "https://api.openai.com/v1"));
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = text(model, "");
        this.timeout = boundedTimeout(timeout);
        this.maxRetries = Math.min(
                MAX_RETRIES, Math.max(0, maxRetries));
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

    private static Duration boundedTimeout(Duration value) {
        Duration candidate = value == null
                ? DEFAULT_TIMEOUT
                : value;
        if (candidate.isZero() || candidate.isNegative()) {
            throw new IllegalArgumentException(
                    "timeout must be positive");
        }
        if (candidate.compareTo(MIN_TIMEOUT) < 0) {
            return MIN_TIMEOUT;
        }
        return candidate.compareTo(MAX_TIMEOUT) > 0
                ? MAX_TIMEOUT
                : candidate;
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
