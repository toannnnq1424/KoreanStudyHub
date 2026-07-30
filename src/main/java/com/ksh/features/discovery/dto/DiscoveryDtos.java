package com.ksh.features.discovery.dto;

import com.ksh.features.discovery.entity.NewsCategory;

import java.time.LocalDateTime;
import java.util.List;

public final class DiscoveryDtos {

    private DiscoveryDtos() {
    }

    public record CategoryOption(
            String slug,
            String label,
            boolean active
    ) {
    }

    public record StoryCard(
            Long id,
            String slug,
            String title,
            String originalTitle,
            boolean koreanOriginal,
            String excerpt,
            NewsCategory category,
            String categoryLabel,
            String categorySlug,
            String glyph,
            String sourceName,
            String imageUrl,
            LocalDateTime publishedAt,
            String displayDate,
            boolean vietnamRelated,
            int readMinutes
    ) {
    }

    public record VocabularyCard(
            String word,
            String pronunciation,
            String meaningVi,
            String partOfSpeech,
            String dictionaryUrl
    ) {
    }

    public record AttachmentCard(
            String name,
            String sourceUrl,
            String mediaType,
            String sizeLabel
    ) {
    }

    public record DiscoveryPage(
            String selectedLanguage,
            String selectedCategory,
            String query,
            List<CategoryOption> categories,
            StoryCard hero,
            List<StoryCard> digest,
            List<StoryCard> featured,
            List<StoryCard> latest,
            StoryCard scholarship,
            List<VocabularyCard> quickVocabulary,
            boolean empty,
            int currentPage,
            int totalPages,
            long totalElements,
            boolean hasPrevious,
            boolean hasNext,
            List<Integer> pageNumbers
    ) {
    }

    public record ArticleDetail(
            Long id,
            String slug,
            String title,
            String originalTitle,
            boolean koreanOriginal,
            String excerpt,
            NewsCategory category,
            String categoryLabel,
            String categorySlug,
            String glyph,
            String sourceName,
            String imageUrl,
            String canonicalUrl,
            String sourceBodyHtml,
            String editorialBody,
            String sourceLayout,
            String sourceAuthor,
            Long sourceViewCount,
            String languageLabel,
            LocalDateTime publishedAt,
            String displayDate,
            boolean vietnamRelated,
            boolean containsKorean,
            int readMinutes,
            List<AttachmentCard> attachments,
            List<VocabularyCard> vocabulary,
            List<StoryCard> related
    ) {
    }
}
