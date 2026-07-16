package com.ksh.features.practice.ai.readinglistening;

public record PublishedVersionExplanationEvent(Long publishedVersionId) {
    public PublishedVersionExplanationEvent {
        if (publishedVersionId == null) {
            throw new IllegalArgumentException("publishedVersionId is required");
        }
    }
}
