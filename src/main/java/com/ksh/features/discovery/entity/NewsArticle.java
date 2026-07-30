package com.ksh.features.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "news_articles")
@Getter
@Setter
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "ingestion_run_id")
    private Long ingestionRunId;

    @Column(name = "source_name", nullable = false, length = 180)
    private String sourceName;

    @Column(name = "external_id", length = 180)
    private String externalId;

    @Column(name = "canonical_url", nullable = false, length = 2048)
    private String canonicalUrl;

    @Column(
            name = "canonical_url_hash",
            nullable = false,
            unique = true,
            length = 64,
            columnDefinition = "CHAR(64)"
    )
    private String canonicalUrlHash;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "original_title", nullable = false, length = 700)
    private String originalTitle;

    @Column(name = "display_title", nullable = false, length = 700)
    private String displayTitle;

    @Column(name = "source_excerpt", columnDefinition = "TEXT")
    private String sourceExcerpt;

    @Column(name = "ai_editorial_title", length = 700)
    private String aiEditorialTitle;

    @Column(name = "ai_editorial_excerpt", columnDefinition = "TEXT")
    private String aiEditorialExcerpt;

    @Column(name = "ai_editorial_body", columnDefinition = "MEDIUMTEXT")
    private String aiEditorialBody;

    @Column(name = "ai_generated_at")
    private LocalDateTime aiGeneratedAt;

    @Column(name = "ai_generation_run_id")
    private Long aiGenerationRunId;

    @Column(name = "ai_generation_error", length = 1000)
    private String aiGenerationError;

    @Column(name = "source_body_html", columnDefinition = "MEDIUMTEXT")
    private String sourceBodyHtml;

    @Column(name = "source_body_text", columnDefinition = "MEDIUMTEXT")
    private String sourceBodyText;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_layout", length = 40)
    private NewsSourceLayout sourceLayout;

    @Column(name = "source_author", length = 180)
    private String sourceAuthor;

    @Column(name = "source_view_count")
    private Long sourceViewCount;

    @Column(name = "source_content_fetched_at")
    private LocalDateTime sourceContentFetchedAt;

    @Convert(converter = NewsArticleAttachmentsConverter.class)
    @Column(name = "source_attachments_json", columnDefinition = "MEDIUMTEXT")
    private List<NewsArticleAttachment> sourceAttachments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NewsCategory category;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsArticleStatus status;

    @Column(name = "blacklist_reason", length = 300)
    private String blacklistReason;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    @Column(name = "political_risk", nullable = false)
    private boolean politicalRisk;

    @Column(name = "vietnam_relevance", nullable = false)
    private int vietnamRelevance;

    @Column(name = "rank_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal rankScore;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
