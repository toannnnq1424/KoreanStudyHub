package com.ksh.features.flashcards.service;

import com.ksh.features.flashcards.dto.FlashcardDtos.CardItem;
import com.ksh.features.flashcards.dto.FlashcardDtos.CardView;
import com.ksh.features.flashcards.dto.FlashcardDtos.DeckEditorView;
import com.ksh.features.flashcards.entity.Flashcard;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static com.ksh.common.IConstant.MSG_CARD_SIDE_BLANK;

/**
 * Card bulk-save (full replace) + editor read (KSH-5.x).
 *
 * <p>The editor submits the whole card list; {@link #replaceCards} diffs it
 * against the deck's current cards in one transaction: kept ids are updated
 * in place (preserving their SM-2 review schedule), new rows are inserted, and
 * removed cards are deleted (their reviews cascade away).
 */
@Service
public class CardService {

    private final FlashcardRepository cardRepository;
    private final DeckAccessResolver accessResolver;

    public CardService(FlashcardRepository cardRepository,
                       DeckAccessResolver accessResolver) {
        this.cardRepository = cardRepository;
        this.accessResolver = accessResolver;
    }

    /** Editor view-model (owner-only): deck metadata + current cards. */
    @Transactional(readOnly = true)
    public DeckEditorView getEditorView(Long deckId, Long ownerId) {
        FlashcardDeck deck = accessResolver.requireOwner(deckId, ownerId);
        List<CardView> cards = new ArrayList<>();
        for (Flashcard c : cardRepository.findByDeckIdOrderBySortOrderAsc(deckId)) {
            cards.add(toView(c));
        }
        return new DeckEditorView(deck.getId(), deck.getTitle(), deck.getDescription(),
                cards, deck.isShared(), deck.getClassId(), List.of(), deck.getSubjectId());
    }

    /**
     * Replaces the deck's cards with the submitted set; owner-only. Validates
     * that every card has non-blank front AND back text; a blank side aborts
     * the whole save (existing cards left unchanged by the rolled-back tx).
     *
     * @throws IllegalArgumentException with {@code MSG_CARD_SIDE_BLANK} on a blank side
     */
    @Transactional
    public List<CardView> replaceCards(Long deckId, Long ownerId, List<CardItem> items) {
        accessResolver.requireOwner(deckId, ownerId);
        List<CardItem> submitted = items == null ? List.of() : items;
        // Validate ALL rows before any mutation so a blank side leaves the
        // existing cards untouched (atomic even when joined to an outer tx).
        for (CardItem item : submitted) {
            if (trim(item.front()).isEmpty() || trim(item.back()).isEmpty()) {
                throw new IllegalArgumentException(MSG_CARD_SIDE_BLANK);
            }
        }

        List<Flashcard> existing = cardRepository.findByDeckIdOrderBySortOrderAsc(deckId);
        Map<Long, Flashcard> byId = new HashMap<>();
        for (Flashcard c : existing) byId.put(c.getId(), c);

        Set<Long> keptIds = new HashSet<>();
        List<Flashcard> persisted = new ArrayList<>();
        int order = 0;
        for (CardItem item : submitted) {
            String front = trim(item.front());
            String back = trim(item.back());
            if (item.id() != null && byId.containsKey(item.id())) {
                Flashcard card = byId.get(item.id());
                card.updateRich(front, back, clean(item.frontImage()), clean(item.backImage()),
                        clean(item.alternativesJson()), order);
                persisted.add(cardRepository.save(card));
                keptIds.add(item.id());
            } else {
                Flashcard card = new Flashcard(deckId, front, back, order);
                card.updateRich(front, back, clean(item.frontImage()), clean(item.backImage()),
                        clean(item.alternativesJson()), order);
                persisted.add(cardRepository.save(card));
            }
            order++;
        }
        for (Flashcard c : existing) {
            if (!keptIds.contains(c.getId())) {
                cardRepository.delete(c);
            }
        }
        cardRepository.flush();
        return persisted.stream().map(CardService::toView).toList();
    }

    @Transactional
    public void setImage(Long cardId, Long ownerId, String side, String url) {
        Flashcard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thẻ"));
        accessResolver.requireOwner(card.getDeckId(), ownerId);
        if (!"front".equals(side) && !"back".equals(side)) {
            throw new IllegalArgumentException("Mặt thẻ không hợp lệ");
        }
        card.updateRich(card.getFrontText(), card.getBackText(),
                "front".equals(side) ? url : card.getFrontImage(),
                "back".equals(side) ? url : card.getBackImage(),
                card.getAlternativesJson(), card.getSortOrder());
        cardRepository.save(card);
    }

    private static CardView toView(Flashcard c) {
        return new CardView(c.getId(), c.getFrontText(), c.getBackText(),
                c.getFrontImage(), c.getBackImage(), c.getAlternativesJson());
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
