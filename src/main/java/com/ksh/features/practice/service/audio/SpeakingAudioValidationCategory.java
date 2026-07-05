package com.ksh.features.practice.service.audio;

public enum SpeakingAudioValidationCategory {
    EMPTY,
    TOO_LARGE,
    TOO_LONG,
    UNSUPPORTED_TYPE,
    INVALID_CONTAINER,
    UNSUPPORTED_CODEC,
    MULTIPLE_AUDIO_STREAMS,
    NON_AUDIO_STREAM_PRESENT,
    CORRUPT_MEDIA,
    PROBE_UNAVAILABLE,
    PROBE_TIMEOUT,
    PROBE_OUTPUT_TOO_LARGE,
    STORAGE_FAILURE
}
