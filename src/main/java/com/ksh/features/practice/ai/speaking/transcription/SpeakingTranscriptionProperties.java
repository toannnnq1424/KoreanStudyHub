package com.ksh.features.practice.ai.speaking.transcription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpeakingTranscriptionProperties {
    private final boolean enabled;
    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String language;
    private final long maxBytes;
    private final Duration timeout;
    private final int maxRetries;
    private final boolean includeLogprobs;
    private final Set<String> allowedMimeTypes;

    public SpeakingTranscriptionProperties(
            @Value("${app.practice.speaking-transcription.enabled:false}") boolean enabled,
            @Value("${app.practice.speaking-transcription.provider:openai}") String provider,
            @Value("${app.practice.speaking-transcription.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.practice.speaking-transcription.api-key:${OPENAI_TRANSCRIPTION_API_KEY:}}") String apiKey,
            @Value("${app.practice.speaking-transcription.model:gpt-4o-mini-transcribe}") String model,
            @Value("${app.practice.speaking-transcription.language:ko}") String language,
            @Value("${app.practice.speaking-transcription.max-bytes:26214400}") long maxBytes,
            @Value("${app.practice.speaking-transcription.timeout:30s}") Duration timeout,
            @Value("${app.practice.speaking-transcription.max-retries:2}") int maxRetries,
            @Value("${app.practice.speaking-transcription.include-logprobs:true}") boolean includeLogprobs,
            @Value("${app.practice.speaking-transcription.allowed-mime-types:audio/webm,audio/mp4}") String allowedMimeTypes
    ) {
        this.enabled = enabled;
        this.provider = text(provider, "openai").toLowerCase(Locale.ROOT);
        this.baseUrl = trimTrailingSlash(text(baseUrl, "https://api.openai.com/v1"));
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = text(model, "gpt-4o-mini-transcribe");
        this.language = text(language, "ko").toLowerCase(Locale.ROOT);
        this.maxBytes = requirePositive(maxBytes, "maxBytes");
        this.timeout = requirePositive(timeout, "timeout");
        this.maxRetries = Math.max(0, maxRetries);
        this.includeLogprobs = includeLogprobs;
        this.allowedMimeTypes = parseMimeTypes(allowedMimeTypes);
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

    private static long requirePositive(long value, String label) {
        if (value <= 0L) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static Set<String> parseMimeTypes(String value) {
        Set<String> parsed = Arrays.stream(text(value, "audio/webm,audio/mp4").split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return parsed.isEmpty() ? Set.of("audio/webm", "audio/mp4") : parsed;
    }

    public boolean enabled() { return enabled; }
    public String provider() { return provider; }
    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String model() { return model; }
    public String language() { return language; }
    public long maxBytes() { return maxBytes; }
    public Duration timeout() { return timeout; }
    public int maxRetries() { return maxRetries; }
    public boolean includeLogprobs() { return includeLogprobs; }
    public Set<String> allowedMimeTypes() { return allowedMimeTypes; }

    @Override
    public String toString() {
        return "SpeakingTranscriptionProperties{"
                + "enabled=" + enabled
                + ", provider='" + provider + '\''
                + ", baseUrl='" + baseUrl + '\''
                + ", model='" + model + '\''
                + ", language='" + language + '\''
                + ", maxBytes=" + maxBytes
                + ", timeout=" + timeout
                + ", maxRetries=" + maxRetries
                + ", includeLogprobs=" + includeLogprobs
                + ", allowedMimeTypes=" + allowedMimeTypes
                + ", apiKeyPresent=" + !apiKey.isBlank()
                + '}';
    }
}
