package com.ksh.features.practice.ai.speaking.transcription;

public enum SpeakingTranscriptionErrorCategory {
    MISSING_API_KEY,
    PROVIDER_HTTP_ERROR,
    PROVIDER_TRANSPORT_ERROR,
    PROVIDER_MALFORMED_JSON,
    PROVIDER_EMPTY_TRANSCRIPT,
    UNSUPPORTED_MEDIA,
    AUDIO_MISSING,
    AUDIO_UNAVAILABLE
}
