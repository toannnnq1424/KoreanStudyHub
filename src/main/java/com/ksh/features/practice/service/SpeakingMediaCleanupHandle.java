package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingStorageProvider;

record SpeakingMediaCleanupHandle(
        Long mediaId,
        PracticeSpeakingStorageProvider storageProvider,
        String storageKey
) {
    @Override
    public String toString() {
        return "SpeakingMediaCleanupHandle{mediaId=" + mediaId
                + ", storageProvider=" + storageProvider
                + '}';
    }
}
