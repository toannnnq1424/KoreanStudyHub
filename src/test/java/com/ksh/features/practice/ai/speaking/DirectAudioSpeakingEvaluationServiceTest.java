package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioSpeakingEvaluationServiceTest {

    @Test
    void darkCaptureTransfersAuthorizedAudioThroughDedicatedPortWithoutScores() {
        AtomicReference<DirectAudioSpeakingEvaluationPort.AuthorizedRequest> captured =
                new AtomicReference<>();
        List<DirectAudioSpeakingEvaluationService.AuditEvent> audit = new ArrayList<>();
        DirectAudioSpeakingEvaluationService service = service(captured, audit, true);
        DirectAudioSpeakingEvaluationService.Candidate candidate = validCandidate();

        DirectAudioSpeakingEvaluationService.Outcome outcome = service.evaluate(candidate);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().audioBytes()).containsExactly(1, 2, 3, 4);
        assertThat(captured.get().purpose())
                .isEqualTo(DirectAudioSpeakingEvaluationService.PURPOSE);
        assertThat(captured.get().policyBundleId())
                .isEqualTo(DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_ID);
        assertThat(captured.get().cacheIdentity())
                .isEqualTo(DirectAudioSpeakingEvaluationService.cacheIdentity(candidate));
        assertThat(captured.get().toString()).contains("audioBytes=<redacted>")
                .doesNotContain("audio-handle-test");
        assertThat(candidate.toString()).contains("handleId=<redacted>")
                .contains("bytes=<redacted>")
                .contains("digest=<redacted>")
                .doesNotContain("audio-handle-test")
                .doesNotContain(candidate.audio().digest());
        assertThat(outcome.state()).isEqualTo("DARK_CAPTURED_NON_SCORE_BEARING");
        assertThat(outcome.scoreAvailable()).isFalse();
        assertThat(outcome.acousticScoresAvailable()).isFalse();
        assertThat(audit).extracting(
                        DirectAudioSpeakingEvaluationService.AuditEvent::eventType)
                .containsExactly("TRANSFER_AUTHORIZED", "DARK_CAPTURE_COMPLETED");
        assertThat(audit.toString()).doesNotContain("audio-handle-test")
                .doesNotContain(candidate.audio().digest())
                .doesNotContain("TEST-PROVIDER-REQUEST");
    }

    @Test
    void consentWithdrawalDeletionAndUnauthorizedAudioNeverReachProvider() {
        List<DirectAudioSpeakingEvaluationService.Candidate> rejected = List.of(
                withConsent(validCandidate(),
                        new DirectAudioSpeakingEvaluationService.ConsentEvidence(
                                "TEST-CONSENT", DirectAudioSpeakingEvaluationService.ConsentState.WITHDRAWN,
                                DirectAudioSpeakingEvaluationService.PURPOSE, "TEST-DISCLOSURE", true)),
                withConsent(validCandidate(),
                        new DirectAudioSpeakingEvaluationService.ConsentEvidence(
                                "TEST-CONSENT", DirectAudioSpeakingEvaluationService.ConsentState.DELETED,
                                DirectAudioSpeakingEvaluationService.PURPOSE, "TEST-DISCLOSURE", true)),
                withAudio(validCandidate(), new DirectAudioSpeakingEvaluationService.AudioEvidence(
                        "audio-handle-test", new byte[]{1}, "audio/webm",
                        "audio-digest-test", false, true, false)),
                withAudio(validCandidate(), new DirectAudioSpeakingEvaluationService.AudioEvidence(
                        "audio-handle-test", new byte[]{1, 2, 3, 4}, "audio/webm",
                        "wrong-digest", true, true, false)),
                withAudio(validCandidate(), new DirectAudioSpeakingEvaluationService.AudioEvidence(
                        "audio-handle-test", new byte[]{1}, "audio/webm",
                        "audio-digest-test", true, true, true)));

        assertAllRejectedWithoutTransfer(rejected);
    }

    @Test
    void missingReviewerProviderOrCalibrationEvidenceNeverReachesProvider() {
        DirectAudioSpeakingEvaluationService.Candidate valid = validCandidate();
        assertAllRejectedWithoutTransfer(List.of(
                withReviewer(valid, new DirectAudioSpeakingEvaluationService.ReviewerPolicy(
                        "", true, true)),
                withProvider(valid, new DirectAudioSpeakingEvaluationService.ProviderPolicy(
                        "TEST-PROFILE", "", "TEST-NONTRAIN", "TEST-RETENTION",
                        "TEST-DELETION")),
                withProvider(valid, new DirectAudioSpeakingEvaluationService.ProviderPolicy(
                        "UNAPPROVED-PROFILE", "TEST-REGION", "TEST-NONTRAINING",
                        "TEST-RETENTION", "TEST-DELETION-SLA")),
                withCalibration(valid, new DirectAudioSpeakingEvaluationService.CalibrationEvidence(
                        "TEST-CORPUS", "TEST-CALIBRATION", "", "TEST-REPEAT")),
                withCalibration(valid, new DirectAudioSpeakingEvaluationService.CalibrationEvidence(
                        "UNAPPROVED-CORPUS", "TEST-ACOUSTIC-CALIBRATION",
                        "TEST-FAIRNESS", "TEST-REPEATABILITY"))));
    }

    @Test
    void disabledAndPrematureScoreRolloutsNeverReachProvider() {
        DirectAudioSpeakingEvaluationService.Candidate valid = validCandidate();
        assertAllRejectedWithoutTransfer(List.of(
                withRollout(valid, DirectAudioSpeakingEvaluationService.RolloutState.DISABLED),
                withRollout(valid, DirectAudioSpeakingEvaluationService.RolloutState.SCORE_ENABLED)));
    }

    @Test
    void providerConsumptionMustBeCapturedAndStillCannotReleaseScore() {
        AtomicReference<DirectAudioSpeakingEvaluationPort.AuthorizedRequest> captured =
                new AtomicReference<>();
        DirectAudioSpeakingEvaluationService service =
                service(captured, new ArrayList<>(), false);

        DirectAudioSpeakingEvaluationService.Outcome outcome =
                service.evaluate(validCandidate());

        assertThat(captured.get()).isNotNull();
        assertThat(outcome.rejectionReason())
                .isEqualTo("PROVIDER_AUDIO_CONSUMPTION_UNPROVEN");
        assertThat(outcome.scoreAvailable()).isFalse();
        assertThat(outcome.acousticScoresAvailable()).isFalse();
    }

    @Test
    void cacheIdentityIsBoundToConsentDisclosureAndReviewerEvidence() {
        DirectAudioSpeakingEvaluationService.Candidate original = validCandidate();
        DirectAudioSpeakingEvaluationService.Candidate changedConsent = withConsent(
                original, new DirectAudioSpeakingEvaluationService.ConsentEvidence(
                        "TEST-CONSENT-2", DirectAudioSpeakingEvaluationService.ConsentState.ACTIVE,
                        DirectAudioSpeakingEvaluationService.PURPOSE,
                        "TEST-DISCLOSURE-V2", true));
        DirectAudioSpeakingEvaluationService.Candidate changedReviewer = withReviewer(
                original, new DirectAudioSpeakingEvaluationService.ReviewerPolicy(
                        "TEST-REVIEWER-POLICY-2", true, true));

        assertThat(DirectAudioSpeakingEvaluationService.cacheIdentity(original))
                .isNotEqualTo(DirectAudioSpeakingEvaluationService.cacheIdentity(changedConsent))
                .isNotEqualTo(DirectAudioSpeakingEvaluationService.cacheIdentity(changedReviewer));
    }

    @Test
    void blankProviderReceiptIdentityCannotProveConsumption() {
        DirectAudioSpeakingEvaluationService.Candidate allowed = validCandidate();
        DirectAudioSpeakingEvaluationService service =
                new DirectAudioSpeakingEvaluationService(
                        request -> new DirectAudioSpeakingEvaluationPort.Receipt("", true),
                        event -> { },
                        new DirectAudioSpeakingEvaluationService.ReadinessAuthority() {
                            @Override public boolean providerPolicyAllowed(
                                    DirectAudioSpeakingEvaluationService.ProviderPolicy policy) {
                                return allowed.providerPolicy().equals(policy);
                            }
                            @Override public boolean calibrationApproved(
                                    DirectAudioSpeakingEvaluationService.CalibrationEvidence evidence) {
                                return allowed.calibration().equals(evidence);
                            }
                        });

        var outcome = service.evaluate(allowed);

        assertThat(outcome.rejectionReason())
                .isEqualTo("PROVIDER_AUDIO_CONSUMPTION_UNPROVEN");
        assertThat(outcome.scoreAvailable()).isFalse();
    }

    private static DirectAudioSpeakingEvaluationService service(
            AtomicReference<DirectAudioSpeakingEvaluationPort.AuthorizedRequest> captured,
            List<DirectAudioSpeakingEvaluationService.AuditEvent> audit,
            boolean consumed) {
        DirectAudioSpeakingEvaluationService.Candidate allowed = validCandidate();
        return new DirectAudioSpeakingEvaluationService(request -> {
            captured.set(request);
            return new DirectAudioSpeakingEvaluationPort.Receipt(
                    "TEST-PROVIDER-REQUEST", consumed);
        }, audit::add, new DirectAudioSpeakingEvaluationService.ReadinessAuthority() {
            @Override
            public boolean providerPolicyAllowed(
                    DirectAudioSpeakingEvaluationService.ProviderPolicy policy) {
                return allowed.providerPolicy().equals(policy);
            }

            @Override
            public boolean calibrationApproved(
                    DirectAudioSpeakingEvaluationService.CalibrationEvidence evidence) {
                return allowed.calibration().equals(evidence);
            }
        });
    }

    private static void assertAllRejectedWithoutTransfer(
            List<DirectAudioSpeakingEvaluationService.Candidate> candidates) {
        for (DirectAudioSpeakingEvaluationService.Candidate candidate : candidates) {
            AtomicReference<DirectAudioSpeakingEvaluationPort.AuthorizedRequest> captured =
                    new AtomicReference<>();
            DirectAudioSpeakingEvaluationService.Outcome outcome =
                    service(captured, new ArrayList<>(), true).evaluate(candidate);
            assertThat(captured.get()).isNull();
            assertThat(outcome.scoreAvailable()).isFalse();
            assertThat(outcome.acousticScoresAvailable()).isFalse();
            assertThat(outcome.rejectionReason()).isNotBlank();
        }
    }

    private static DirectAudioSpeakingEvaluationService.Candidate validCandidate() {
        return new DirectAudioSpeakingEvaluationService.Candidate(
                "request-test", 1L, 2L, 3L,
                new DirectAudioSpeakingEvaluationService.AudioEvidence(
                        "audio-handle-test", new byte[]{1, 2, 3, 4},
                        "audio/webm",
                        "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a",
                        true, true, false),
                new DirectAudioSpeakingEvaluationService.ConsentEvidence(
                        "TEST-CONSENT", DirectAudioSpeakingEvaluationService.ConsentState.ACTIVE,
                        DirectAudioSpeakingEvaluationService.PURPOSE,
                        "TEST-DISCLOSURE", true),
                new DirectAudioSpeakingEvaluationService.ReviewerPolicy(
                        "TEST-REVIEWER-POLICY", true, true),
                new DirectAudioSpeakingEvaluationService.ProviderPolicy(
                        "TEST-PROVIDER-PROFILE", "TEST-REGION", "TEST-NONTRAINING",
                        "TEST-RETENTION", "TEST-DELETION-SLA"),
                new DirectAudioSpeakingEvaluationService.CalibrationEvidence(
                        "TEST-KOREAN-CORPUS", "TEST-ACOUSTIC-CALIBRATION",
                        "TEST-FAIRNESS", "TEST-REPEATABILITY"),
                DirectAudioSpeakingEvaluationService.RolloutState.DARK_CAPTURE);
    }

    private static DirectAudioSpeakingEvaluationService.Candidate withConsent(
            DirectAudioSpeakingEvaluationService.Candidate c,
            DirectAudioSpeakingEvaluationService.ConsentEvidence value) {
        return new DirectAudioSpeakingEvaluationService.Candidate(
                c.requestId(), c.userId(), c.attemptId(), c.questionId(), c.audio(), value,
                c.reviewerPolicy(), c.providerPolicy(), c.calibration(), c.rolloutState());
    }

    private static DirectAudioSpeakingEvaluationService.Candidate withAudio(
            DirectAudioSpeakingEvaluationService.Candidate c,
            DirectAudioSpeakingEvaluationService.AudioEvidence value) {
        return new DirectAudioSpeakingEvaluationService.Candidate(
                c.requestId(), c.userId(), c.attemptId(), c.questionId(), value, c.consent(),
                c.reviewerPolicy(), c.providerPolicy(), c.calibration(), c.rolloutState());
    }

    private static DirectAudioSpeakingEvaluationService.Candidate withReviewer(
            DirectAudioSpeakingEvaluationService.Candidate c,
            DirectAudioSpeakingEvaluationService.ReviewerPolicy value) {
        return new DirectAudioSpeakingEvaluationService.Candidate(
                c.requestId(), c.userId(), c.attemptId(), c.questionId(), c.audio(), c.consent(),
                value, c.providerPolicy(), c.calibration(), c.rolloutState());
    }

    private static DirectAudioSpeakingEvaluationService.Candidate withProvider(
            DirectAudioSpeakingEvaluationService.Candidate c,
            DirectAudioSpeakingEvaluationService.ProviderPolicy value) {
        return new DirectAudioSpeakingEvaluationService.Candidate(
                c.requestId(), c.userId(), c.attemptId(), c.questionId(), c.audio(), c.consent(),
                c.reviewerPolicy(), value, c.calibration(), c.rolloutState());
    }

    private static DirectAudioSpeakingEvaluationService.Candidate withCalibration(
            DirectAudioSpeakingEvaluationService.Candidate c,
            DirectAudioSpeakingEvaluationService.CalibrationEvidence value) {
        return new DirectAudioSpeakingEvaluationService.Candidate(
                c.requestId(), c.userId(), c.attemptId(), c.questionId(), c.audio(), c.consent(),
                c.reviewerPolicy(), c.providerPolicy(), value, c.rolloutState());
    }

    private static DirectAudioSpeakingEvaluationService.Candidate withRollout(
            DirectAudioSpeakingEvaluationService.Candidate c,
            DirectAudioSpeakingEvaluationService.RolloutState value) {
        return new DirectAudioSpeakingEvaluationService.Candidate(
                c.requestId(), c.userId(), c.attemptId(), c.questionId(), c.audio(), c.consent(),
                c.reviewerPolicy(), c.providerPolicy(), c.calibration(), value);
    }
}
