package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.entity.NewsSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class NewsRankingPolicy {

    public BigDecimal score(
            NewsSource source,
            NewsCandidate candidate,
            NewsPolicy.Evaluation evaluation
    ) {
        long ageHours = Math.max(
                0,
                Duration.between(candidate.publishedAt(), LocalDateTime.now()).toHours()
        );
        double recency = Math.max(0, 40 - (ageHours / 24.0 * 1.4));
        double vietnamBoost = evaluation.vietnamRelevance() > 0 ? 6 : 0;
        double opportunityBoost = scholarshipQuality(candidate);
        double excerptBoost = candidate.excerpt() == null || candidate.excerpt().isBlank() ? 0 : 4;
        double score = source.getPriorityWeight()
                + recency
                + vietnamBoost
                + opportunityBoost
                + excerptBoost;
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private static double scholarshipQuality(NewsCandidate candidate) {
        if (candidate.category() != NewsCategory.SCHOLARSHIP) {
            return 0;
        }
        String text = NewsTextSupport.normalizeForPolicy(
                candidate.title() + " " + (candidate.excerpt() == null ? "" : candidate.excerpt())
        );
        double score = 8;
        if (containsAny(
                text,
                "scholarship guideline",
                "application guideline",
                "applicants",
                "apply",
                "recruitment",
                "장학생 모집",
                "모집 공고"
        )) {
            score += 24;
        }
        if (containsAny(
                text,
                "newsletter",
                "final result",
                "final choice",
                "revised schedule",
                "invitation letter"
        )) {
            score -= 34;
        }
        if (candidate.deadlineAt() != null) {
            score += candidate.deadlineAt().isAfter(LocalDateTime.now()) ? 28 : -45;
        }
        return score;
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
