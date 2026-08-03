package com.ksh.features.flashcards.service;

import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardDeckRepository;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-controlled public-link lifecycle and anonymous token resolution. */
@Service
public class DeckPublicLinkService {

    private final FlashcardDeckRepository deckRepository;
    private final DeckAccessResolver accessResolver;
    private final FlashcardPublicTokenGenerator tokenGenerator;

    public DeckPublicLinkService(FlashcardDeckRepository deckRepository,
                                 DeckAccessResolver accessResolver,
                                 FlashcardPublicTokenGenerator tokenGenerator) {
        this.deckRepository = deckRepository;
        this.accessResolver = accessResolver;
        this.tokenGenerator = tokenGenerator;
    }

    /** Enables anonymous read-only access and returns the stable token. */
    @Transactional
    public String enable(Long deckId, Long ownerId) {
        FlashcardDeck deck = accessResolver.requireOwner(deckId, ownerId);
        deck.enablePublicLink(tokenGenerator.generate());
        return deckRepository.save(deck).getShareToken();
    }

    /** Disables anonymous access while retaining the token for later reuse. */
    @Transactional
    public void disable(Long deckId, Long ownerId) {
        FlashcardDeck deck = accessResolver.requireOwner(deckId, ownerId);
        deck.disablePublicLink();
        deckRepository.save(deck);
    }

    /** Invalidates every prior URL, enables access, and returns a fresh token. */
    @Transactional
    public String regenerate(Long deckId, Long ownerId) {
        FlashcardDeck deck = accessResolver.requireOwner(deckId, ownerId);
        deck.regeneratePublicToken(tokenGenerator.generate());
        deck.enablePublicLink(deck.getShareToken());
        return deckRepository.save(deck).getShareToken();
    }

    /** Resolves only enabled, non-deleted public decks without leaking state. */
    @Transactional(readOnly = true)
    public FlashcardDeck resolvePublic(String token) {
        if (token == null || !token.matches(FlashcardPublicTokenGenerator.TOKEN_REGEX)) {
            throw notFound();
        }
        FlashcardDeck deck = deckRepository.findByShareToken(token).orElseThrow(this::notFound);
        if (deck.isDeleted() || !deck.isPublicLink()) {
            throw notFound();
        }
        return deck;
    }

    private EntityNotFoundException notFound() {
        return new EntityNotFoundException(DeckAccessResolver.NF_MSG);
    }
}
