package com.ksh.features.practice.ai.readinglistening;

import java.util.Map;

public record PublishedVersionExplanationEvent(
        Long publishedVersionId,
        Long draftId,
        Map<String, Long> questionVersionIdsByClient
) {
    public PublishedVersionExplanationEvent {
        if (publishedVersionId == null) {
            throw new IllegalArgumentException("publishedVersionId is required");
        }
        questionVersionIdsByClient = questionVersionIdsByClient == null
                ? Map.of()
                : Map.copyOf(questionVersionIdsByClient);
    }

    public PublishedVersionExplanationEvent(Long publishedVersionId) {
        this(publishedVersionId, null, Map.of());
    }
}
