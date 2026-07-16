package com.ksh.features.practice.service;

public final class PracticeSpeakingMediaPlaybackNotFoundException extends RuntimeException {
    public PracticeSpeakingMediaPlaybackNotFoundException() {
        super("Speaking media playback is unavailable.");
    }
}
