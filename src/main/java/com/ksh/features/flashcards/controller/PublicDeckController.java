package com.ksh.features.flashcards.controller;

import com.ksh.features.flashcards.dto.FlashcardDtos.CardView;
import com.ksh.features.flashcards.dto.FlashcardDtos.PublicDeckView;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import com.ksh.features.flashcards.service.DeckPublicLinkService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.ksh.common.IConstant.ATTR_CARDS;
import static com.ksh.common.IConstant.ATTR_DECK;
import static com.ksh.common.IConstant.PATH_PUBLIC_DECK;
import static com.ksh.common.IConstant.VIEW_PUBLIC_DECK;

/** Anonymous, read-only flashcard deck view protected by a high-entropy token. */
@Controller
public class PublicDeckController {

    private final DeckPublicLinkService publicLinkService;
    private final FlashcardRepository cardRepository;

    public PublicDeckController(DeckPublicLinkService publicLinkService,
                                FlashcardRepository cardRepository) {
        this.publicLinkService = publicLinkService;
        this.cardRepository = cardRepository;
    }

    @GetMapping(PATH_PUBLIC_DECK + "/{token}")
    public String view(@PathVariable String token, Model model) {
        FlashcardDeck deck;
        try {
            deck = publicLinkService.resolvePublic(token);
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<CardView> cards = cardRepository.findByDeckIdOrderBySortOrderAsc(deck.getId())
                .stream()
                .map(card -> new CardView(card.getId(), card.getFrontText(), card.getBackText(),
                        card.getFrontImage(), card.getBackImage(), card.getAlternativesJson()))
                .toList();
        model.addAttribute(ATTR_DECK,
                new PublicDeckView(deck.getTitle(), deck.getDescription(), cards.size()));
        model.addAttribute(ATTR_CARDS, cards);
        return VIEW_PUBLIC_DECK;
    }
}
