package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.dictionary.NewsVocabularyEnrichmentService;
import com.ksh.features.discovery.entity.NewsIngestionRun;
import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.entity.NewsSourceType;
import com.ksh.features.discovery.repository.NewsIngestionRunRepository;
import com.ksh.features.discovery.repository.NewsSourceRepository;
import com.ksh.features.discovery.service.NewsBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NewsIngestionOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(NewsIngestionOrchestrator.class);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(45);

    private final NewsSourceRepository sourceRepository;
    private final NewsIngestionRunRepository runRepository;
    private final NewsArticleWriter articleWriter;
    private final NewsSourceContentCrawler contentCrawler;
    private final NewsIngestionLease ingestionLease;
    private final NewsVocabularyEnrichmentService vocabularyEnrichmentService;
    private final NewsBlacklistService blacklistService;
    private final Map<NewsSourceType, NewsSourceAdapter> adapters;

    public NewsIngestionOrchestrator(
            NewsSourceRepository sourceRepository,
            NewsIngestionRunRepository runRepository,
            NewsArticleWriter articleWriter,
            NewsSourceContentCrawler contentCrawler,
            NewsIngestionLease ingestionLease,
            NewsVocabularyEnrichmentService vocabularyEnrichmentService,
            NewsBlacklistService blacklistService,
            List<NewsSourceAdapter> adapters
    ) {
        this.sourceRepository = sourceRepository;
        this.runRepository = runRepository;
        this.articleWriter = articleWriter;
        this.contentCrawler = contentCrawler;
        this.ingestionLease = ingestionLease;
        this.vocabularyEnrichmentService = vocabularyEnrichmentService;
        this.blacklistService = blacklistService;
        this.adapters = new EnumMap<>(NewsSourceType.class);
        adapters.forEach(adapter -> this.adapters.put(adapter.supportedType(), adapter));
    }

    public RunSummary run(Trigger trigger) {
        NewsIngestionLease.Lease lease = ingestionLease.tryAcquire(LEASE_DURATION);
        if (!lease.acquired()) {
            NewsIngestionRun skipped = startRun(trigger);
            skipped.setStatus("SKIPPED");
            skipped.setErrorMessage("Một tiến trình nhập tin khác đang chạy.");
            skipped.setCompletedAt(LocalDateTime.now());
            runRepository.save(skipped);
            return RunSummary.from(skipped);
        }

        NewsIngestionRun run = startRun(trigger);
        try {
            List<NewsSource> sources = sourceRepository.findByEnabledTrueOrderByPriorityWeightDesc();
            run.setSourceCount(sources.size());
            runRepository.save(run);
            for (NewsSource source : sources) {
                ingestSource(source, run);
            }
            vocabularyEnrichmentService.enrichRecentMissing();
            run.setStatus(run.getErrorCount() == 0 ? "SUCCEEDED" : "PARTIAL");
        } catch (RuntimeException exception) {
            log.error("Korea Discovery ingestion failed", exception);
            run.setStatus("FAILED");
            run.setErrorCount(run.getErrorCount() + 1);
            run.setErrorMessage(errorMessage(exception, 1000));
        } finally {
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            ingestionLease.release(lease);
        }
        return RunSummary.from(run);
    }

    private void ingestSource(NewsSource source, NewsIngestionRun run) {
        LocalDateTime attemptedAt = LocalDateTime.now();
        source.setLastAttemptAt(attemptedAt);
        source.setUpdatedAt(attemptedAt);
        sourceRepository.save(source);

        try {
            NewsSourceAdapter adapter = adapters.get(source.getSourceType());
            if (adapter == null) {
                throw new IllegalStateException("Chưa có adapter cho " + source.getSourceType());
            }
            List<NewsCandidate> candidates = adapter.fetch(source);
            run.setFetchedCount(run.getFetchedCount() + candidates.size());
            for (NewsCandidate candidate : candidates) {
                try {
                    if (blacklistService.isBlacklisted(candidate.canonicalUrl())) {
                        run.setBlacklistedCount(run.getBlacklistedCount() + 1);
                        continue;
                    }
                    NewsCandidate enriched = articleWriter.requiresContentFetch(candidate)
                            ? contentCrawler.enrichIfNeeded(source, candidate)
                            : candidate;
                    applyResult(run, articleWriter.persist(source, enriched));
                } catch (RuntimeException itemException) {
                    run.setErrorCount(run.getErrorCount() + 1);
                    log.warn(
                            "Skipped invalid item from source {}: {}",
                            source.getCode(),
                            errorMessage(itemException, 240)
                    );
                }
            }
            source.setLastSuccessAt(LocalDateTime.now());
            source.setLastError(null);
        } catch (RuntimeException sourceException) {
            run.setErrorCount(run.getErrorCount() + 1);
            source.setLastError(errorMessage(sourceException, 500));
            log.warn("News source {} failed", source.getCode(), sourceException);
        } finally {
            source.setUpdatedAt(LocalDateTime.now());
            sourceRepository.save(source);
            runRepository.save(run);
        }
    }

    private static void applyResult(
            NewsIngestionRun run,
            NewsArticleWriter.PersistResult result
    ) {
        switch (result.outcome()) {
            case PUBLISHED -> run.setPublishedCount(run.getPublishedCount() + 1);
            case REJECTED -> run.setRejectedCount(run.getRejectedCount() + 1);
            case DUPLICATE -> run.setDuplicateCount(run.getDuplicateCount() + 1);
            case BLACKLISTED -> run.setBlacklistedCount(run.getBlacklistedCount() + 1);
        }
    }

    private NewsIngestionRun startRun(Trigger trigger) {
        NewsIngestionRun run = new NewsIngestionRun();
        run.setTriggerType(trigger.name());
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        return runRepository.save(run);
    }

    private static String errorMessage(Throwable throwable, int maxLength) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }

    public enum Trigger {
        SCHEDULED,
        MANUAL
    }

    public record RunSummary(
            Long runId,
            String status,
            int fetched,
            int published,
            int rejected,
            int duplicates,
            int blacklisted,
            int errors
    ) {
        static RunSummary from(NewsIngestionRun run) {
            return new RunSummary(
                    run.getId(),
                    run.getStatus(),
                    run.getFetchedCount(),
                    run.getPublishedCount(),
                    run.getRejectedCount(),
                    run.getDuplicateCount(),
                    run.getBlacklistedCount(),
                    run.getErrorCount()
            );
        }
    }
}
