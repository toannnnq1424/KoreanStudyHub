package com.ksh.features.dictionary;

import com.ksh.features.dictionary.KoreanDictionaryDtos.SaveRequest;
import com.ksh.features.discovery.dictionary.KoreanDictionaryClient;
import com.ksh.features.flashcards.entity.Flashcard;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardDeckRepository;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KoreanDictionaryLearningServiceTest {
    @Test void rejectsSavingIntoAnotherUsersDeck() {
        KoreanDictionaryClient client = mock(KoreanDictionaryClient.class);
        FlashcardDeckRepository decks = mock(FlashcardDeckRepository.class);
        FlashcardRepository cards = mock(FlashcardRepository.class);
        FlashcardDeck deck = mock(FlashcardDeck.class);
        when(deck.getOwnerId()).thenReturn(99L);
        when(decks.findById(5L)).thenReturn(Optional.of(deck));
        KoreanDictionaryLearningService service = new KoreanDictionaryLearningService(client, decks, cards);
        assertThatThrownBy(() -> service.save(1L,
                new SaveRequest(5L, "문화", "văn hóa", null, null, null)))
                .hasMessageContaining("Không tìm thấy bộ thẻ");
    }
}
