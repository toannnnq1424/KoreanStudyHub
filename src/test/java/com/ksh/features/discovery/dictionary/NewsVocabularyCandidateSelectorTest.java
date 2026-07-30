package com.ksh.features.discovery.dictionary;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsVocabularyCandidateSelectorTest {

    @Test
    void putsVietnamKeywordBeforeCategoryWordsAndCapsAtThree() {
        NewsArticle article = new NewsArticle();
        article.setDisplayTitle("Lễ hội văn hóa Việt Nam tại Seoul");
        article.setCategory(NewsCategory.CULTURE);

        assertThat(new NewsVocabularyCandidateSelector().select(article))
                .containsExactly("베트남", "축제", "문화");
    }
}
