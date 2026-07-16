package com.ksh.features.practice.service.audio;

public record SpeakingAudioInspection(
        String container,
        String codec,
        String canonicalMimeType,
        long durationMs
) {
}
