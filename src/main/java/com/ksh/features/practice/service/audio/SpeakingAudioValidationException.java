package com.ksh.features.practice.service.audio;

public class SpeakingAudioValidationException extends RuntimeException {
    private final SpeakingAudioValidationCategory category;

    public SpeakingAudioValidationException(SpeakingAudioValidationCategory category, String message) {
        super(message);
        this.category = category;
    }

    public SpeakingAudioValidationException(SpeakingAudioValidationCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public SpeakingAudioValidationCategory getCategory() {
        return category;
    }
}
