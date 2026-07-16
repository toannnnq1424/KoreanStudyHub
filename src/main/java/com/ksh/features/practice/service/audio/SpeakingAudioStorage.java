package com.ksh.features.practice.service.audio;

import java.io.InputStream;

public interface SpeakingAudioStorage {
    StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredContentLength);

    String promoteTemporary(String temporaryKey);

    InputStream open(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);
}
