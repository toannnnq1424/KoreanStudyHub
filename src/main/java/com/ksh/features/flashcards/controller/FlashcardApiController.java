package com.ksh.features.flashcards.controller;

import com.ksh.features.flashcards.dto.FlashcardDtos;
import com.ksh.features.flashcards.dto.FlashcardDtos.CreateDeckRequest;
import com.ksh.features.flashcards.dto.FlashcardDtos.DeckForm;
import com.ksh.features.flashcards.dto.FlashcardDtos.DeckSaveResult;
import com.ksh.features.flashcards.dto.FlashcardDtos.ImportResult;
import com.ksh.features.flashcards.dto.FlashcardDtos.ImportedCardRow;
import com.ksh.features.flashcards.dto.FlashcardDtos.ReviewRatingRequest;
import com.ksh.features.flashcards.dto.FlashcardDtos.ReviewResult;
import com.ksh.features.flashcards.dto.FlashcardDtos.SaveCardsRequest;
import com.ksh.features.flashcards.imports.FlashcardImportParser;
import com.ksh.features.flashcards.imports.FlashcardImportTemplate;
import com.ksh.features.flashcards.service.CardService;
import com.ksh.features.flashcards.service.DeckService;
import com.ksh.features.flashcards.service.SmartReviewService;
import com.ksh.features.flashcards.service.FlashcardImageStorageService;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static com.ksh.common.IConstant.API_FLASHCARDS;
import static com.ksh.features.lessons.controller.support.AjaxResponses.badRequest;
import static com.ksh.features.lessons.controller.support.AjaxResponses.forbidden;
import static com.ksh.features.lessons.controller.support.AjaxResponses.internalError;
import static com.ksh.features.lessons.controller.support.AjaxResponses.notFound;
import static com.ksh.features.lessons.dto.SectionDtos.AjaxResult;

/**
 * JSON API for flashcards under {@code /api/flashcards}: bulk card save,
 * Smart-Review rating submit, and Excel import.
 *
 * <p>All authorization lives in the services / {@code DeckAccessResolver}; this
 * controller only maps exceptions onto the shared {@link AjaxResult} envelope
 * ({@link IllegalArgumentException} → 400, {@link AccessDeniedException} → 403,
 * {@link EntityNotFoundException} → 404, anything else → 500).
 */
@RestController
@RequestMapping(value = API_FLASHCARDS, produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
public class FlashcardApiController {

    private static final Logger log = LoggerFactory.getLogger(FlashcardApiController.class);

    // XLSX MIME type + template filename for the download endpoint.
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TEMPLATE_FILENAME = "flashcard-import-mau.xlsx";

    private final CardService cardService;
    private final SmartReviewService smartReviewService;
    private final DeckAccessResolver accessResolver;
    private final FlashcardImportParser importParser;
    private final FlashcardImportTemplate importTemplate;
    private final FlashcardImageStorageService imageStorage;
    private final FlashcardRepository cardRepository;
    private final DeckService deckService;

    public FlashcardApiController(CardService cardService,
                                  SmartReviewService smartReviewService,
                                  DeckAccessResolver accessResolver,
                                  FlashcardImportParser importParser,
                                  FlashcardImportTemplate importTemplate,
                                  FlashcardImageStorageService imageStorage,
                                  FlashcardRepository cardRepository,
                                  DeckService deckService) {
        this.cardService = cardService;
        this.smartReviewService = smartReviewService;
        this.accessResolver = accessResolver;
        this.importParser = importParser;
        this.importTemplate = importTemplate;
        this.imageStorage = imageStorage;
        this.cardRepository = cardRepository;
        this.deckService = deckService;
    }

    /**
     * Creates a deck and its initial cards without navigating away. The ordered
     * card ids let the editor upload locally-previewed images only after the
     * card rows exist.
     */
    @PostMapping(value = "/decks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDeck(@RequestBody CreateDeckRequest req,
                                        @AuthenticationPrincipal KshUserDetails user) {
        try {
            validateDeckRequest(req);
            List<FlashcardDtos.CardItem> items = req.cards() == null ? List.of() : req.cards();
            DeckSaveResult saved = deckService.createDeckWithCards(user.getId(),
                    new DeckForm(req.title().trim(), req.description()), items);
            return ResponseEntity.ok(AjaxResult.success(saved));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to create flashcard deck", ex);
            return internalError();
        }
    }

    @PostMapping(value = "/cards/{cardId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadImage(@PathVariable Long cardId,
                                         @RequestParam("side") String side,
                                         @RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal KshUserDetails user) {
        try {
            // Resolve ownership before accepting bytes into object storage.
            var card = cardRepository.findById(cardId)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thẻ"));
            accessResolver.requireOwner(card.getDeckId(), user.getId());
            String url = imageStorage.store(cardId, side, file);
            cardService.setImage(cardId, user.getId(), side, url);
            return ResponseEntity.ok(AjaxResult.success(url));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (AccessDeniedException ex) {
            return forbidden();
        } catch (EntityNotFoundException ex) {
            return notFound(ex.getMessage());
        } catch (IOException ex) {
            return internalError();
        }
    }

    /** Replaces the deck's cards with the submitted set; owner-only. */
    @PostMapping(value = "/{deckId}/cards", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveCards(@PathVariable Long deckId,
                                       @RequestBody SaveCardsRequest req,
                                       @AuthenticationPrincipal KshUserDetails user) {
        try {
            List<FlashcardDtos.CardItem> items = req.cards() == null ? List.of() : req.cards();
            return ResponseEntity.ok(AjaxResult.success(
                    cardService.replaceCards(deckId, user.getId(), items)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (AccessDeniedException ex) {
            return forbidden();
        } catch (EntityNotFoundException ex) {
            return notFound(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to save cards for deck {}", deckId, ex);
            return internalError();
        }
    }

    /** Records a recall rating for a card and upserts the user's SM-2 state. */
    @PostMapping(value = "/cards/{cardId}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> review(@PathVariable Long cardId,
                                    @RequestBody ReviewRatingRequest req,
                                    @AuthenticationPrincipal KshUserDetails user) {
        try {
            ReviewResult result = smartReviewService.recordRating(cardId, user.getId(), req.quality());
            return ResponseEntity.ok(AjaxResult.success(result));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (AccessDeniedException ex) {
            return forbidden();
        } catch (EntityNotFoundException ex) {
            return notFound(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to record review for card {}", cardId, ex);
            return internalError();
        }
    }

    /**
     * Parses an uploaded Excel file into card rows for editor review; owner-only.
     * Does NOT persist — the JS appends the rows and the existing save flow
     * commits them once the user confirms.
     */
    @PostMapping(value = "/import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> previewExcel(@RequestParam("file") MultipartFile file) {
        try {
            List<ImportedCardRow> rows = importParser.parse(file);
            return ResponseEntity.ok(AjaxResult.success(new ImportResult(rows, rows.size())));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to preview flashcard Excel import", ex);
            return internalError();
        }
    }

    @PostMapping(value = "/{deckId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importExcel(@PathVariable Long deckId,
                                         @RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal KshUserDetails user) {
        try {
            // Owner-only gate before parsing the upload.
            accessResolver.requireOwner(deckId, user.getId());
            List<ImportedCardRow> rows = importParser.parse(file);
            return ResponseEntity.ok(AjaxResult.success(new ImportResult(rows, rows.size())));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (AccessDeniedException ex) {
            return forbidden();
        } catch (EntityNotFoundException ex) {
            return notFound(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to import Excel for deck {}", deckId, ex);
            return internalError();
        }
    }

    /** Streams the two-column .xlsx import template; any authenticated user. */
    @GetMapping(value = "/import-template", produces = XLSX_MIME)
    public ResponseEntity<byte[]> importTemplate() {
        try {
            byte[] bytes = importTemplate.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(XLSX_MIME));
            headers.setContentDispositionFormData("attachment", TEMPLATE_FILENAME);
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (IOException ex) {
            log.error("Failed to generate flashcard import template", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static void validateDeckRequest(CreateDeckRequest req) {
        if (req == null || req.title() == null || req.title().isBlank()) {
            throw new IllegalArgumentException("Tiêu đề không được để trống");
        }
        if (req.title().trim().length() > 300) {
            throw new IllegalArgumentException("Tiêu đề tối đa 300 ký tự");
        }
        if (req.description() != null && req.description().length() > 2000) {
            throw new IllegalArgumentException("Mô tả tối đa 2000 ký tự");
        }
    }
}
