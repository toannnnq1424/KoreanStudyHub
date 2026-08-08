package com.ksh.features.practice.controller;

import com.ksh.entities.User;
import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;
import com.ksh.features.practice.ai.speaking.acoustic.DirectAudioDarkObservationCoordinator;
import com.ksh.features.practice.ai.speaking.acoustic.DirectAudioDarkObservationService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectAudioReviewerInspectionControllerTest {

    @Test
    void metadataResponseOmitsProviderObservationsAggregatesAndEveryScoreField() {
        DirectAudioDarkObservationCoordinator observations =
                mock(DirectAudioDarkObservationCoordinator.class);
        Instant captured = Instant.parse("2026-08-03T10:00:00Z");
        when(observations.inspect(77L, 44L)).thenReturn(Optional.of(
                new DirectAudioDarkObservationService.ReviewerView(
                        "observation-0001", 44L, 55L, 66L,
                        "ksh-speaking-direct-audio-acoustic-v1", "ko-KR",
                        "TEST-EVALUATOR", "test-model", "TEST-CALIBRATION", "TEST-V1",
                        new BigDecimal("1.50"), new BigDecimal("0.85"),
                        "PROVIDER_NUMERIC_PAYLOAD_MUST_NOT_ESCAPE", captured,
                        captured.plusSeconds(3600),
                        PracticeAiResultCompleteness.Status.PARTIAL_NON_SCORE,
                        "DIRECT_AUDIO_DIAGNOSTIC_ITEMS_REJECTED", 1,
                        false, null, null)));
        DirectAudioReviewerInspectionController controller =
                new DirectAudioReviewerInspectionController(
                        observations, new AuthenticatedUserIdResolver());

        var response = controller.latest(44L, authentication(77L));
        var body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.completenessStatus()).isEqualTo("PARTIAL_NON_SCORE");
        assertThat(body.scoreReleaseEligible()).isFalse();
        assertThat(body.toString())
                .doesNotContain("PROVIDER_NUMERIC_PAYLOAD_MUST_NOT_ESCAPE", "1.50", "0.85");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store", "private");
        verify(observations).inspect(77L, 44L);
    }

    private static UsernamePasswordAuthenticationToken authentication(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getRole()).thenReturn(Role.STUDENT);
        when(user.getEmail()).thenReturn("reviewer@example.test");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn("Reviewer");
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        KshUserDetails principal = new KshUserDetails(user);
        return new UsernamePasswordAuthenticationToken(
                principal, "N/A", principal.getAuthorities());
    }
}
