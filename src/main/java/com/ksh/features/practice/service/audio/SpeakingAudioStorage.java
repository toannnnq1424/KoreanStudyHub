package com.ksh.features.practice.service.audio;

import java.io.InputStream;

public interface SpeakingAudioStorage {
    StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredContentLength);

    String promoteTemporary(String storageProfileCode, String temporaryKey);

    InputStream open(String storageProfileCode, String storageKey);

    boolean exists(String storageProfileCode, String storageKey);

    void delete(String storageProfileCode, String storageKey);
}
