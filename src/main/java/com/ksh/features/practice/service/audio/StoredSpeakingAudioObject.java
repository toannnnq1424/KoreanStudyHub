package com.ksh.features.practice.service.audio;

import java.nio.file.Path;
import com.ksh.entities.PracticeSpeakingStorageProvider;

public final class StoredSpeakingAudioObject {
    private final String storageKey;
    private final long byteSize;
    private final String sha256;
    private final Path privatePath;
    private final String storageProfileCode;
    private final PracticeSpeakingStorageProvider storageProvider;

    public StoredSpeakingAudioObject(String storageKey, long byteSize, String sha256, Path privatePath) {
        this(storageKey, byteSize, sha256, privatePath, null,
                PracticeSpeakingStorageProvider.LOCAL);
    }

    public StoredSpeakingAudioObject(String storageKey, long byteSize, String sha256,
                                     Path privatePath, String storageProfileCode,
                                     PracticeSpeakingStorageProvider storageProvider) {
        this.storageKey = storageKey;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.privatePath = privatePath;
        this.storageProfileCode = storageProfileCode;
        this.storageProvider = storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getSha256() {
        return sha256;
    }

    Path getPrivatePath() {
        return privatePath;
    }

    void discardInspectionCopy() {
        if (storageProfileCode == null || privatePath == null) return;
        try {
            java.nio.file.Files.deleteIfExists(privatePath);
        } catch (java.io.IOException ignored) {
            // OS-temporary inspection copies have no product retention value.
        }
    }

    public String getStorageProfileCode() { return storageProfileCode; }
    public PracticeSpeakingStorageProvider getStorageProvider() { return storageProvider; }

    @Override
    public String toString() {
        return "StoredSpeakingAudioObject{byteSize=" + byteSize + "}";
    }
}
