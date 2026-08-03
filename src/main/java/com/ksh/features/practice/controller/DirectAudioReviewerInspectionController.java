package com.ksh.features.practice.controller;

import com.ksh.features.practice.ai.speaking.acoustic.DirectAudioDarkObservationCoordinator;
import com.ksh.features.practice.ai.speaking.acoustic.DirectAudioDarkObservationService;
import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackNotFoundException;
import com.ksh.features.practice.web.PracticeMediaRoutes;
import com.ksh.features.practice.web.PracticeRoutes;
import com.ksh.security.AuthenticatedUserIdResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Metadata-only reviewer inspection; provider observations and scores stay server-side. */
@RestController
@RequestMapping(PracticeRoutes.BASE)
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(
        prefix = "app.practice.speaking-direct-audio",
        name = "reviewer-inspection-api-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class DirectAudioReviewerInspectionController {
    private static final CacheControl NO_STORE = CacheControl.maxAge(0, TimeUnit.SECONDS)
            .noStore().cachePrivate().mustRevalidate();

    private final DirectAudioDarkObservationCoordinator observations;
    private final AuthenticatedUserIdResolver userIdResolver;

    public DirectAudioReviewerInspectionController(
            DirectAudioDarkObservationCoordinator observations,
            AuthenticatedUserIdResolver userIdResolver) {
        this.observations = observations;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping(PracticeMediaRoutes.DIRECT_AUDIO_REVIEW_LATEST_OBSERVATION)
    public ResponseEntity<ReviewerObservationMetadata> latest(
            @PathVariable Long attemptId, Authentication authentication) {
        Long reviewerId = userIdResolver.resolve(authentication);
        DirectAudioDarkObservationService.ReviewerView view = observations
                .inspect(reviewerId, attemptId)
                .orElseThrow(PracticeSpeakingMediaPlaybackNotFoundException::new);
        return ResponseEntity.ok().cacheControl(NO_STORE).body(
                ReviewerObservationMetadata.from(view));
    }

    public record ReviewerObservationMetadata(
            String observationKey,
            Long attemptId,
            Long questionId,
            Long mediaId,
            String contractVersion,
            String language,
            String evaluatorId,
            String model,
            String calibrationProfileId,
            String calibrationVersion,
            String completenessStatus,
            String completenessReasonCode,
            int rejectedItemCount,
            Instant capturedAt,
            Instant deleteAfter,
            boolean scoreReleaseEligible) {
        private static ReviewerObservationMetadata from(
                DirectAudioDarkObservationService.ReviewerView view) {
            return new ReviewerObservationMetadata(
                    view.observationKey(), view.attemptId(), view.questionId(), view.mediaId(),
                    view.contractVersion(), view.language(), view.evaluatorId(), view.model(),
                    view.calibrationProfileId(), view.calibrationVersion(),
                    view.completenessStatus().name(), view.completenessReasonCode(),
                    view.rejectedItemCount(), view.capturedAt(), view.deleteAfter(), false);
        }
    }
}
