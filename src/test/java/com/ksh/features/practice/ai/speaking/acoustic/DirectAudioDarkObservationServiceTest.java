package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectAudioDarkObservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");
    private final AtomicReference<DirectAudioDarkObservationService.StoredObservation>
            stored = new AtomicReference<>();
    private boolean reviewerAuthorized;
    private final DirectAudioDarkObservationService service =
            new DirectAudioDarkObservationService(new FakeStore(), new ObjectMapper(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void captureStoresOnlyBoundedProjectionAndOpaqueFingerprints() {
        var captured = service.capture(44L, "dark-observation-0001", valid(),
                NOW.plusSeconds(3600));

        assertThat(captured.receiptFingerprint()).hasSize(64)
                .doesNotContain("provider-request-test-1");
        assertThat(captured.providerCacheFingerprint()).hasSize(64)
                .doesNotContain("provider-cache-test-1");
        assertThat(captured.payloadJson())
                .contains("PRONUNCIATION", "FLUENCY", "evidence-test-1")
                .doesNotContain("provider observation must never persist")
                .doesNotContain("provider-request-test-1", "provider-cache-test-1")
                .doesNotContain("holistic", "attemptPoints", "scoreRelease");
        assertThat(captured.retentionPolicyId()).isEqualTo(
                DirectAudioDarkObservationService.RETENTION_POLICY_ID);
    }

    @Test
    void inspectionRequiresStoreEnforcedReviewerGrantAndNeverReturnsScores() {
        service.capture(44L, "dark-observation-0001", valid(),
                NOW.plusSeconds(3600));

        assertThat(service.inspect(77L, 44L)).isEmpty();
        reviewerAuthorized = true;
        var view = service.inspect(77L, 44L).orElseThrow();

        assertThat(view.scoreReleaseEligible()).isFalse();
        assertThat(view.holisticScore()).isNull();
        assertThat(view.attemptPoints()).isNull();
        assertThat(view.providerObservationTotal())
                .isEqualByComparingTo("1.50");
        assertThat(view.completenessStatus()).isEqualTo(
                PracticeAiResultCompleteness.Status.COMPLETE);
        assertThat(view.rejectedItemCount()).isZero();
    }

    @Test
    void rejectedResultAndOutOfPolicyRetentionNeverReachStore() {
        assertThatThrownBy(() -> service.capture(44L, "dark-observation-0001",
                DirectAudioAcousticObservationResult.rejected("TEST"),
                NOW.plusSeconds(3600)))
                .hasMessage("DIRECT_AUDIO_DARK_OBSERVATION_INVALID");
        assertThatThrownBy(() -> service.capture(44L, "dark-observation-0002",
                valid(), NOW.plus(DirectAudioDarkObservationService.RETENTION_CEILING)
                        .plusSeconds(1)))
                .hasMessage("DIRECT_AUDIO_DARK_RETENTION_INVALID");
        assertThat(stored.get()).isNull();
    }

    @Test
    void reviewerCanInspectPersistedPartialDiagnosticsWithoutAnyScoreRelease() {
        service.capture(44L, "dark-observation-partial", partial(),
                NOW.plusSeconds(3600));
        reviewerAuthorized = true;

        var view = service.inspect(77L, 44L).orElseThrow();

        assertThat(view.completenessStatus()).isEqualTo(
                PracticeAiResultCompleteness.Status.PARTIAL_NON_SCORE);
        assertThat(view.completenessReasonCode()).isEqualTo(
                "DIRECT_AUDIO_DIAGNOSTIC_ITEMS_REJECTED");
        assertThat(view.rejectedItemCount()).isEqualTo(1);
        assertThat(view.scoreReleaseEligible()).isFalse();
        assertThat(view.holisticScore()).isNull();
        assertThat(view.attemptPoints()).isNull();
    }

    private static DirectAudioAcousticObservationResult valid() {
        return new DirectAudioAcousticObservationResult(
                DirectAudioAcousticObservationResult.State.VALID_DARK_OBSERVATION,
                DirectAudioAcousticResponseNormalizer.CONTRACT_VERSION,
                DirectAudioAcousticResponseNormalizer.LANGUAGE,
                "TEST-EVALUATOR", "test-model", "TEST-CALIBRATION", "TEST-V1",
                List.of(
                        dimension(DirectAudioAcousticObservationResult.Dimension
                                .PRONUNCIATION, "evidence-test-1", "0.80", "0.90"),
                        dimension(DirectAudioAcousticObservationResult.Dimension
                                .FLUENCY, "evidence-test-2", "0.70", "0.80")),
                new BigDecimal("1.50"), new BigDecimal("0.85"),
                "provider-request-test-1", "provider-cache-test-1", null,
                PracticeAiResultCompleteness.complete(),
                false, false, null, null);
    }

    private static DirectAudioAcousticObservationResult partial() {
        DirectAudioAcousticObservationResult complete = valid();
        return new DirectAudioAcousticObservationResult(
                complete.state(), complete.contractVersion(), complete.language(),
                complete.evaluatorId(), complete.model(),
                complete.calibrationProfileId(), complete.calibrationVersion(),
                complete.observations(), complete.providerObservationTotal(),
                complete.providerConfidence(), complete.providerRequestId(),
                complete.providerCacheIdentity(), null,
                PracticeAiResultCompleteness.partial(
                        "DIRECT_AUDIO_DIAGNOSTIC_ITEMS_REJECTED", 1),
                false, false, null, null);
    }

    private static DirectAudioAcousticObservationResult.DimensionObservation dimension(
            DirectAudioAcousticObservationResult.Dimension dimension,
            String evidenceId,
            String signal,
            String confidence) {
        return new DirectAudioAcousticObservationResult.DimensionObservation(
                dimension, new BigDecimal(signal), new BigDecimal(confidence),
                List.of(new DirectAudioAcousticObservationResult.EvidenceSpan(
                        evidenceId, 100, 500, new BigDecimal(confidence),
                        "provider observation must never persist")));
    }

    private final class FakeStore implements DirectAudioDarkObservationService.Store {
        @Override
        public DirectAudioDarkObservationService.StoredObservation insert(
                DirectAudioDarkObservationService.StoredObservation observation) {
            stored.set(observation);
            return observation;
        }

        @Override
        public Optional<DirectAudioDarkObservationService.StoredObservation>
        findInspectable(Long reviewerId, Long attemptId, Instant now) {
            return reviewerAuthorized ? Optional.ofNullable(stored.get()) : Optional.empty();
        }
    }
}
