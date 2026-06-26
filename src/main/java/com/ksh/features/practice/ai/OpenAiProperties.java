package com.ksh.features.practice.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProperties {

    private final String apiKey;
    private final String evaluatorModel;
    private final String transcriptionModel;
    private final String baseUrl;

    public OpenAiProperties(@Value("${openai.api-key:}") String apiKey,
                            @Value("${openai.evaluator-model:models/gemini-2.5-flash-lite}") String evaluatorModel,
                            @Value("${openai.transcription-model:gpt-4o-transcribe}") String transcriptionModel,
                            @Value("${openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl) {
        this.apiKey = apiKey;
        this.evaluatorModel = evaluatorModel;
        this.transcriptionModel = transcriptionModel;
        this.baseUrl = baseUrl;
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
}
