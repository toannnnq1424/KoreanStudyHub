package com.ksh.features.dictionary;

import com.ksh.features.dictionary.KoreanDictionaryDtos.DeckOptions;
import com.ksh.features.dictionary.KoreanDictionaryDtos.SaveRequest;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.ksh.features.lessons.controller.support.AjaxResponses.badRequest;
import static com.ksh.features.lessons.controller.support.AjaxResponses.notFound;
import static com.ksh.features.lessons.dto.SectionDtos.AjaxResult;

@RestController
@RequestMapping("/api/korean-dictionary")
@PreAuthorize("isAuthenticated()")
public class KoreanDictionaryController {
    private final KoreanDictionaryLearningService service;

    public KoreanDictionaryController(KoreanDictionaryLearningService service) {
        this.service = service;
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam("word") String word) {
        try {
            return ResponseEntity.ok(AjaxResult.success(service.lookup(word)));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    @GetMapping("/decks")
    public ResponseEntity<?> decks(@AuthenticationPrincipal KshUserDetails user) {
        return ResponseEntity.ok(AjaxResult.success(new DeckOptions(service.decks(user.getId()))));
    }

    @PostMapping("/flashcards")
    public ResponseEntity<?> save(@RequestBody SaveRequest request,
                                  @AuthenticationPrincipal KshUserDetails user) {
        try {
            return ResponseEntity.ok(AjaxResult.success(service.save(user.getId(), request)));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        } catch (EntityNotFoundException exception) {
            return notFound(exception.getMessage());
        }
    }
}
