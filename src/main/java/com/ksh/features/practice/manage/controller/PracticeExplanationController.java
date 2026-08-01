package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService;
import com.ksh.features.practice.ai.readinglistening.ObjectiveExplanationEditorialService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/practice/manage/explanations")
@PreAuthorize(Roles.PREAUTH_LECTURER)
public class PracticeExplanationController {

    private final QuestionExplanationRetryService retryService;
    private final ObjectiveExplanationEditorialService editorialService;

    @Autowired
    public PracticeExplanationController(
            QuestionExplanationRetryService retryService,
            ObjectiveExplanationEditorialService editorialService) {
        this.retryService = retryService;
        this.editorialService = editorialService;
    }

    PracticeExplanationController(
            QuestionExplanationRetryService retryService) {
        this(retryService, null);
    }

    @PostMapping("/{artifactId}/retry")
    public ResponseEntity<Map<String, Object>> retry(
            @PathVariable Long artifactId,
            @AuthenticationPrincipal KshUserDetails user) {
        QuestionExplanationRetryService.RetryResult result =
                retryService.retry(artifactId, user.getId());
        Map<String, Object> body = Map.of(
                "artifactId", artifactId,
                "status", result.status(),
                "queued", result.queued(),
                "message", result.message());
        if ("RATE_LIMITED".equals(result.status())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER,
                            String.valueOf(result.retryAfterSeconds()))
                    .body(body);
        }
        if ("NOT_RETRYABLE".equals(result.status())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(body);
        }
        return result.queued()
                ? ResponseEntity.accepted().body(body)
                : ResponseEntity.ok(body);
    }

    @PostMapping("/drafts/{draftId}/questions/{questionClientId}/generate")
    public ResponseEntity<ObjectiveExplanationEditorialService.EditorialView>
            generateDraft(
                    @PathVariable Long draftId,
                    @PathVariable String questionClientId,
                    @AuthenticationPrincipal KshUserDetails user) {
        requireEditorialService();
        return ResponseEntity.accepted().body(
                editorialService.generateDraft(
                        draftId, questionClientId, user.getId()));
    }

    @GetMapping("/drafts/{draftId}/questions/{questionClientId}/current")
    public ResponseEntity<ObjectiveExplanationEditorialService.EditorialView>
            current(
                    @PathVariable Long draftId,
                    @PathVariable String questionClientId,
                    @AuthenticationPrincipal KshUserDetails user) {
        requireEditorialService();
        return ResponseEntity.of(editorialService.current(
                draftId, questionClientId, user.getId()));
    }

    @PutMapping("/drafts/{draftId}/questions/{questionClientId}/revisions")
    public ResponseEntity<ObjectiveExplanationEditorialService.EditorialView>
            saveEditedDraft(
                    @PathVariable Long draftId,
                    @PathVariable String questionClientId,
                    @RequestBody EditorialEditRequest request,
                    @AuthenticationPrincipal KshUserDetails user) {
        requireEditorialService();
        if (request == null
                || request.explanationJson() == null
                || request.explanationJson().isBlank()) {
            throw new IllegalArgumentException(
                    "Nội dung lời giải typed là bắt buộc.");
        }
        return ResponseEntity.ok(editorialService.saveEditedDraft(
                draftId,
                questionClientId,
                request.explanationJson(),
                user.getId()));
    }

    @PostMapping(
            "/drafts/{draftId}/questions/{questionClientId}/revisions/{revisionId}/approve")
    public ResponseEntity<ObjectiveExplanationEditorialService.EditorialView>
            approve(
                    @PathVariable Long draftId,
                    @PathVariable String questionClientId,
                    @PathVariable Long revisionId,
                    @AuthenticationPrincipal KshUserDetails user) {
        requireEditorialService();
        return ResponseEntity.ok(editorialService.approve(
                draftId, questionClientId, revisionId, user.getId()));
    }

    private void requireEditorialService() {
        if (editorialService == null) {
            throw new IllegalStateException(
                    "Editorial explanation service is unavailable.");
        }
    }

    public record EditorialEditRequest(String explanationJson) {
    }
}
