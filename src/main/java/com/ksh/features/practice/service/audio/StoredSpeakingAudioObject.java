package com.ksh.features.practice.service.audio;

import java.nio.file.Path;

public final class StoredSpeakingAudioObject {
    private final String storageKey;
    private final long byteSize;
    private final String sha256;
    private final Path privatePath;

    public StoredSpeakingAudioObject(String storageKey, long byteSize, String sha256, Path privatePath) {
        this.storageKey = storageKey;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.privatePath = privatePath;
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

    @Override
    public String toString() {
        return "StoredSpeakingAudioObject{byteSize=" + byteSize + "}";
    }
}
