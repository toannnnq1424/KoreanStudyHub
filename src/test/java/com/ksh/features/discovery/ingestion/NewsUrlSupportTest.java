package com.ksh.features.discovery.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsUrlSupportTest {

    @Test
    void canonicalizesSchemeQueryOrderAndTrackingParameters() {
        String canonical = NewsUrlSupport.canonicalize(
                "http://world.kbs.co.kr/service/news_view.htm"
                        + "?utm_source=test&lang=v&Seq_Code=123#section"
        );

        assertThat(canonical)
                .isEqualTo("https://world.kbs.co.kr/service/news_view.htm?Seq_Code=123&lang=v");
        assertThat(NewsUrlSupport.sha256(canonical)).hasSize(64);
    }

    @Test
    void rejectsUnknownArticleHost() {
        assertThatThrownBy(() -> NewsUrlSupport.canonicalize("https://example.com/story"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nguồn đã duyệt");
    }
}
