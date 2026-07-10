package com.ksh.features.practice.service;

public record PracticeAttemptVersionLock(
        Long publishedVersionId,
        Long setVersionId,
        Long testVersionId,
        Long sectionVersionId
) {
}
