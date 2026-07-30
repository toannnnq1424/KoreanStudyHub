package com.ksh.features.discovery.controller;

import com.ksh.features.discovery.dto.DiscoveryLearningDtos.SaveVocabularyRequest;
import com.ksh.features.discovery.service.DiscoveryVocabularyLearningService;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.ksh.features.lessons.controller.support.AjaxResponses.badRequest;
import static com.ksh.features.lessons.controller.support.AjaxResponses.internalError;
import static com.ksh.features.lessons.controller.support.AjaxResponses.notFound;
import static com.ksh.features.lessons.dto.SectionDtos.AjaxResult;

@RestController
@RequestMapping(value = "/api/discovery", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
public class DiscoveryVocabularyController {

    private static final Logger log =
            LoggerFactory.getLogger(DiscoveryVocabularyController.class);
    private final DiscoveryVocabularyLearningService learningService;

    public DiscoveryVocabularyController(
            DiscoveryVocabularyLearningService learningService
    ) {
        this.learningService = learningService;
    }

    @GetMapping("/articles/{articleId}/dictionary")
    public ResponseEntity<?> lookup(
            @PathVariable Long articleId,
            @RequestParam("word") String word
    ) {
        try {
            return ResponseEntity.ok(AjaxResult.success(
                    learningService.lookup(articleId, word)
            ));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        } catch (EntityNotFoundException exception) {
            return notFound(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Failed to look up vocabulary for article {}", articleId, exception);
            return internalError();
        }
    }

    @PostMapping(
            value = "/articles/{articleId}/flashcards",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> save(
            @PathVariable Long articleId,
            @RequestBody SaveVocabularyRequest request,
            @AuthenticationPrincipal KshUserDetails user
    ) {
        try {
            return ResponseEntity.ok(AjaxResult.success(
                    learningService.save(articleId, user.getId(), request)
            ));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        } catch (EntityNotFoundException exception) {
            return notFound(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Failed to save vocabulary from article {}", articleId, exception);
            return internalError();
        }
    }
}
