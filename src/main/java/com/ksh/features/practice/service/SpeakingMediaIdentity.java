package com.ksh.features.practice.service;

public record SpeakingMediaIdentity(
        Long mediaId,
        Long lockVersion,
        String contentHash,
        Long byteSize,
        Long attemptId,
        Long questionId
) {}
