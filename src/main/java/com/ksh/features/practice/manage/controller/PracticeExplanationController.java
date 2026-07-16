package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService;
import com.ksh.security.KshUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/practice/manage/explanations")
public class PracticeExplanationController {

    private final QuestionExplanationRetryService retryService;

    public PracticeExplanationController(QuestionExplanationRetryService retryService) {
        this.retryService = retryService;
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
}
