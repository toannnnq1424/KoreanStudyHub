package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleAttachment;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.service.NewsBlacklistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class NewsArticleWriter {

    private final NewsArticleRepository articleRepository;
    private final NewsBlacklistService blacklistService;
    private final NewsPolicy policy;
    private final NewsRankingPolicy rankingPolicy;

    public NewsArticleWriter(
            NewsArticleRepository articleRepository,
            NewsBlacklistService blacklistService,
            NewsPolicy policy,
            NewsRankingPolicy rankingPolicy
    ) {
        this.articleRepository = articleRepository;
        this.blacklistService = blacklistService;
        this.policy = policy;
        this.rankingPolicy = rankingPolicy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PersistResult persist(NewsSource source, NewsCandidate candidate) {
        String canonicalUrl = NewsUrlSupport.canonicalize(candidate.canonicalUrl());
        String urlHash = NewsUrlSupport.sha256(canonicalUrl);
        if (blacklistService.isBlacklistedHash(urlHash)) {
            return PersistResult.blacklisted();
        }
        NewsPolicy.Evaluation evaluation = policy.evaluate(candidate);
        Optional<NewsArticle> existing = articleRepository.findByCanonicalUrlHash(urlHash);
        if (existing.isPresent()) {
            refreshExisting(source, existing.get(), candidate, evaluation);
            return PersistResult.duplicate();
        }

        LocalDateTime now = LocalDateTime.now();
        NewsArticle article = new NewsArticle();
        article.setSourceId(source.getId());
        article.setSourceName(source.getName());
        article.setExternalId(trimTo(candidate.externalId(), 180));
        article.setCanonicalUrl(canonicalUrl);
        article.setCanonicalUrlHash(urlHash);
        article.setSlug(NewsSlugSupport.from(candidate.title(), urlHash));
        article.setOriginalTitle(trimTo(candidate.title(), 700));
        article.setDisplayTitle(trimTo(candidate.title(), 700));
        article.setSourceExcerpt(NewsTextSupport.plainText(candidate.excerpt(), 480));
        applySourceContent(article, candidate.sourceContent(), now);
        article.setCategory(candidate.category());
        article.setLanguageCode(trimTo(candidate.languageCode(), 10));
        article.setImageUrl(trimTo(candidate.imageUrl(), 2048));
        article.setStatus(evaluation.status());
        article.setPoliticalRisk(evaluation.politicalRisk());
        article.setVietnamRelevance(evaluation.vietnamRelevance());
        article.setRankScore(rankingPolicy.score(source, candidate, evaluation));
        article.setPublishedAt(candidate.publishedAt());
        article.setDeadlineAt(candidate.deadlineAt());
        article.setIngestedAt(now);
        article.setUpdatedAt(now);

        replaceAttachments(article, candidate.sourceContent());
        NewsArticle saved = articleRepository.saveAndFlush(article);
        return evaluation.status() == NewsArticleStatus.PUBLISHED
                ? PersistResult.published(saved)
                : PersistResult.rejected(saved);
    }

    @Transactional(readOnly = true)
    public boolean requiresContentFetch(NewsCandidate candidate) {
        String canonicalUrl = NewsUrlSupport.canonicalize(candidate.canonicalUrl());
        String urlHash = NewsUrlSupport.sha256(canonicalUrl);
        return articleRepository.findByCanonicalUrlHash(urlHash)
                .map(article -> article.getSourceBodyHtml() == null
                        || article.getSourceBodyHtml().isBlank()
                        || article.getImageUrl() == null
                        || article.getImageUrl().isBlank())
                .orElse(true);
    }

    private void refreshExisting(
            NewsSource source,
            NewsArticle article,
            NewsCandidate candidate,
            NewsPolicy.Evaluation evaluation
    ) {
        LocalDateTime now = LocalDateTime.now();
        if (candidate.imageUrl() != null && !candidate.imageUrl().isBlank()) {
            article.setImageUrl(trimTo(candidate.imageUrl(), 2048));
        }
        if (candidate.excerpt() != null && !candidate.excerpt().isBlank()) {
            article.setSourceExcerpt(NewsTextSupport.plainText(candidate.excerpt(), 480));
        }
        applySourceContent(article, candidate.sourceContent(), now);
        article.setPoliticalRisk(evaluation.politicalRisk());
        article.setVietnamRelevance(evaluation.vietnamRelevance());
        article.setStatus(evaluation.status());
        article.setRankScore(rankingPolicy.score(source, candidate, evaluation));
        article.setPublishedAt(candidate.publishedAt());
        article.setDeadlineAt(candidate.deadlineAt());
        article.setUpdatedAt(now);
        replaceAttachments(article, candidate.sourceContent());
        articleRepository.saveAndFlush(article);
    }

    private static void applySourceContent(
            NewsArticle article,
            NewsSourceContent content,
            LocalDateTime fetchedAt
    ) {
        if (content == null || content.html() == null || content.html().isBlank()) {
            return;
        }
        article.setSourceBodyHtml(content.html());
        article.setSourceBodyText(content.text());
        article.setSourceLayout(content.layout());
        article.setSourceAuthor(trimTo(content.author(), 180));
        article.setSourceViewCount(content.viewCount());
        article.setSourceContentFetchedAt(fetchedAt);
    }

    private static void replaceAttachments(NewsArticle article, NewsSourceContent content) {
        if (content == null || content.attachments().isEmpty()) {
            return;
        }
        List<NewsArticleAttachment> attachments = content.attachments().stream()
                .sorted(Comparator.comparingInt(NewsAttachmentCandidate::displayOrder))
                .map(candidate -> new NewsArticleAttachment(
                        trimTo(candidate.displayName(), 500),
                        trimTo(candidate.sourceUrl(), 2048),
                        trimTo(candidate.mediaType(), 120),
                        candidate.sizeBytes(),
                        candidate.displayOrder()
                ))
                .toList();
        article.setSourceAttachments(attachments);
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record PersistResult(PersistOutcome outcome, NewsArticle article) {
        static PersistResult published(NewsArticle article) {
            return new PersistResult(PersistOutcome.PUBLISHED, article);
        }

        static PersistResult rejected(NewsArticle article) {
            return new PersistResult(PersistOutcome.REJECTED, article);
        }

        static PersistResult duplicate() {
            return new PersistResult(PersistOutcome.DUPLICATE, null);
        }

        static PersistResult blacklisted() {
            return new PersistResult(PersistOutcome.BLACKLISTED, null);
        }
    }

    public enum PersistOutcome {
        PUBLISHED,
        REJECTED,
        DUPLICATE,
        BLACKLISTED
    }
}
