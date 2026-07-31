package com.ksh.features.discovery.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.admin.settings.repository.AiSystemPromptRepository;
import com.ksh.features.ai.client.AiClient;
import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static com.ksh.features.ai.log.AiRequestLogger.SOURCE_DISCOVERY_NEWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsAiEditorialServiceTest {

    @Test
    void generatesAndStoresEditorialFieldsThroughTheSharedAiClient() {
        NewsArticleRepository articles = mock(NewsArticleRepository.class);
        AiSystemPromptRepository prompts = mock(AiSystemPromptRepository.class);
        AiClient client = mock(AiClient.class);
        NewsArticle article = new NewsArticle();
        article.setId(7L);
        article.setOriginalTitle("한국 문화 행사");
        article.setLanguageCode("ko");
        article.setSourceName("Korea.net");
        article.setSourceBodyText("가".repeat(120));
        when(articles.findAiEditorialCandidates(ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(article));
        when(articles.findById(7L)).thenReturn(Optional.of(article));
        when(prompts.findByNameAndEnabledTrue(NewsAiEditorialService.PROMPT_NAME))
                .thenReturn(Optional.empty());
        when(client.chatJsonObject(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(2_400), ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(SOURCE_DISCOVERY_NEWS)))
                .thenReturn("""
                        {"titleVi":"Ngày hội văn hóa Hàn Quốc",
                         "excerptVi":"Những điểm chính của sự kiện.",
                         "bodyVi":"Đoạn thứ nhất.\\n\\nĐoạn thứ hai."}
                        """);

        NewsAiEditorialService service = new NewsAiEditorialService(
                articles, prompts, client, new ObjectMapper());
        NewsAiEditorialService.EnrichmentSummary summary = service.enrichRecentMissing(42L);

        assertThat(summary.generated()).isEqualTo(1);
        assertThat(article.getAiEditorialTitle()).isEqualTo("Ngày hội văn hóa Hàn Quốc");
        assertThat(article.getAiEditorialExcerpt()).isEqualTo("Những điểm chính của sự kiện.");
        assertThat(article.getAiEditorialBody()).contains("\n\n");
        assertThat(article.getAiGeneratedAt()).isNotNull();
        assertThat(article.getAiGenerationRunId()).isEqualTo(42L);
        verify(articles).save(article);
    }

    @Test
    void parser_recovers_one_editorial_object_from_free_provider_prose() {
        NewsAiEditorialService service = new NewsAiEditorialService(
                mock(NewsArticleRepository.class),
                mock(AiSystemPromptRepository.class),
                mock(AiClient.class),
                new ObjectMapper());

        NewsAiEditorialService.Editorial editorial = service.parse("""
                Đây là kết quả:
                {"titleVi":"Tiêu đề","excerptVi":"Tóm tắt",
                 "bodyVi":"Đoạn một.\\n\\nĐoạn hai."}
                Hoàn tất.""");

        assertThat(editorial.titleVi()).isEqualTo("Tiêu đề");
        assertThat(editorial.bodyVi()).contains("\n\n");
    }

    @Test
    void retries_without_provider_json_mode_when_first_editorial_is_invalid() {
        NewsArticleRepository articles = mock(NewsArticleRepository.class);
        AiSystemPromptRepository prompts = mock(AiSystemPromptRepository.class);
        AiClient client = mock(AiClient.class);
        NewsArticle article = new NewsArticle();
        article.setId(8L);
        article.setOriginalTitle("한국 교육");
        article.setLanguageCode("ko");
        article.setSourceName("Korea.net");
        article.setSourceBodyText("교육 내용 ".repeat(30));
        when(articles.findAiEditorialCandidates(ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(article));
        when(articles.findById(8L)).thenReturn(Optional.of(article));
        when(prompts.findByNameAndEnabledTrue(NewsAiEditorialService.PROMPT_NAME))
                .thenReturn(Optional.empty());
        when(client.chatJsonObject(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(2_400), ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(SOURCE_DISCOVERY_NEWS))).thenReturn("bad");
        when(client.chat(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(2_400), ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(SOURCE_DISCOVERY_NEWS))).thenReturn("""
                {"titleVi":"Giáo dục Hàn Quốc",
                 "excerptVi":"Tóm tắt giáo dục.",
                 "bodyVi":"Đoạn một.\\n\\nĐoạn hai."}
                """);

        NewsAiEditorialService service = new NewsAiEditorialService(
                articles, prompts, client, new ObjectMapper());
        NewsAiEditorialService.EnrichmentSummary summary = service.enrichRecentMissing(43L);

        assertThat(summary.generated()).isEqualTo(1);
        assertThat(article.getAiEditorialTitle()).isEqualTo("Giáo dục Hàn Quốc");
        verify(client, times(1)).chat(
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(2_400), ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(SOURCE_DISCOVERY_NEWS));
    }
}
