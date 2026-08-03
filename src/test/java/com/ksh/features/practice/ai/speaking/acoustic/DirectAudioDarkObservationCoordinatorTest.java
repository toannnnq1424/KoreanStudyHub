package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectAudioDarkObservationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");

    @Test
    void delegatesToStoreEnforcedReviewerConsentGrantAndReturnsValidatedMetadata() {
        DirectAudioDarkObservationJdbcStore store = mock(DirectAudioDarkObservationJdbcStore.class);
        when(store.findInspectable(77L, 44L, NOW)).thenReturn(Optional.of(
                new DirectAudioDarkObservationService.StoredObservation(
                        "observation-0001", 44L, 55L, 66L,
                        DirectAudioAcousticResponseNormalizer.CONTRACT_VERSION, "ko-KR",
                        "TEST-EVALUATOR", "test-model", "TEST-CALIBRATION", "TEST-V1",
                        "a".repeat(64), "b".repeat(64), new BigDecimal("1.50"),
                        new BigDecimal("0.85"),
                        "{\"result_completeness\":{\"version\":\"practice-ai-result-completeness-v1\",\"status\":\"COMPLETE\",\"reason_code\":\"NONE\",\"rejected_item_count\":0},\"observations\":[]}",
                        DirectAudioDarkObservationService.RETENTION_POLICY_ID,
                        NOW.minusSeconds(60), NOW.plusSeconds(3600))));
        DirectAudioDarkObservationCoordinator coordinator =
                new DirectAudioDarkObservationCoordinator(store, new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC));

        var view = coordinator.inspect(77L, 44L).orElseThrow();

        assertThat(view.questionId()).isEqualTo(55L);
        assertThat(view.mediaId()).isEqualTo(66L);
        assertThat(view.scoreReleaseEligible()).isFalse();
        assertThat(view.holisticScore()).isNull();
        assertThat(view.attemptPoints()).isNull();
    }
}
