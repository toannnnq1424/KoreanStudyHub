package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingMediaStatus;

import java.util.Optional;

record SpeakingMediaDeletionResult(
        Long mediaId,
        PracticeSpeakingMediaStatus status,
        Optional<SpeakingMediaCleanupHandle> cleanup
) {
    SpeakingMediaDeletionResult {
        cleanup = cleanup == null ? Optional.empty() : cleanup;
    }

    @Override
    public String toString() {
        return "SpeakingMediaDeletionResult{mediaId=" + mediaId
                + ", status=" + status
                + ", cleanup=" + cleanup.isPresent()
                + '}';
    }
}
