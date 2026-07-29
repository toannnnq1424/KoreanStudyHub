package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.entity.NewsSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KoreaNetNewsSourceAdapterTest {

    @Test
    void parsesCurrentKoreaNetListMarkup() {
        String html = """
                <html><body>
                  <div class="list-box">
                    <a href="javascript:contentView( '/NewsFocus/FoodTravel', '294869', 'pageIndex=1')">
                      <div class="thumb">
                        <img src="/upload/content/image/story.jpg" alt="Ảnh"/>
                      </div>
                      <p class="tit">Du lịch Hàn Quốc tiếp nối mối nhân duyên với Việt Nam</p>
                    </a>
                    <p class="date">26.06.2026</p>
                  </div>
                </body></html>
                """;
        NewsSource source = RssNewsSourceAdapterTest.source(NewsCategory.FOOD);
        source.setFeedUrl("https://vietnamese.korea.net/NewsFocus/FoodTravel");
        KoreaNetNewsSourceAdapter adapter = new KoreaNetNewsSourceAdapter(null);

        List<NewsCandidate> candidates = adapter.parse(html, source);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.externalId()).isEqualTo("294869");
            assertThat(candidate.canonicalUrl()).isEqualTo(
                    "https://vietnamese.korea.net/NewsFocus/FoodTravel/view?articleId=294869"
            );
            assertThat(candidate.imageUrl()).isEqualTo(
                    "https://vietnamese.korea.net/upload/content/image/story.jpg"
            );
            assertThat(candidate.publishedAt().toLocalDate().toString()).isEqualTo("2026-06-26");
        });
    }

    @Test
    void fetchAlwaysChecksPageOneAndAdvancesArchiveCursor() {
        NewsHttpClient client = mock(NewsHttpClient.class);
        NewsSource source = RssNewsSourceAdapterTest.source(NewsCategory.CULTURE);
        source.setFeedUrl("https://vietnamese.korea.net/NewsFocus/Culture");
        source.setCrawlCursor(2);
        when(client.get(contains("pageIndex=1"), eq(org.springframework.http.MediaType.TEXT_HTML)))
                .thenReturn(page("301", "Bài mới"));
        when(client.get(contains("pageIndex=2"), eq(org.springframework.http.MediaType.TEXT_HTML)))
                .thenReturn(page("201", "Bài lịch sử 2"));
        when(client.get(contains("pageIndex=3"), eq(org.springframework.http.MediaType.TEXT_HTML)))
                .thenReturn(page("101", "Bài lịch sử 3"));
        KoreaNetNewsSourceAdapter adapter = new KoreaNetNewsSourceAdapter(client, 3);

        List<NewsCandidate> candidates = adapter.fetch(source);

        assertThat(candidates).extracting(NewsCandidate::externalId)
                .containsExactly("301", "201", "101");
        assertThat(source.getCrawlCursor()).isEqualTo(4);
    }

    private static String page(String id, String title) {
        return """
                <div class="list-box">
                  <a href="javascript:contentView('/NewsFocus/Culture', '%s', 'pageIndex=1')">
                    <p class="tit">%s</p>
                  </a>
                  <p class="date">30.07.2026</p>
                </div>
                """.formatted(id, title);
    }
}
