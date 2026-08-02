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
    public PreparedSpeakingAudio(
            PracticeSpeakingStorageProvider storageProvider,
            String storageKey,
            String mimeType,
            String container,
            String codec,
            long byteSize,
            long durationMs,
            String contentHash) {
        this(storageProvider, null, storageKey, storageKey,
                mimeType, container, codec, byteSize, durationMs, contentHash);
    }

    public ValidatedSpeakingMediaDescriptor toDescriptor() {
        return new ValidatedSpeakingMediaDescriptor(
                storageProvider,
                storageProfileCode == null ? "PRACTICE_SPEAKING" : storageProfileCode,
                storageKey,
                mimeType,
                container,
                codec,
                byteSize,
                durationMs,
                contentHash
        );
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

    public boolean requiresProfilePromotion() {
        return storageProfileCode != null
                && temporaryStorageKey != null
                && temporaryStorageKey.startsWith("learner-speaking/temporary/")
                && temporaryStorageKey.equals(storageKey);
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
