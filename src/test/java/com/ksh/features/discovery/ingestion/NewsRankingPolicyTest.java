package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.entity.NewsSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NewsRankingPolicyTest {

    private final NewsRankingPolicy policy = new NewsRankingPolicy();

    @Test
    void vietnamIsOnlyASmallSignalInsteadOfAForcedTopRank() {
        NewsSource source = source();
        NewsCandidate regular = candidate(
                "Lễ hội văn hóa mới tại Seoul",
                NewsCategory.CULTURE
        );
        NewsCandidate vietnam = candidate(
                "Lễ hội văn hóa Việt Nam tại Seoul",
                NewsCategory.CULTURE
        );

        BigDecimal regularScore = policy.score(
                source,
                regular,
                new NewsPolicy.Evaluation(
                        com.ksh.features.discovery.entity.NewsArticleStatus.PUBLISHED,
                        false,
                        0
                )
        );
        BigDecimal vietnamScore = policy.score(
                source,
                vietnam,
                new NewsPolicy.Evaluation(
                        com.ksh.features.discovery.entity.NewsArticleStatus.PUBLISHED,
                        false,
                        100
                )
        );

        assertThat(vietnamScore.subtract(regularScore)).isEqualByComparingTo("6.00");
    }

    @Test
    void actionableScholarshipGuidelinesBeatAdministrativeNotices() {
        NewsSource source = source();
        NewsPolicy.Evaluation evaluation = new NewsPolicy.Evaluation(
                com.ksh.features.discovery.entity.NewsArticleStatus.PUBLISHED,
                false,
                0
        );
        BigDecimal actionable = policy.score(
                source,
                candidate("2027 GKS Scholarship Application Guidelines", NewsCategory.SCHOLARSHIP),
                evaluation
        );
        BigDecimal administrative = policy.score(
                source,
                candidate("Revised Schedule for Invitation Letters", NewsCategory.SCHOLARSHIP),
                evaluation
        );

        assertThat(actionable).isGreaterThan(administrative);
    }

    private static NewsSource source() {
        NewsSource source = new NewsSource();
        source.setPriorityWeight(80);
        return source;
    }

    private static NewsCandidate candidate(String title, NewsCategory category) {
        return new NewsCandidate(
                title,
                title,
                "Applications are open.",
                "https://world.kbs.co.kr/service/news_view.htm?Seq_Code=1&lang=v",
                null,
                "vi",
                category,
                LocalDateTime.now(),
                null
        );
    }
}
