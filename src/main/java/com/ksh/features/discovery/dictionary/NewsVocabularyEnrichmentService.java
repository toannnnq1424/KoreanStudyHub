package com.ksh.features.discovery.dictionary;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsVocabulary;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.repository.NewsVocabularyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NewsVocabularyEnrichmentService {

    private static final Logger log =
            LoggerFactory.getLogger(NewsVocabularyEnrichmentService.class);

    private final KoreanDictionaryClient dictionaryClient;
    private final NewsVocabularyCandidateSelector selector;
    private final NewsArticleRepository articleRepository;
    private final NewsVocabularyRepository vocabularyRepository;
    private final int batchSize;

    public NewsVocabularyEnrichmentService(
            KoreanDictionaryClient dictionaryClient,
            NewsVocabularyCandidateSelector selector,
            NewsArticleRepository articleRepository,
            NewsVocabularyRepository vocabularyRepository,
            @Value("${app.news.dictionary.batch-size:12}") int batchSize
    ) {
        this.dictionaryClient = dictionaryClient;
        this.selector = selector;
        this.articleRepository = articleRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.batchSize = Math.max(1, Math.min(batchSize, 30));
    }

    public int enrichRecentMissing() {
        if (!dictionaryClient.isConfigured()) {
            return 0;
        }
        List<NewsArticle> articles =
                articleRepository.findVocabularyCandidates(PageRequest.of(0, batchSize));
        int enriched = 0;
        for (NewsArticle article : articles) {
            try {
                if (enrich(article)) {
                    enriched++;
                }
            } catch (RuntimeException exception) {
                log.warn("Dictionary enrichment failed for article {}", article.getId(), exception);
            }
        }
        return enriched;
    }

    @Transactional
    public boolean enrich(NewsArticle article) {
        int order = 0;
        boolean savedAny = false;
        for (String word : selector.select(article)) {
            if (vocabularyRepository.existsByArticleIdAndKoreanWord(article.getId(), word)) {
                continue;
            }
            DictionaryEntry entry = dictionaryClient.lookupVietnamese(word).orElse(null);
            if (entry == null) {
                continue;
            }
            NewsVocabulary vocabulary = new NewsVocabulary();
            vocabulary.setArticleId(article.getId());
            vocabulary.setTargetCode(entry.targetCode());
            vocabulary.setKoreanWord(entry.word());
            vocabulary.setPronunciation(entry.pronunciation());
            vocabulary.setPartOfSpeech(entry.partOfSpeech());
            vocabulary.setWordLevel(entry.wordLevel());
            vocabulary.setMeaningVi(entry.meaningVi());
            vocabulary.setDictionaryUrl(entry.dictionaryUrl());
            vocabulary.setDisplayOrder(order++);
            vocabulary.setVerified(true);
            vocabulary.setCreatedAt(LocalDateTime.now());
            vocabularyRepository.save(vocabulary);
            savedAny = true;
        }
        return savedAny;
    }

    public boolean isDictionaryConfigured() {
        return dictionaryClient.isConfigured();
    }
}
