package com.ksh.features.discovery.service;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.repository.NewsVocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryServiceTest {

    private NewsArticleRepository articleRepository;
    private NewsVocabularyRepository vocabularyRepository;
    private DiscoveryService service;

    @BeforeEach
    void setUp() {
        articleRepository = mock(NewsArticleRepository.class);
        vocabularyRepository = mock(NewsVocabularyRepository.class);
        service = new DiscoveryService(articleRepository, vocabularyRepository);
        when(vocabularyRepository.findByArticleIdOrderByDisplayOrderAsc(anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void koModeBringsHangulStoryToTheTopWhileViModePrefersVietnameseStories() {
        NewsArticle vietnamese = article(
                11L,
                "viet-story",
                "Tin Việt Nam",
                "Bài viết liên quan Việt Nam",
                "https://vietnamese.korea.net/NewsFocus/Culture/view?articleId=11",
                "vi",
                NewsCategory.CULTURE,
                BigDecimal.valueOf(99),
                "Korea.net Vietnamese"
        );
        NewsArticle korean = article(
                12L,
                "ko-story",
                "한국 문화 이야기",
                "한국 문화 이야기",
                "https://world.kbs.co.kr/service/news_view.htm?Seq_Code=12&lang=v",
                "en",
                NewsCategory.CULTURE,
                BigDecimal.valueOf(10),
                "KBS WORLD Vietnamese"
        );

        when(articleRepository.findFeed(any(), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(vietnamese, korean),
                        PageRequest.of(0, 240),
                        2
                ));

        assertThat(service.page(null, null, "ko", 1).hero().id()).isEqualTo(12L);
        assertThat(service.page(null, null, "vi", 1).hero().id()).isEqualTo(11L);
    }

    private static NewsArticle article(
            Long id,
            String slug,
            String title,
            String originalTitle,
            String url,
            String language,
            NewsCategory category,
            BigDecimal rankScore,
            String sourceName
    ) {
        NewsArticle article = new NewsArticle();
        article.setId(id);
        article.setSlug(slug);
        article.setDisplayTitle(title);
        article.setOriginalTitle(originalTitle);
        article.setSourceExcerpt(title);
        article.setCanonicalUrl(url);
        article.setLanguageCode(language);
        article.setCategory(category);
        article.setRankScore(rankScore);
        article.setSourceName(sourceName);
        article.setPublishedAt(LocalDateTime.of(2026, 7, 29, 12, 0));
        article.setStatus(NewsArticleStatus.PUBLISHED);
        return article;
    }
}
