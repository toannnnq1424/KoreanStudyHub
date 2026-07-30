package com.ksh.features.discovery.entity;

/**
 * Inline attachment metadata persisted as JSON in news_articles.
 */
public record NewsArticleAttachment(
        String displayName,
        String sourceUrl,
        String mediaType,
        Long sizeBytes,
        int displayOrder
) {
}
