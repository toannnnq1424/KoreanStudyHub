package com.ksh.features.discovery.dictionary;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.ingestion.NewsTextSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NewsVocabularyCandidateSelector {

    private static final Map<NewsCategory, List<String>> CATEGORY_WORDS = Map.of(
            NewsCategory.CULTURE, List.of("문화", "교류", "전시"),
            NewsCategory.FOOD, List.of("음식", "재료", "맛"),
            NewsCategory.ENTERTAINMENT, List.of("영화", "배우", "작품"),
            NewsCategory.SCHOLARSHIP, List.of("장학금", "지원", "대학")
    );

    private static final List<KeywordWord> KEYWORD_WORDS = List.of(
            new KeywordWord("viet nam", "베트남"),
            new KeywordWord("vietnam", "베트남"),
            new KeywordWord("webtoon", "웹툰"),
            new KeywordWord("le hoi", "축제"),
            new KeywordWord("festival", "축제"),
            new KeywordWord("kimchi", "김치"),
            new KeywordWord("am thuc", "음식"),
            new KeywordWord("du lich", "여행"),
            new KeywordWord("phim", "영화"),
            new KeywordWord("dien vien", "배우"),
            new KeywordWord("hoc bong", "장학금")
    );

    public List<String> select(NewsArticle article) {
        String title = NewsTextSupport.normalizeForPolicy(article.getDisplayTitle());
        Set<String> selected = new LinkedHashSet<>();
        KEYWORD_WORDS.forEach(candidate -> {
            if (title.contains(candidate.keyword())) {
                selected.add(candidate.word());
            }
        });
        selected.addAll(CATEGORY_WORDS.getOrDefault(article.getCategory(), List.of()));
        return new ArrayList<>(selected).stream().limit(3).toList();
    }

    private record KeywordWord(String keyword, String word) {
    }
}
