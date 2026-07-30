package com.ksh.features.discovery.repository;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.entity.NewsCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByCanonicalUrlHash(String canonicalUrlHash);

    boolean existsByCanonicalUrlHashAndStatus(
            String canonicalUrlHash,
            NewsArticleStatus status
    );

    Optional<NewsArticle> findByCanonicalUrlHash(String canonicalUrlHash);

    Optional<NewsArticle> findBySlugAndStatus(String slug, NewsArticleStatus status);

    Optional<NewsArticle> findByIdAndStatus(Long id, NewsArticleStatus status);

    Page<NewsArticle> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

    @Query("""
            SELECT article
            FROM NewsArticle article
            WHERE article.status = com.ksh.features.discovery.entity.NewsArticleStatus.PUBLISHED
              AND (:category IS NULL OR article.category = :category)
              AND (
                    :query IS NULL
                    OR LOWER(article.displayTitle) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(article.sourceExcerpt, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(article.aiEditorialTitle, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(article.aiEditorialExcerpt, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            ORDER BY article.rankScore DESC, article.publishedAt DESC, article.id DESC
            """)
    Page<NewsArticle> findFeed(
            @Param("category") NewsCategory category,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            SELECT article
            FROM NewsArticle article
            WHERE article.status = com.ksh.features.discovery.entity.NewsArticleStatus.PUBLISHED
              AND article.vietnamRelevance > 0
              AND (
                    :query IS NULL
                    OR LOWER(article.displayTitle) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(article.sourceExcerpt, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(article.aiEditorialTitle, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(article.aiEditorialExcerpt, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            ORDER BY article.rankScore DESC, article.publishedAt DESC, article.id DESC
            """)
    Page<NewsArticle> findVietnamFeed(
            @Param("query") String query,
            Pageable pageable
    );

    List<NewsArticle> findByStatusAndCategoryAndIdNotOrderByRankScoreDescPublishedAtDesc(
            NewsArticleStatus status,
            NewsCategory category,
            Long id,
            Pageable pageable
    );

    List<NewsArticle> findBySourceIdAndStatusOrderByPublishedAtDescIdDesc(
            Long sourceId,
            NewsArticleStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT article
            FROM NewsArticle article
            WHERE article.status = com.ksh.features.discovery.entity.NewsArticleStatus.PUBLISHED
              AND NOT EXISTS (
                    SELECT vocabulary.id
                    FROM NewsVocabulary vocabulary
                    WHERE vocabulary.articleId = article.id
                  )
            ORDER BY article.rankScore DESC, article.publishedAt DESC, article.id DESC
            """)
    List<NewsArticle> findVocabularyCandidates(Pageable pageable);

    @Query("""
            SELECT article
            FROM NewsArticle article
            WHERE article.status = com.ksh.features.discovery.entity.NewsArticleStatus.PUBLISHED
              AND article.aiGeneratedAt IS NULL
              AND article.sourceBodyText IS NOT NULL
              AND LENGTH(article.sourceBodyText) > 80
            ORDER BY article.rankScore DESC, article.publishedAt DESC, article.id DESC
            """)
    List<NewsArticle> findAiEditorialCandidates(Pageable pageable);

    long countByStatus(NewsArticleStatus status);

    long countBySourceBodyHtmlIsNotNull();

    long countByImageUrlIsNotNull();
}
