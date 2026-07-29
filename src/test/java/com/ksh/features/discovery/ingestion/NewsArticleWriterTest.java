package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.service.NewsBlacklistService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsArticleWriterTest {

    @Test
    void duplicateCrawlBackfillsAPreviouslyMissingImage() {
        NewsArticleRepository repository = mock(NewsArticleRepository.class);
        NewsBlacklistService blacklistService = mock(NewsBlacklistService.class);
        NewsArticle existing = new NewsArticle();
        existing.setPublishedAt(LocalDateTime.of(2026, 7, 29, 12, 0));
        when(repository.findByCanonicalUrlHash(anyString())).thenReturn(Optional.of(existing));
        when(blacklistService.isBlacklistedHash(anyString())).thenReturn(false);
        NewsArticleWriter writer = new NewsArticleWriter(
                repository,
                blacklistService,
                new NewsPolicy(),
                new NewsRankingPolicy()
        );
        NewsCandidate candidate = new NewsCandidate(
                "71203",
                "Một tin văn hóa",
                "Mô tả",
                "https://world.kbs.co.kr/service/news_view.htm?Seq_Code=71203&lang=v",
                "https://worldimg.kbs.co.kr/src/images/news/story.jpg",
                "vi",
                NewsCategory.CULTURE,
                LocalDateTime.of(2026, 7, 15, 12, 0),
                null
        );

        NewsArticleWriter.PersistResult result = writer.persist(new NewsSource(), candidate);

        assertThat(result.outcome()).isEqualTo(NewsArticleWriter.PersistOutcome.DUPLICATE);
        assertThat(existing.getImageUrl())
                .isEqualTo("https://worldimg.kbs.co.kr/src/images/news/story.jpg");
        assertThat(existing.getPublishedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 15, 12, 0));
        assertThat(existing.getUpdatedAt()).isNotNull();
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void blacklistedStoryIsSkippedBeforePersist() {
        NewsArticleRepository repository = mock(NewsArticleRepository.class);
        NewsBlacklistService blacklistService = mock(NewsBlacklistService.class);
        when(blacklistService.isBlacklistedHash(anyString())).thenReturn(true);
        NewsArticleWriter writer = new NewsArticleWriter(
                repository,
                blacklistService,
                new NewsPolicy(),
                new NewsRankingPolicy()
        );

        NewsCandidate candidate = new NewsCandidate(
                "71203",
                "Một tin văn hóa",
                "Mô tả",
                "https://world.kbs.co.kr/service/news_view.htm?Seq_Code=71203&lang=v",
                "https://worldimg.kbs.co.kr/src/images/news/story.jpg",
                "vi",
                NewsCategory.CULTURE,
                LocalDateTime.of(2026, 7, 15, 12, 0),
                null
        );

        NewsArticleWriter.PersistResult result = writer.persist(new NewsSource(), candidate);

        assertThat(result.outcome()).isEqualTo(NewsArticleWriter.PersistOutcome.BLACKLISTED);
        verify(repository, never()).findByCanonicalUrlHash(anyString());
    }
}
