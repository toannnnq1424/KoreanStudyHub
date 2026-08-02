package com.ksh.features.flashcards.service;

import com.ksh.features.classes.service.invites.InviteTokenGenerator;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardDeckRepository;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class DeckPublicLinkServiceTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEF";

    private FlashcardDeckRepository repository;
    private DeckAccessResolver accessResolver;
    private InviteTokenGenerator tokenGenerator;
    private DeckPublicLinkService service;
    private FlashcardDeck deck;

    @BeforeEach
    void setUp() {
        repository = mock(FlashcardDeckRepository.class);
        accessResolver = mock(DeckAccessResolver.class);
        tokenGenerator = mock(InviteTokenGenerator.class);
        service = new DeckPublicLinkService(repository, accessResolver, tokenGenerator);
        deck = new FlashcardDeck(7L, "Korean", null);
        when(accessResolver.requireOwner(11L, 7L)).thenReturn(deck);
        when(tokenGenerator.generateLink()).thenReturn(TOKEN);
        when(repository.save(any(FlashcardDeck.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enableKeepsStableTokenAcrossDisableAndReenable() {
        assertThat(service.enable(11L, 7L)).isEqualTo(TOKEN);
        service.disable(11L, 7L);
        when(tokenGenerator.generateLink()).thenReturn("0123456789abcdefghijklmnopqrstuv");

        assertThat(service.enable(11L, 7L)).isEqualTo(TOKEN);
        assertThat(deck.isPublicLink()).isTrue();
    }

    @Test
    void regenerateInvalidatesOldTokenAndLeavesLinkEnabled() {
        service.enable(11L, 7L);
        String replacement = "0123456789abcdefghijklmnopqrstuv";
        when(tokenGenerator.generateLink()).thenReturn(replacement);

        assertThat(service.regenerate(11L, 7L)).isEqualTo(replacement);
        assertThat(deck.isPublicLink()).isTrue();
    }

    @Test
    void publicResolutionRejectsMalformedUnknownAndDisabledTokens() {
        assertThatThrownBy(() -> service.resolvePublic("short"))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.resolvePublic(TOKEN))
                .isInstanceOf(EntityNotFoundException.class);

        deck.enablePublicLink(TOKEN);
        when(repository.findByShareToken(TOKEN)).thenReturn(Optional.of(deck));
        assertThat(service.resolvePublic(TOKEN)).isSameAs(deck);

        deck.disablePublicLink();
        assertThatThrownBy(() -> service.resolvePublic(TOKEN))
                .isInstanceOf(EntityNotFoundException.class);
        verify(repository, times(3)).findByShareToken(TOKEN);
    }
}
