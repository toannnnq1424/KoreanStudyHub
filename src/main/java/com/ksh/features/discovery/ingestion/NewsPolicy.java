package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsArticleStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NewsPolicy {

    private static final List<String> POLITICAL_TERMS = List.of(
            "tong thong", "thu tuong", "quoc hoi", "bau cu", "chinh dang",
            "dang cam quyen", "ngoai giao", "quan su", "ten lua", "hat nhan",
            "trieu tien", "lien trieu", "bo truong quoc phong",
            "president", "prime minister", "parliament", "election", "political party",
            "diplomacy", "military", "missile", "nuclear", "north korea",
            "대통령", "총리", "국회", "선거", "정당", "외교", "군사", "미사일",
            "핵무기", "북한", "남북"
    );

    private static final List<String> VIETNAM_TERMS = List.of(
            "viet nam", "vietnam", "vietnamese", "ha noi", "hanoi",
            "ho chi minh", "da nang", "can tho", "hai phong",
            "베트남", "하노이", "호찌민", "다낭"
    );

    public Evaluation evaluate(NewsCandidate candidate) {
        String haystack = NewsTextSupport.normalizeForPolicy(
                candidate.title() + " " + (candidate.excerpt() == null ? "" : candidate.excerpt())
        );
        boolean political = POLITICAL_TERMS.stream().anyMatch(haystack::contains);
        int vietnamRelevance = VIETNAM_TERMS.stream().anyMatch(haystack::contains) ? 100 : 0;
        NewsArticleStatus status = political
                ? NewsArticleStatus.REJECTED
                : NewsArticleStatus.PUBLISHED;
        return new Evaluation(status, political, vietnamRelevance);
    }

    public record Evaluation(
            NewsArticleStatus status,
            boolean politicalRisk,
            int vietnamRelevance
    ) {
    }
}
