package com.ksh.features.practice.controller;

import com.ksh.features.practice.ai.speaking.acoustic.DirectAudioDarkObservationCoordinator;
import com.ksh.features.practice.ai.speaking.acoustic.DirectAudioDarkObservationService;
import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackNotFoundException;
import com.ksh.features.practice.web.PracticeMediaRoutes;
import com.ksh.features.practice.web.PracticeRoutes;
import com.ksh.security.AuthenticatedUserIdResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;

/** Server-rendered reviewer-only shell; no provider values or scores enter the model. */
@Controller
@RequestMapping(PracticeRoutes.BASE)
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(
        prefix = "app.practice.speaking-direct-audio",
        name = "reviewer-page-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class DirectAudioReviewerPageController {
    private final DirectAudioDarkObservationCoordinator observations;
    private final AuthenticatedUserIdResolver userIdResolver;

    public DirectAudioReviewerPageController(
            DirectAudioDarkObservationCoordinator observations,
            AuthenticatedUserIdResolver userIdResolver) {
        this.observations = observations;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping(PracticeMediaRoutes.DIRECT_AUDIO_REVIEW_PAGE)
    public String page(
            @PathVariable Long attemptId,
            Authentication authentication,
            HttpServletResponse response,
            Model model) {
        Long reviewerId = userIdResolver.resolve(authentication);
        DirectAudioDarkObservationService.ReviewerView observation = observations
                .inspect(reviewerId, attemptId)
                .orElseThrow(PracticeSpeakingMediaPlaybackNotFoundException::new);
        ReviewerPageView view = ReviewerPageView.from(observation);
        response.setHeader("Cache-Control", "no-store, private, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        model.addAttribute("review", view);
        model.addAttribute("reviewAudioPath",
                PracticeMediaRoutes.directAudioReviewerPlaybackPath(
                        view.attemptId(), view.questionId(), view.mediaId()));
        return "practice/direct-audio-reviewer";
    }

    record ReviewerPageView(
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
        private static ReviewerPageView from(
                DirectAudioDarkObservationService.ReviewerView view) {
            return new ReviewerPageView(
                    view.attemptId(), view.questionId(), view.mediaId(),
                    view.contractVersion(), view.language(),
                    view.evaluatorId(), view.model(), view.calibrationProfileId(),
                    view.calibrationVersion(), view.completenessStatus().name(),
                    view.completenessReasonCode(), view.rejectedItemCount(),
                    view.capturedAt(), view.deleteAfter(), false);
        }
    }
}
