package com.ksh.features.practice.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OpenAiProperties {

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

    public OpenAiProperties(@Value("${openai.api-key:}") String apiKey,
                            @Value("${openai.evaluator-model:models/gemini-2.5-flash-lite}") String evaluatorModel,
                            @Value("${openai.transcription-model:gpt-4o-transcribe}") String transcriptionModel,
                            @Value("${openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl,
                            @Value("${openai.connect-timeout:5s}") Duration connectTimeout,
                            @Value("${openai.read-timeout:60s}") Duration readTimeout) {
        this.apiKey = apiKey;
        this.evaluatorModel = evaluatorModel;
        this.transcriptionModel = transcriptionModel;
        this.baseUrl = baseUrl;
        this.connectTimeout = bounded(
                connectTimeout, DEFAULT_CONNECT_TIMEOUT, MIN_CONNECT_TIMEOUT, MAX_CONNECT_TIMEOUT);
        this.readTimeout = bounded(
                readTimeout, DEFAULT_READ_TIMEOUT, MIN_READ_TIMEOUT, MAX_READ_TIMEOUT);
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
