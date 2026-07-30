package com.ksh.features.discovery.service;

import com.ksh.features.discovery.dictionary.DictionaryEntry;
import com.ksh.features.discovery.dictionary.KoreanDictionaryClient;
import com.ksh.features.discovery.dto.DiscoveryLearningDtos.SaveVocabularyRequest;
import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.repository.NewsVocabularyRepository;
import com.ksh.features.flashcards.entity.Flashcard;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardDeckRepository;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryVocabularyLearningServiceTest {
    @Mock KoreanDictionaryClient dictionaryClient;
    @Mock NewsArticleRepository articleRepository;
    @Mock NewsVocabularyRepository vocabularyRepository;
    @Mock FlashcardDeckRepository deckRepository;
    @Mock FlashcardRepository cardRepository;
    DiscoveryVocabularyLearningService service;

    @BeforeEach void setUp() {
        service = new DiscoveryVocabularyLearningService(dictionaryClient, articleRepository,
                vocabularyRepository, deckRepository, cardRepository);
        NewsArticle article = new NewsArticle();
        article.setId(7L); article.setStatus(NewsArticleStatus.PUBLISHED);
        when(articleRepository.findByIdAndStatus(7L, NewsArticleStatus.PUBLISHED)).thenReturn(Optional.of(article));
        when(vocabularyRepository.findFirstByArticleIdAndKoreanWord(7L, "문화")).thenReturn(Optional.empty());
    }

    @Test void lookupUsesOfficialVietnameseDictionaryResult() {
        when(dictionaryClient.isConfigured()).thenReturn(true);
        when(dictionaryClient.lookupVietnamese("문화")).thenReturn(Optional.of(
                new DictionaryEntry("343267", "문화", "문화", "명사", "초급", "văn hóa",
                        "https://krdict.korean.go.kr/vie/dicSearch/SearchView?ParaWordNo=343267")));
        assertThat(service.lookup(7L, " 문화 ").meaningVi()).isEqualTo("văn hóa");
    }

    @Test void savesCompactMeaningIntoExplicitOwnedDeck() {
        when(dictionaryClient.isConfigured()).thenReturn(false);
        FlashcardDeck deck = mock(FlashcardDeck.class);
        when(deck.getId()).thenReturn(55L); when(deck.getOwnerId()).thenReturn(9L); when(deck.getTitle()).thenReturn("TOPIK");
        when(deckRepository.findById(55L)).thenReturn(Optional.of(deck));
        when(cardRepository.findFirstByDeckIdAndFrontText(55L, "문화")).thenReturn(Optional.empty());
        when(cardRepository.countByDeckId(55L)).thenReturn(2L);
        Flashcard saved = mock(Flashcard.class); when(saved.getId()).thenReturn(88L);
        when(cardRepository.save(any(Flashcard.class))).thenReturn(saved);

        var result = service.save(7L, 9L,
                new SaveVocabularyRequest(55L, "문화", null, "văn hóa", "danh từ", null));
        assertThat(result.deckId()).isEqualTo(55L);
        ArgumentCaptor<Flashcard> card = ArgumentCaptor.forClass(Flashcard.class);
        verify(cardRepository).save(card.capture());
        assertThat(card.getValue().getFrontText()).isEqualTo("문화");
        assertThat(card.getValue().getBackText()).isEqualTo("văn hóa");
    }
}
