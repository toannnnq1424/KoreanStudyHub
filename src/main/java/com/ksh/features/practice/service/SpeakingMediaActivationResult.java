package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingMediaStatus;

import java.util.Optional;

record SpeakingMediaActivationResult(
        Long mediaId,
        Long questionId,
        PracticeSpeakingMediaStatus status,
        Long byteSize,
        Long durationMs,
        String mimeType,
        Long lockVersion,
        Optional<Long> supersededCleanupTaskId
) {
    SpeakingMediaActivationResult {
        supersededCleanupTaskId = supersededCleanupTaskId == null ? Optional.empty() : supersededCleanupTaskId;
    }

    @Override
    public String toString() {
        return "SpeakingMediaActivationResult{mediaId=" + mediaId
                + ", questionId=" + questionId
                + ", status=" + status
                + ", byteSize=" + byteSize
                + ", durationMs=" + durationMs
                + ", mimeType='" + mimeType + '\''
                + ", lockVersion=" + lockVersion
                + ", supersededCleanupTask=" + supersededCleanupTaskId.isPresent()
                + '}';
    }
}
