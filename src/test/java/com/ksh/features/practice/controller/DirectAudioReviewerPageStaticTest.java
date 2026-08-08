package com.ksh.features.practice.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioReviewerPageStaticTest {

    @Test
    void pageIsDefaultOffSameOriginRangeOnlyAndNonScoreBearing() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/controller/"
                        + "DirectAudioReviewerPageController.java"));
        String template = Files.readString(Path.of(
                "src/main/resources/templates/practice/direct-audio-reviewer.html"));
        String css = Files.readString(Path.of(
                "src/main/resources/static/css/direct-audio-reviewer.css"));
        String properties = Files.readString(Path.of(
                "src/main/resources/application.properties"));

        assertThat(controller).contains("reviewer-page-enabled", "isAuthenticated()",
                        "observations", ".inspect(reviewerId",
                        "no-store, private, must-revalidate",
                        "scoreReleaseEligible")
                .doesNotContain("payloadJson()", "providerObservationTotal()",
                        "providerConfidence()", "holisticScore()", "attemptPoints()");
        assertThat(template).contains("preload=\"none\"", "reviewAudioPath",
                        "Không có điểm số", "không phát hành điểm")
                .doesNotContain("<script", "playbackUrl", "storageKey", "presign",
                        "providerObservationTotal", "providerConfidence",
                        "holisticScore", "attemptPoints");
        assertThat(css).contains("@media (max-width: 760px)",
                "overflow-wrap: anywhere", "prefers-reduced-motion");
        assertThat(properties).contains(
                "PRACTICE_SPEAKING_DIRECT_AUDIO_REVIEWER_PAGE_ENABLED:false");
    }
}
