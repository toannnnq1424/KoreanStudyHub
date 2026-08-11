package com.ksh.features.flashcards.service;

import com.ksh.features.flashcards.dto.FlashcardDtos.ReviewResult;
import com.ksh.features.flashcards.entity.Flashcard;
import com.ksh.features.flashcards.entity.FlashcardReview;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import com.ksh.features.flashcards.repository.FlashcardReviewRepository;
import com.ksh.features.flashcards.service.Sm2Scheduler.Sm2State;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartReviewServiceTest {

    private final FlashcardRepository cardRepository = mock(FlashcardRepository.class);
    private final FlashcardReviewRepository reviewRepository =
            mock(FlashcardReviewRepository.class);
    private final DeckAccessResolver accessResolver = mock(DeckAccessResolver.class);
    private final Sm2Scheduler scheduler = mock(Sm2Scheduler.class);

    private final SmartReviewService service = new SmartReviewService(
            cardRepository,
            reviewRepository,
            accessResolver,
            scheduler
    );

    @Test
    void recordRating_withNewReview_savesReviewAndReturnsRemainingDueAndInterval() {
        Flashcard card = new Flashcard(101L, "안녕하세요", "Hello", 0);
        Sm2State state = new Sm2State(2.6, 1, 1, LocalDateTime.now().plusDays(1));

        when(cardRepository.findById(201L)).thenReturn(Optional.of(card));
        when(cardRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(card));
        when(reviewRepository.findByUserIdAndFlashcardId(10L, 201L))
                .thenReturn(Optional.empty());
        when(scheduler.schedule(eq(5), eq(Sm2Scheduler.DEFAULT_EF), eq(0), eq(1), any()))
                .thenReturn(state);
        when(cardRepository.findDueCards(eq(101L), eq(10L), any()))
                .thenReturn(List.of(new Flashcard(101L, "감사합니다", "Thank you", 1)));

        ReviewResult result = service.recordRating(201L, 10L, 5);

        assertThat(result.dueRemaining()).isEqualTo(1);
        assertThat(result.intervalDays()).isEqualTo(1);
        verify(accessResolver).requireViewable(101L, 10L);
        verify(reviewRepository).save(any(FlashcardReview.class));
    }

    @Test
    void recordRating_withExistingReview_updatesReviewAndReturnsInterval() {
        Flashcard card = new Flashcard(101L, "안녕하세요", "Hello", 0);
        FlashcardReview existing = new FlashcardReview(
                10L,
                201L,
                4,
                2.5,
                1,
                1,
                LocalDateTime.now().plusDays(1)
        );
        Sm2State state = new Sm2State(2.6, 2, 6, LocalDateTime.now().plusDays(6));

        when(cardRepository.findById(201L)).thenReturn(Optional.of(card));
        when(cardRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(card));
        when(reviewRepository.findByUserIdAndFlashcardId(10L, 201L))
                .thenReturn(Optional.of(existing));
        when(scheduler.schedule(eq(4), eq(2.5), eq(1), eq(1), any()))
                .thenReturn(state);
        when(cardRepository.findDueCards(eq(101L), eq(10L), any())).thenReturn(List.of());

        ReviewResult result = service.recordRating(201L, 10L, 4);

        assertThat(result.dueRemaining()).isZero();
        assertThat(result.intervalDays()).isEqualTo(6);
        assertThat(existing.getQuality()).isEqualTo(4);
        assertThat(existing.getIntervalDays()).isEqualTo(6);
        verify(reviewRepository).save(existing);
    }

    @Test
    void recordRating_withQualityOutsideZeroToFive_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.recordRating(201L, 10L, 6))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cardRepository, never()).findById(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void recordRating_withUnknownCard_throwsEntityNotFoundException() {
        when(cardRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordRating(999L, 10L, 5))
                .isInstanceOf(EntityNotFoundException.class);

        verify(reviewRepository, never()).save(any());
    }
}
