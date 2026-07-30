package com.ksh.features.discovery.repository;

import com.ksh.features.discovery.entity.NewsVocabulary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsVocabularyRepository extends JpaRepository<NewsVocabulary, Long> {
    List<NewsVocabulary> findByArticleIdOrderByDisplayOrderAsc(Long articleId);
    Optional<NewsVocabulary> findFirstByArticleIdAndKoreanWord(Long articleId, String koreanWord);
    boolean existsByArticleIdAndKoreanWord(Long articleId, String koreanWord);
}
