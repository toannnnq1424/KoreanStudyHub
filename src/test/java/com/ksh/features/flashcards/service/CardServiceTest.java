package com.ksh.features.flashcards.service;

import com.ksh.features.flashcards.dto.FlashcardDtos.CardItem;
import com.ksh.features.flashcards.dto.FlashcardDtos.CardView;
import com.ksh.features.flashcards.entity.Flashcard;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardServiceTest {

    private final FlashcardRepository cardRepository = mock(FlashcardRepository.class);
    private final DeckAccessResolver accessResolver = mock(DeckAccessResolver.class);

    private final CardService service = new CardService(cardRepository, accessResolver);

    @Test
    void replaceCards_withValidNewCards_returnsSavedCardViews() {
        when(cardRepository.findByDeckIdOrderBySortOrderAsc(101L)).thenReturn(List.of());
        when(cardRepository.save(any(Flashcard.class)))
                .thenAnswer(invocation -> {
                    Flashcard card = invocation.getArgument(0);
                    ReflectionTestUtils.setField(card, "id", 200L + card.getSortOrder());
                    return card;
                });

        List<CardView> result = service.replaceCards(
                101L,
                10L,
                List.of(
                        new CardItem(null, " 안녕하세요 ", " Hello "),
                        new CardItem(null, " 감사합니다 ", " Thank you ")
                )
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(200L);
        assertThat(result.get(0).front()).isEqualTo("안녕하세요");
        assertThat(result.get(0).back()).isEqualTo("Hello");
        assertThat(result.get(1).id()).isEqualTo(201L);
        verify(accessResolver).requireOwner(101L, 10L);
        verify(cardRepository).flush();
    }

    @Test
    void replaceCards_withExistingCard_updatesKeptCardAndDeletesRemovedCard() {
        Flashcard kept = card(301L, 101L, "old front", "old back", 0);
        Flashcard removed = card(302L, 101L, "remove front", "remove back", 1);

        when(cardRepository.findByDeckIdOrderBySortOrderAsc(101L))
                .thenReturn(List.of(kept, removed));
        when(cardRepository.save(kept)).thenReturn(kept);

        List<CardView> result = service.replaceCards(
                101L,
                10L,
                List.of(new CardItem(301L, " 새 단어 ", " New word "))
        );

        assertThat(result).hasSize(1);
        assertThat(kept.getFrontText()).isEqualTo("새 단어");
        assertThat(kept.getBackText()).isEqualTo("New word");
        verify(cardRepository).delete(removed);
        verify(cardRepository).flush();
    }

    @Test
    void replaceCards_withBlankFront_throwsIllegalArgumentExceptionAndDoesNotSave() {
        assertThatThrownBy(() -> service.replaceCards(
                101L,
                10L,
                List.of(new CardItem(null, " ", "Hello"))
        ))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accessResolver).requireOwner(101L, 10L);
        verify(cardRepository, never()).save(any(Flashcard.class));
        verify(cardRepository, never()).flush();
    }

    private static Flashcard card(
            Long id,
            Long deckId,
            String front,
            String back,
            int sortOrder
    ) {
        Flashcard card = new Flashcard(deckId, front, back, sortOrder);
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }
}
