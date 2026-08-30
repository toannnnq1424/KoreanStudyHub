package com.ksh.features.flashcards.support;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardDeckRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeckAccessResolverTest {

    @Test
    void activeEnrollmentCannotViewSharedDeckAfterClassDeletion() {
        FlashcardDeckRepository decks = mock(FlashcardDeckRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        ClassRepository classes = mock(ClassRepository.class);
        FlashcardDeck deck = mock(FlashcardDeck.class);
        Enrollment enrollment = mock(Enrollment.class);
        ClassEntity deletedClass = mock(ClassEntity.class);

        when(decks.findById(11L)).thenReturn(Optional.of(deck));
        when(deck.isDeleted()).thenReturn(false);
        when(deck.getOwnerId()).thenReturn(99L);
        when(deck.isShared()).thenReturn(true);
        when(deck.getClassId()).thenReturn(13L);
        when(deletedClass.isDeleted()).thenReturn(true);
        when(classes.findById(13L)).thenReturn(Optional.of(deletedClass));
        when(enrollment.getStatus()).thenReturn(Enrollment.STATUS_ACTIVE);
        when(enrollments.findByUserIdAndClassId(7L, 13L))
                .thenReturn(Optional.of(enrollment));

        DeckAccessResolver resolver = new DeckAccessResolver(decks, enrollments, classes);

        assertThatThrownBy(() -> resolver.requireViewable(11L, 7L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage(DeckAccessResolver.NF_MSG);
        verifyNoInteractions(enrollments);
    }
}
