package com.ksh.features.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_vocabularies")
@Getter
@Setter
public class NewsVocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "target_code", nullable = false, length = 40)
    private String targetCode;

    @Column(name = "korean_word", nullable = false, length = 120)
    private String koreanWord;

    @Column(length = 180)
    private String pronunciation;

    @Column(name = "part_of_speech", length = 80)
    private String partOfSpeech;

    @Column(name = "word_level", length = 80)
    private String wordLevel;

    @Column(name = "meaning_vi", nullable = false, columnDefinition = "TEXT")
    private String meaningVi;

    @Column(name = "dictionary_url", nullable = false, length = 2048)
    private String dictionaryUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
