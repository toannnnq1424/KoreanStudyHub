package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.entity.NewsCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NewsPolicyTest {

    private final NewsPolicy policy = new NewsPolicy();

    @Test
    void prioritizesVietnamWithoutRejectingCultureStory() {
        NewsCandidate candidate = candidate(
                "Du lịch Hàn Quốc tiếp nối mối nhân duyên 800 năm giữa Hàn Quốc và Việt Nam"
        );

        NewsPolicy.Evaluation evaluation = policy.evaluate(candidate);

        assertThat(evaluation.status()).isEqualTo(NewsArticleStatus.PUBLISHED);
        assertThat(evaluation.politicalRisk()).isFalse();
        assertThat(evaluation.vietnamRelevance()).isEqualTo(100);
    }

    @Test
    void rejectsVietnamesePoliticalStory() {
        NewsPolicy.Evaluation evaluation = policy.evaluate(candidate(
                "Tổng thống phát biểu trước cuộc bầu cử tại Quốc hội"
        ));

        assertThat(evaluation.status()).isEqualTo(NewsArticleStatus.REJECTED);
        assertThat(evaluation.politicalRisk()).isTrue();
    }

    @Test
    void rejectsKoreanPoliticalStory() {
        NewsPolicy.Evaluation evaluation = policy.evaluate(candidate(
                "대통령 국회 선거 관련 소식"
        ));

        assertThat(evaluation.status()).isEqualTo(NewsArticleStatus.REJECTED);
    }

    private static NewsCandidate candidate(String title) {
        return new NewsCandidate(
                "1",
                title,
                null,
                "https://vietnamese.korea.net/NewsFocus/Culture/view?articleId=1",
                null,
                "vi",
                NewsCategory.CULTURE,
                LocalDateTime.now(),
                null
        );
    }
}
