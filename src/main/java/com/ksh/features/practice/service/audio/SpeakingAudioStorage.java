package com.ksh.features.practice.service.audio;

import java.io.InputStream;

public interface SpeakingAudioStorage {
    StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredContentLength);

    String promoteTemporary(String temporaryKey);

    default String promoteTemporary(String storageProfileCode, String temporaryKey) {
        if (storageProfileCode != null) {
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.STORAGE_FAILURE,
                    "Storage profile identity is invalid");
        }
        return promoteTemporary(temporaryKey);
    }

    InputStream open(String storageKey);

    default InputStream open(String storageProfileCode, String storageKey) {
        if (storageProfileCode != null) {
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.STORAGE_FAILURE,
                    "Storage profile identity is invalid");
        }
        return open(storageKey);
    }

    boolean exists(String storageKey);

    default boolean exists(String storageProfileCode, String storageKey) {
        return storageProfileCode == null && exists(storageKey);
    }

    void delete(String storageKey);

    default void delete(String storageProfileCode, String storageKey) {
        if (storageProfileCode != null) {
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.STORAGE_FAILURE,
                    "Storage profile identity is invalid");
        }
        delete(storageKey);
    }
}
