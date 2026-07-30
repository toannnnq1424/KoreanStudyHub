package com.ksh.features.discovery.service;

import com.ksh.features.discovery.dictionary.NewsVocabularyEnrichmentService;
import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.entity.NewsIngestionRun;
import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.repository.NewsIngestionRunRepository;
import com.ksh.features.discovery.repository.NewsSourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AdminNewsService {

    private static final int PAGE_SIZE = 20;

    private final NewsSourceRepository sourceRepository;
    private final NewsIngestionRunRepository runRepository;
    private final NewsArticleRepository articleRepository;
    private final NewsBlacklistService blacklistService;
    private final NewsVocabularyEnrichmentService vocabularyEnrichmentService;

    public AdminNewsService(
            NewsSourceRepository sourceRepository,
            NewsIngestionRunRepository runRepository,
            NewsArticleRepository articleRepository,
            NewsBlacklistService blacklistService,
            NewsVocabularyEnrichmentService vocabularyEnrichmentService
    ) {
        this.sourceRepository = sourceRepository;
        this.runRepository = runRepository;
        this.articleRepository = articleRepository;
        this.blacklistService = blacklistService;
        this.vocabularyEnrichmentService = vocabularyEnrichmentService;
    }

    public Overview overview(int page, Long runId, Long aiRunId, String aiStatus) {
        int safePage = Math.max(1, page);
        String safeAiStatus = switch (aiStatus == null ? "" : aiStatus.trim().toLowerCase()) {
            case "generated", "pending", "failed" -> aiStatus.trim().toLowerCase();
            default -> null;
        };
        return new Overview(
                sourceRepository.findAll(),
                runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 10)),
                articleRepository.countByStatus(NewsArticleStatus.PUBLISHED),
                articleRepository.countByStatus(NewsArticleStatus.REJECTED),
                blacklistService.count(),
                articleRepository.countBySourceBodyHtmlIsNotNull(),
                articleRepository.countByImageUrlIsNotNull(),
                vocabularyEnrichmentService.isDictionaryConfigured(),
                runId,
                aiRunId,
                safeAiStatus,
                articleRepository.findAdminArticles(
                        runId, aiRunId, safeAiStatus, PageRequest.of(safePage - 1, PAGE_SIZE))
        );
    }

    @Transactional
    public int deleteOneRecentArticlePerSource(int requestedCount) {
        int safeCount = Math.max(1, Math.min(requestedCount, 10));
        List<NewsArticle> selected = new java.util.ArrayList<>();
        for (NewsSource source : sourceRepository.findByEnabledTrueOrderByPriorityWeightDesc()) {
            if (selected.size() >= safeCount) {
                break;
            }
            articleRepository
                    .findBySourceIdAndStatusOrderByPublishedAtDescIdDesc(
                            source.getId(),
                            NewsArticleStatus.PUBLISHED,
                            PageRequest.of(0, 1)
                    )
                    .stream()
                    .findFirst()
                    .ifPresent(selected::add);
        }
        articleRepository.deleteAll(selected);
        articleRepository.flush();
        return selected.size();
    }

    @Transactional
    public BulkActionResult deleteArticles(Collection<Long> articleIds, boolean blacklistBeforeDelete) {
        List<NewsArticle> selected = loadArticles(articleIds);
        int skipped = safeCount(articleIds) - selected.size();
        int blacklisted = blacklistBeforeDelete
                ? blacklistService.blacklistArticles(selected, "Xóa từ admin /discovery")
                : 0;
        if (!blacklistBeforeDelete) {
            articleRepository.deleteAll(selected);
            articleRepository.flush();
        }
        return new BulkActionResult(selected.size(), selected.size(), blacklisted, skipped);
    }

    @Transactional
    public BulkActionResult blacklistArticles(Collection<Long> articleIds) {
        List<NewsArticle> selected = loadArticles(articleIds);
        int skipped = safeCount(articleIds) - selected.size();
        int blacklisted = blacklistService.blacklistArticles(selected, "Blacklist từ admin /discovery");
        return new BulkActionResult(selected.size(), 0, blacklisted, skipped);
    }

    private List<NewsArticle> loadArticles(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<NewsArticle> articles = new ArrayList<>();
        articleRepository.findAllById(ids).forEach(articles::add);
        return articles;
    }

    private static int safeCount(Collection<Long> articleIds) {
        if (articleIds == null) {
            return 0;
        }
        return (int) articleIds.stream().filter(Objects::nonNull).distinct().count();
    }

    public record Overview(
            List<NewsSource> sources,
            List<NewsIngestionRun> recentRuns,
            long publishedCount,
            long rejectedCount,
            long blacklistedCount,
            long fullContentCount,
            long imageCount,
            boolean dictionaryConfigured,
            Long selectedRunId,
            Long selectedAiRunId,
            String selectedAiStatus,
            Page<NewsArticle> recentArticles
    ) {
    }

    public record BulkActionResult(
            int matchedCount,
            int affectedCount,
            int blacklistedCount,
            int skippedCount
    ) {
    }
}
