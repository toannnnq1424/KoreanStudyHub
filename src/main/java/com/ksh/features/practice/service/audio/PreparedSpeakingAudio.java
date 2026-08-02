package com.ksh.features.practice.service.audio;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.ValidatedSpeakingMediaDescriptor;

public record PreparedSpeakingAudio(
        PracticeSpeakingStorageProvider storageProvider,
        String storageProfileCode,
        String temporaryStorageKey,
        String storageKey,
        String mimeType,
        String container,
        String codec,
        long byteSize,
        long durationMs,
        String contentHash
) {
    public PreparedSpeakingAudio {
        if (!"PRACTICE_SPEAKING".equals(storageProfileCode)) {
            throw new IllegalArgumentException("storageProfileCode is invalid.");
        }
    }

    public ValidatedSpeakingMediaDescriptor temporaryDescriptor() {
        return new ValidatedSpeakingMediaDescriptor(
                storageProvider, storageProfileCode, temporaryStorageKey,
                mimeType, container, codec, byteSize, durationMs, contentHash);
    }

    public ValidatedSpeakingMediaDescriptor readyDescriptor(String readyStorageKey) {
        return new ValidatedSpeakingMediaDescriptor(
                storageProvider, storageProfileCode, readyStorageKey,
                mimeType, container, codec, byteSize, durationMs, contentHash);
    }

    @Override
    public String toString() {
        return "PreparedSpeakingAudio{storageProvider=" + storageProvider
                + ", mimeType='" + mimeType + '\''
                + ", container='" + container + '\''
                + ", codec='" + codec + '\''
                + ", byteSize=" + byteSize
                + ", durationMs=" + durationMs
                + '}';
    }
}
