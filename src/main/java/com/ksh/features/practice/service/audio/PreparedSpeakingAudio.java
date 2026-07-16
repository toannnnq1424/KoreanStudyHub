package com.ksh.features.practice.service.audio;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.ValidatedSpeakingMediaDescriptor;

public record PreparedSpeakingAudio(
        PracticeSpeakingStorageProvider storageProvider,
        String storageKey,
        String mimeType,
        String container,
        String codec,
        long byteSize,
        long durationMs,
        String contentHash
) {
    public ValidatedSpeakingMediaDescriptor toDescriptor() {
        return new ValidatedSpeakingMediaDescriptor(
                storageProvider,
                storageKey,
                mimeType,
                container,
                codec,
                byteSize,
                durationMs,
                contentHash
        );
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
