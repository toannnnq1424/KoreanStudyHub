package com.ksh.features.discovery.service;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.ingestion.NewsUrlSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;

@Service
@Transactional(readOnly = true)
public class NewsBlacklistService {

    private final NewsArticleRepository articleRepository;

    public NewsBlacklistService(NewsArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public boolean isBlacklisted(String canonicalUrl) {
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            return false;
        }
        return articleRepository.existsByCanonicalUrlHashAndStatus(
                NewsUrlSupport.sha256(NewsUrlSupport.canonicalize(canonicalUrl)),
                NewsArticleStatus.BLACKLISTED
        );
    }

    public boolean isBlacklistedHash(String canonicalUrlHash) {
        return canonicalUrlHash != null
                && articleRepository.existsByCanonicalUrlHashAndStatus(
                        canonicalUrlHash,
                        NewsArticleStatus.BLACKLISTED
                );
    }

    @Transactional
    public int blacklistArticles(Collection<NewsArticle> articles, String reason) {
        int created = 0;
        if (articles == null || articles.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (NewsArticle article : articles) {
            if (article == null || article.getCanonicalUrlHash() == null || article.getCanonicalUrlHash().isBlank()) {
                continue;
            }
            if (article.getStatus() == NewsArticleStatus.BLACKLISTED) {
                continue;
            }
            article.setStatus(NewsArticleStatus.BLACKLISTED);
            article.setBlacklistReason(normalizeReason(reason));
            article.setBlacklistedAt(now);
            article.setSourceExcerpt(null);
            article.setSourceBodyHtml(null);
            article.setSourceBodyText(null);
            article.setSourceAttachments(java.util.List.of());
            article.setImageUrl(null);
            article.setSourceAuthor(null);
            article.setSourceViewCount(null);
            article.setSourceContentFetchedAt(null);
            article.setUpdatedAt(now);
            articleRepository.save(article);
            created++;
        }
        articleRepository.flush();
        return created;
    }

    public long count() {
        return articleRepository.countByStatus(NewsArticleStatus.BLACKLISTED);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }
}
