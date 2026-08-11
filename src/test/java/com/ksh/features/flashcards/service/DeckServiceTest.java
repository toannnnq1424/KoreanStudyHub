package com.ksh.features.flashcards.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.flashcards.dto.FlashcardDtos.DeckForm;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardDeckRepository;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeckServiceTest {

    private final FlashcardDeckRepository deckRepository = mock(FlashcardDeckRepository.class);
    private final FlashcardRepository cardRepository = mock(FlashcardRepository.class);
    private final DeckAccessResolver accessResolver = mock(DeckAccessResolver.class);
    private final DeckSummaryAssembler assembler = mock(DeckSummaryAssembler.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final ClassRepository classRepository = mock(ClassRepository.class);
    private final CardService cardService = mock(CardService.class);

    private final DeckService service = new DeckService(
            deckRepository,
            cardRepository,
            accessResolver,
            assembler,
            enrollmentRepository,
            classRepository,
            cardService
    );

    @Test
    void createDeck_withValidInput_returnsSavedDeckId() {
        when(deckRepository.save(any(FlashcardDeck.class)))
                .thenAnswer(invocation -> {
                    FlashcardDeck deck = invocation.getArgument(0);
                    ReflectionTestUtils.setField(deck, "id", 101L);
                    return deck;
                });

        Long result = service.createDeck(
                10L,
                new DeckForm("  Korean Vocabulary  ", "  Basic nouns  ")
        );

        assertThat(result).isEqualTo(101L);
    }

    @Test
    void updateMetadata_withOwnerDeck_updatesDeckAndSaves() {
        FlashcardDeck deck = deck(101L, 10L, "Old title", "Old description");
        when(accessResolver.requireOwner(101L, 10L)).thenReturn(deck);

        service.updateMetadata(
                101L,
                10L,
                new DeckForm("  Updated Korean Deck  ", "  Updated description  ")
        );

        assertThat(deck.getTitle()).isEqualTo("Updated Korean Deck");
        assertThat(deck.getDescription()).isEqualTo("Updated description");
        verify(deckRepository).save(deck);
    }

    @Test
    void share_withOwnerAndValidClass_setsDeckSharedAndSaves() {
        FlashcardDeck deck = deck(101L, 10L, "Korean Deck", null);
        Enrollment enrollment = mock(Enrollment.class);

        when(accessResolver.requireOwner(101L, 10L)).thenReturn(deck);
        when(enrollmentRepository.findByUserIdAndClassId(10L, 55L))
                .thenReturn(Optional.of(enrollment));
        when(enrollment.getStatus()).thenReturn(Enrollment.STATUS_ACTIVE);

        service.share(101L, 10L, 55L);

        assertThat(deck.isShared()).isTrue();
        assertThat(deck.getClassId()).isEqualTo(55L);
        verify(deckRepository).save(deck);
    }

    @Test
    void share_withClassNotOwnedOrEnrolled_throwsAccessDeniedException() {
        FlashcardDeck deck = deck(101L, 10L, "Korean Deck", null);

        when(accessResolver.requireOwner(101L, 10L)).thenReturn(deck);
        when(enrollmentRepository.findByUserIdAndClassId(10L, 55L))
                .thenReturn(Optional.empty());
        when(classRepository.findById(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.share(101L, 10L, 55L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static FlashcardDeck deck(Long id, Long ownerId, String title, String description) {
        FlashcardDeck deck = new FlashcardDeck(ownerId, title, description);
        ReflectionTestUtils.setField(deck, "id", id);
        return deck;
    }
}
