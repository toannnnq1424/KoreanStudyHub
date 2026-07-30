package com.ksh.features.flashcards.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.flashcards.dto.FlashcardDtos.CardView;
import com.ksh.features.flashcards.dto.FlashcardDtos.DeckDetailView;
import com.ksh.features.flashcards.service.DeckService;
import com.ksh.features.flashcards.service.FlashcardStudyService;
import com.ksh.features.flashcards.service.SmartReviewService;
import com.ksh.security.KshUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.ksh.common.IConstant.ATTR_CARDS_JSON;
import static com.ksh.common.IConstant.ATTR_DECK;
import static com.ksh.common.IConstant.ATTR_DUE_COUNT;
import static com.ksh.common.IConstant.BASE_FLASHCARDS;
import static com.ksh.common.IConstant.VIEW_FLASHCARD_FLIP;
import static com.ksh.common.IConstant.VIEW_FLASHCARD_LEARNING;
import static com.ksh.common.IConstant.VIEW_FLASHCARD_REVIEW;

/**
 * Study-mode pages for a deck: flip-through and SM-2 Smart Review. Cards are
 * serialized to JSON and passed via a {@code data-*} attribute so the client
 * renders user content with {@code textContent} (never {@code innerHTML}).
 */
@Controller
@RequestMapping(BASE_FLASHCARDS)
@PreAuthorize("isAuthenticated()")
public class FlashcardStudyController {

    private final FlashcardStudyService studyService;
    private final SmartReviewService smartReviewService;
    private final DeckService deckService;
    private final ObjectMapper objectMapper;

    public FlashcardStudyController(FlashcardStudyService studyService,
                                    SmartReviewService smartReviewService,
                                    DeckService deckService,
                                    ObjectMapper objectMapper) {
        this.studyService = studyService;
        this.smartReviewService = smartReviewService;
        this.deckService = deckService;
        this.objectMapper = objectMapper;
    }

    /** Flip study mode: all cards in order. */
    @GetMapping("/{id}/flip")
    public String flip(@PathVariable Long id,
                       @RequestParam(name = "mix", required = false) List<Long> mixedDeckIds,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model) {
        DeckDetailView deck = deckService.getDetail(id, user.getId());
        List<CardView> cards = populateStudySession(id, mixedDeckIds, user.getId(), deck, model);
        model.addAttribute(ATTR_DECK, deck);
        model.addAttribute(ATTR_CARDS_JSON, toJson(cards));
        return VIEW_FLASHCARD_FLIP;
    }

    /** Smart Review mode: only cards due now for the caller. */
    @GetMapping("/{id}/review")
    public String review(@PathVariable Long id,
                         @AuthenticationPrincipal KshUserDetails user,
                         Model model) {
        DeckDetailView deck = deckService.getDetail(id, user.getId());
        List<CardView> due = smartReviewService.getDueCards(id, user.getId());
        model.addAttribute(ATTR_DECK, deck);
        model.addAttribute(ATTR_DUE_COUNT, due.size());
        model.addAttribute(ATTR_CARDS_JSON, toJson(due));
        return VIEW_FLASHCARD_REVIEW;
    }

    /**
     * Client-only learning room. The server supplies the deck cards once; all
     * game state, scoring and timers intentionally stay in the browser and do
     * not create attempt/effort records.
     */
    @GetMapping("/{id}/{mode:learn|test|match|blast|tiles|word-search|word-connect}")
    public String learning(@PathVariable Long id,
                           @PathVariable String mode,
                           @RequestParam(name = "mix", required = false) List<Long> mixedDeckIds,
                           @AuthenticationPrincipal KshUserDetails user,
                           Model model) {
        DeckDetailView deck = deckService.getDetail(id, user.getId());
        List<CardView> cards = populateStudySession(id, mixedDeckIds, user.getId(), deck, model);
        model.addAttribute(ATTR_DECK, deck);
        model.addAttribute(ATTR_CARDS_JSON, toJson(cards));
        model.addAttribute("activeMode", mode);
        return VIEW_FLASHCARD_LEARNING;
    }

    /** Keeps old bookmarks working after replacing the Blocks prototype. */
    @GetMapping("/{id}/blocks")
    public String legacyBlocks(@PathVariable Long id) {
        return "redirect:/my/flashcards/" + id + "/tiles";
    }

    /** Builds one read-only, client-side study session from up to eight viewable decks. */
    private List<CardView> populateStudySession(Long primaryDeckId,
                                                List<Long> mixedDeckIds,
                                                Long userId,
                                                DeckDetailView primaryDeck,
                                                Model model) {
        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
        selectedIds.add(primaryDeckId);
        if (mixedDeckIds != null) {
            mixedDeckIds.stream()
                    .filter(deckId -> deckId != null && !deckId.equals(primaryDeckId))
                    .limit(7)
                    .forEach(selectedIds::add);
        }

        List<CardView> cards = new ArrayList<>();
        selectedIds.forEach(deckId -> cards.addAll(studyService.getStudyCards(deckId, userId)));
        var deckOptions = deckService.listStudyDeckOptions(userId);
        var sessionDecks = deckOptions.stream()
                .filter(summary -> selectedIds.contains(summary.id()))
                .toList();
        var mixedIds = selectedIds.stream().skip(1).toList();

        model.addAttribute("studyDeckOptions", deckOptions);
        model.addAttribute("studySelectedDeckIds", selectedIds);
        model.addAttribute("studyMixedDeckIds", mixedIds);
        model.addAttribute("studySessionDecks", sessionDecks);
        model.addAttribute("studySessionTitle", selectedIds.size() > 1
                ? selectedIds.size() + " bộ · " + cards.size() + " thuật ngữ"
                : primaryDeck.title());
        return cards;
    }

    /** Serializes the card list to a JSON string for the data attribute. */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Defensive: an empty array keeps the client renderer safe.
            return "[]";
        }
    }
}
