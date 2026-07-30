package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsCategory;

import java.time.LocalDateTime;

public record NewsCandidate(
        String externalId,
        String title,
        String excerpt,
        String canonicalUrl,
        String imageUrl,
        String languageCode,
        NewsCategory category,
        LocalDateTime publishedAt,
        LocalDateTime deadlineAt,
        NewsSourceContent sourceContent
) {
    public NewsCandidate(
            String externalId,
            String title,
            String excerpt,
            String canonicalUrl,
            String imageUrl,
            String languageCode,
            NewsCategory category,
            LocalDateTime publishedAt,
            LocalDateTime deadlineAt
    ) {
        this(
                externalId,
                title,
                excerpt,
                canonicalUrl,
                imageUrl,
                languageCode,
                category,
                publishedAt,
                deadlineAt,
                null
        );
    }

    public NewsCandidate withSourceContent(
            NewsSourceContent content,
            String enrichedImageUrl
    ) {
        return new NewsCandidate(
                externalId,
                title,
                excerpt,
                canonicalUrl,
                enrichedImageUrl == null ? imageUrl : enrichedImageUrl,
                languageCode,
                category,
                publishedAt,
                deadlineAt,
                content
        );
    }
}
