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

    @Mock private KoreanDictionaryClient dictionaryClient;
    @Mock private NewsArticleRepository articleRepository;
    @Mock private NewsVocabularyRepository vocabularyRepository;
    @Mock private FlashcardDeckRepository deckRepository;
    @Mock private FlashcardRepository cardRepository;

    private DiscoveryVocabularyLearningService service;
    private NewsArticle article;

    @BeforeEach
    void setUp() {
        service = new DiscoveryVocabularyLearningService(
                dictionaryClient,
                articleRepository,
                vocabularyRepository,
                deckRepository,
                cardRepository
        );
        article = new NewsArticle();
        article.setId(7L);
        article.setDisplayTitle("한국 문화 이야기");
        article.setCanonicalUrl("https://example.test/article");
        article.setStatus(NewsArticleStatus.PUBLISHED);
        when(articleRepository.findByIdAndStatus(7L, NewsArticleStatus.PUBLISHED))
                .thenReturn(Optional.of(article));
        when(vocabularyRepository.findFirstByArticleIdAndKoreanWord(7L, "문화"))
                .thenReturn(Optional.empty());
    }

    @Test
    void lookupUsesOfficialVietnameseDictionaryResult() {
        when(dictionaryClient.isConfigured()).thenReturn(true);
        when(dictionaryClient.lookupVietnamese("문화")).thenReturn(Optional.of(
                new DictionaryEntry(
                        "343267",
                        "문화",
                        "문화",
                        "명사",
                        "초급",
                        "văn hóa",
                        "https://krdict.korean.go.kr/vie/dicSearch/SearchView?ParaWordNo=343267"
                )
        ));

        var result = service.lookup(7L, " 문화 ");

        assertThat(result.found()).isTrue();
        assertThat(result.meaningVi()).isEqualTo("văn hóa");
        assertThat(result.dictionaryUrl()).contains("krdict.korean.go.kr");
    }

    @Test
    void manualMeaningStillSavesWhenDictionaryKeyIsMissing() {
        when(dictionaryClient.isConfigured()).thenReturn(false);
        FlashcardDeck deck = mock(FlashcardDeck.class);
        when(deck.getId()).thenReturn(55L);
        when(deck.getTitle()).thenReturn(DiscoveryVocabularyLearningService.DISCOVERY_DECK_TITLE);
        when(deckRepository.findFirstByOwnerIdAndTitleOrderByIdAsc(
                9L,
                DiscoveryVocabularyLearningService.DISCOVERY_DECK_TITLE
        )).thenReturn(Optional.empty());
        when(deckRepository.save(any(FlashcardDeck.class))).thenReturn(deck);
        when(cardRepository.findFirstByDeckIdAndFrontText(55L, "문화"))
                .thenReturn(Optional.empty());
        when(cardRepository.countByDeckId(55L)).thenReturn(2L);
        Flashcard saved = mock(Flashcard.class);
        when(saved.getId()).thenReturn(88L);
        when(cardRepository.save(any(Flashcard.class))).thenReturn(saved);

        var result = service.save(
                7L,
                9L,
                new SaveVocabularyRequest("문화", null, "văn hóa", "danh từ", null)
        );

        assertThat(result.deckId()).isEqualTo(55L);
        assertThat(result.cardId()).isEqualTo(88L);
        assertThat(result.alreadySaved()).isFalse();
        ArgumentCaptor<Flashcard> card = ArgumentCaptor.forClass(Flashcard.class);
        verify(cardRepository).save(card.capture());
        assertThat(card.getValue().getFrontText()).isEqualTo("문화");
        assertThat(card.getValue().getBackText())
                .startsWith("văn hóa")
                .contains("Từ loại: danh từ")
                .contains("Từ bài: 한국 문화 이야기")
                .contains("Nguồn: https://example.test/article");
    }
}
