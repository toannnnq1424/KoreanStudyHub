package com.ksh.features.practice.ai.transport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OpenAiPrimaryCapabilityProperties {

    private static final Duration MIN_TIMEOUT = Duration.ofMillis(100);
    private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_READ_TIMEOUT = Duration.ofMinutes(2);

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final CapabilitySlots slots;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final int maxRetries;
    private final int maxResponseBytes;

    public OpenAiPrimaryCapabilityProperties(
            @Value("${app.practice.ai.openai-primary.enabled:false}")
            boolean enabled,
            @Value("${app.practice.ai.openai-primary.base-url:https://api.openai.com/v1}")
            String baseUrl,
            @Value("${app.practice.ai.openai-primary.api-key:}")
            String apiKey,
            @Value("${app.practice.ai.openai-primary.assessment-text-vision-model:}")
            String assessmentTextVisionModel,
            @Value("${app.practice.ai.openai-primary.batch-transcription-model:}")
            String batchTranscriptionModel,
            @Value("${app.practice.ai.openai-primary.realtime-transcription-model:}")
            String realtimeTranscriptionModel,
            @Value("${app.practice.ai.openai-primary.tts-model:}")
            String ttsModel,
            @Value("${app.practice.ai.openai-primary.realtime-speech-model:}")
            String realtimeSpeechModel,
            @Value("${app.practice.ai.openai-primary.connect-timeout:5s}")
            Duration connectTimeout,
            @Value("${app.practice.ai.openai-primary.read-timeout:60s}")
            Duration readTimeout,
            @Value("${app.practice.ai.openai-primary.max-retries:2}")
            int maxRetries,
            @Value("${app.practice.ai.openai-primary.max-response-bytes:2097152}")
            int maxResponseBytes) {
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(textOr(
                baseUrl,
                "https://api.openai.com/v1"));
        this.apiKey = textOr(apiKey, "");
        this.slots = new CapabilitySlots(
                textOr(assessmentTextVisionModel, ""),
                textOr(batchTranscriptionModel, ""),
                textOr(realtimeTranscriptionModel, ""),
                textOr(ttsModel, ""),
                textOr(realtimeSpeechModel, ""));
        this.connectTimeout = bounded(
                connectTimeout,
                Duration.ofSeconds(5),
                MAX_CONNECT_TIMEOUT);
        this.readTimeout = bounded(
                readTimeout,
                Duration.ofSeconds(60),
                MAX_READ_TIMEOUT);
        this.maxRetries = Math.max(0, Math.min(maxRetries, 3));
        this.maxResponseBytes = Math.max(
                16_384,
                Math.min(maxResponseBytes, 8 * 1024 * 1024));
    }

    public boolean enabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public CapabilitySlots slots() {
        return slots;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public int maxResponseBytes() {
        return maxResponseBytes;
    }

    public String modelFor(PracticeAiCapability capability) {
        return switch (capability) {
            case ASSESSMENT_TEXT_VISION -> slots.assessmentTextVisionModel();
            case BATCH_TRANSCRIPTION -> slots.batchTranscriptionModel();
            case REALTIME_TRANSCRIPTION -> slots.realtimeTranscriptionModel();
            case TEXT_TO_SPEECH -> slots.ttsModel();
            case REALTIME_SPEECH -> slots.realtimeSpeechModel();
        };
    }

    public boolean available(PracticeAiCapability capability) {
        return enabled && !apiKey.isBlank() && !modelFor(capability).isBlank();
    }

    private static Duration bounded(
            Duration value,
            Duration fallback,
            Duration maximum) {
        Duration candidate = value == null ? fallback : value;
        if (candidate.compareTo(MIN_TIMEOUT) < 0) {
            return MIN_TIMEOUT;
        }
        return candidate.compareTo(maximum) > 0 ? maximum : candidate;
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record CapabilitySlots(
            String assessmentTextVisionModel,
            String batchTranscriptionModel,
            String realtimeTranscriptionModel,
            String ttsModel,
            String realtimeSpeechModel
    ) {
    }
}
