package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingMediaStatus;

record SpeakingMediaDeletionResult(
        Long mediaId,
        PracticeSpeakingMediaStatus status,
        Long cleanupTaskId
) {
    @Override
    public String toString() {
        return "SpeakingMediaDeletionResult{mediaId=" + mediaId
                + ", status=" + status
                + ", cleanupTaskId=" + cleanupTaskId
                + '}';
    }
}
