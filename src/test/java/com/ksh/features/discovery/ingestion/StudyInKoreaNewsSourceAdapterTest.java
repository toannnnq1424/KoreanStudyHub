package com.ksh.features.discovery.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.entity.NewsSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudyInKoreaNewsSourceAdapterTest {

    @Test
    void parsesOfficialGksJsonAndStripsHtml() {
        String json = """
                {
                  "curpage": 1,
                  "list": [{
                    "ntt_id": "4514",
                    "bbs_id": "BBSMSTR_000000000461",
                    "ntt_sj": "2027 Global Korea Scholarship Notice",
                    "ntt_cn": "<p>Applications are now <strong>open</strong>.</p>",
                    "frst_regist_pnttm": "2026-07-10",
                    "frst_register_nm": "GKS Team",
                    "rdcnt": 1520,
                    "atch_file_id": "FILE_123",
                    "ntce_endde": "20260930"
                  }]
                }
                """;
        NewsSource source = RssNewsSourceAdapterTest.source(NewsCategory.SCHOLARSHIP);
        source.setLanguageCode("en");
        StudyInKoreaNewsSourceAdapter adapter =
                new StudyInKoreaNewsSourceAdapter(null, new ObjectMapper());

        List<NewsCandidate> candidates = adapter.parse(json, source);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.title()).isEqualTo("2027 Global Korea Scholarship Notice");
            assertThat(candidate.excerpt()).isEqualTo("Applications are now open.");
            assertThat(candidate.canonicalUrl()).contains("nttId=4514");
            assertThat(candidate.publishedAt().toLocalDate().toString()).isEqualTo("2026-07-10");
            assertThat(candidate.deadlineAt().toLocalDate().toString()).isEqualTo("2026-09-30");
            assertThat(candidate.sourceContent().html()).contains("<strong>open</strong>");
            assertThat(candidate.sourceContent().author()).isEqualTo("GKS Team");
            assertThat(candidate.sourceContent().viewCount()).isEqualTo(1520);
            assertThat(candidate.sourceContent().attachmentGroupId()).isEqualTo("FILE_123");
        });
    }

    @Test
    void fetchAdvancesThroughOlderGksPages() {
        NewsHttpClient client = mock(NewsHttpClient.class);
        NewsSource source = RssNewsSourceAdapterTest.source(NewsCategory.SCHOLARSHIP);
        source.setFeedUrl(
                "https://www.studyinkorea.go.kr/plan/getGksNoticeList.do"
                        + "?bbsId=BBSMSTR_000000000461&page=1"
        );
        source.setLanguageCode("en");
        source.setCrawlCursor(2);
        when(client.get(contains("page=1"), eq(org.springframework.http.MediaType.APPLICATION_JSON)))
                .thenReturn(page("3001", "Newest"));
        when(client.get(contains("page=2"), eq(org.springframework.http.MediaType.APPLICATION_JSON)))
                .thenReturn(page("2001", "Archive two"));
        when(client.get(contains("page=3"), eq(org.springframework.http.MediaType.APPLICATION_JSON)))
                .thenReturn(page("1001", "Archive three"));
        StudyInKoreaNewsSourceAdapter adapter =
                new StudyInKoreaNewsSourceAdapter(client, new ObjectMapper(), 3);

        List<NewsCandidate> candidates = adapter.fetch(source);

        assertThat(candidates).extracting(NewsCandidate::externalId)
                .containsExactly("3001", "2001", "1001");
        assertThat(source.getCrawlCursor()).isEqualTo(4);
    }

    private static String page(String id, String title) {
        return """
                {"list":[{
                  "ntt_id":"%s",
                  "bbs_id":"BBSMSTR_000000000461",
                  "ntt_sj":"%s",
                  "ntt_cn":"<p>Body</p>",
                  "frst_regist_pnttm":"2026-07-30"
                }]}
                """.formatted(id, title);
    }
}
