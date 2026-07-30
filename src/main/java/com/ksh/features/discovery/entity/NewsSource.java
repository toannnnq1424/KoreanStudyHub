package com.ksh.features.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_sources")
@Getter
@Setter
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private NewsSourceType sourceType;

    @Column(name = "feed_url", nullable = false, length = 2048)
    private String feedUrl;

    @Column(name = "site_url", nullable = false, length = 2048)
    private String siteUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_category", nullable = false, length = 30)
    private NewsCategory defaultCategory;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "priority_weight", nullable = false)
    private int priorityWeight;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "crawl_cursor", nullable = false)
    private int crawlCursor = 2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
