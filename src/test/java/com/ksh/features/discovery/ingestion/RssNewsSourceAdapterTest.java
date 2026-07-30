package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.entity.NewsSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RssNewsSourceAdapterTest {

    @Test
    void parsesMetadataOnlyRssItem() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title><![CDATA[Lễ hội văn hóa Việt–Hàn khai mạc]]></title>
                      <link>https://world.kbs.co.kr/service/news_view.htm?lang=v&amp;Seq_Code=1</link>
                      <guid>KBS-1</guid>
                      <pubDate>Wed, 29 Jul 2026 10:15:00 +0900</pubDate>
                      <description><![CDATA[<p>Một sự kiện giao lưu văn hóa.</p>]]></description>
                      <enclosure url="https://world.kbs.co.kr/image.jpg" type="image/jpeg"/>
                    </item>
                  </channel>
                </rss>
                """;
        NewsSource source = source(NewsCategory.CULTURE);
        RssNewsSourceAdapter adapter = new RssNewsSourceAdapter(null);

        List<NewsCandidate> candidates = adapter.parse(xml, source);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.externalId()).isEqualTo("KBS-1");
            assertThat(candidate.title()).isEqualTo("Lễ hội văn hóa Việt–Hàn khai mạc");
            assertThat(candidate.excerpt()).isEqualTo("Một sự kiện giao lưu văn hóa.");
            assertThat(candidate.publishedAt().getHour()).isEqualTo(8);
            assertThat(candidate.category()).isEqualTo(NewsCategory.CULTURE);
        });
    }

    @Test
    void parsesKbsEntertainmentIsoLocalDate() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Golden Disc Awards tổ chức tại Việt Nam</title>
                      <link>https://world.kbs.co.kr/service/contents_view.htm?board_seq=469848</link>
                      <guid>KBS-469848</guid>
                      <pubDate>2026-07-15</pubDate>
                      <description>Một tin giải trí.</description>
                    </item>
                  </channel>
                </rss>
                """;
        RssNewsSourceAdapter adapter = new RssNewsSourceAdapter(null);

        List<NewsCandidate> candidates = adapter.parse(xml, source(NewsCategory.ENTERTAINMENT));

        assertThat(candidates).singleElement().satisfies(candidate ->
                assertThat(candidate.publishedAt())
                        .isEqualTo(LocalDateTime.of(2026, 7, 15, 12, 0))
        );
    }

    static NewsSource source(NewsCategory category) {
        NewsSource source = new NewsSource();
        source.setCode("TEST");
        source.setFeedUrl("https://world.kbs.co.kr/rss/test");
        source.setLanguageCode("vi");
        source.setDefaultCategory(category);
        return source;
    }
}
