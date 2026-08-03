package com.ksh.features.practice.ai.speaking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fail-closed authorization and dark-capture boundary for Speaking branch B.
 * No score is released by this slice.
 */
public final class DirectAudioSpeakingEvaluationService {

    public static final String CAPABILITY_ID =
            "KSH_DIRECT_AUDIO_ACOUSTIC_EVALUATION_V1";
    public static final String PURPOSE =
            "PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION";
    public static final String POLICY_BUNDLE_ID =
            "KSH_SPEAKING_DIRECT_AUDIO_POLICY_BUNDLE_V1";
    private static final String POLICY_COMPONENTS = String.join("|",
            POLICY_BUNDLE_ID,
            "capability=" + CAPABILITY_ID,
            "purpose=" + PURPOSE,
            "consent=explicit-active-withdrawable-v1",
            "reviewer=explicit-least-privilege-v1",
            "provider=region-nontraining-retention-deletion-v1",
            "calibration=korean-corpus-fairness-repeatability-v1",
            "rollout=dark-capture-no-score-v1");
    public static final String POLICY_BUNDLE_FINGERPRINT =
            sha256(POLICY_COMPONENTS);

    private final DirectAudioSpeakingEvaluationPort provider;
    private final AuditSink audit;
    private final ReadinessAuthority readiness;

    public DirectAudioSpeakingEvaluationService(
            DirectAudioSpeakingEvaluationPort provider,
            AuditSink audit,
            ReadinessAuthority readiness) {
        this.provider = java.util.Objects.requireNonNull(provider);
        this.audit = java.util.Objects.requireNonNull(audit);
        this.readiness = java.util.Objects.requireNonNull(readiness);
    }

    public Outcome evaluate(Candidate candidate) {
        String rejection = rejection(candidate);
        if (rejection != null) {
            audit.record(AuditEvent.of(candidate, "TRANSFER_REJECTED", rejection));
            return Outcome.rejected(rejection);
        }
        DirectAudioSpeakingEvaluationPort.AuthorizedRequest request =
                new DirectAudioSpeakingEvaluationPort.AuthorizedRequest(
                        candidate.requestId(),
                        candidate.attemptId(),
                        candidate.questionId(),
                        candidate.audio().bytes(),
                        candidate.audio().mimeType(),
                        candidate.audio().digest(),
                        PURPOSE,
                        POLICY_BUNDLE_ID,
                        POLICY_BUNDLE_FINGERPRINT,
                        cacheIdentity(candidate));
        audit.record(AuditEvent.of(candidate, "TRANSFER_AUTHORIZED", "DARK_CAPTURE"));
        DirectAudioSpeakingEvaluationPort.Receipt receipt = provider.evaluate(request);
        if (receipt == null || !receipt.audioConsumed()) {
            audit.record(AuditEvent.of(
                    candidate, "PROVIDER_CONSUMPTION_UNPROVEN", "NON_SCORE_BEARING"));
            return Outcome.rejected("PROVIDER_AUDIO_CONSUMPTION_UNPROVEN");
        }
        audit.record(AuditEvent.of(candidate, "DARK_CAPTURE_COMPLETED", "NON_SCORE_BEARING"));
        return new Outcome(
                "DARK_CAPTURED_NON_SCORE_BEARING",
                false,
                false,
                null,
                request.cacheIdentity());
    }

    public static String cacheIdentity(Candidate candidate) {
        return "ksh-speaking-direct-audio-v1|sha256|" + sha256(String.join("|",
                POLICY_BUNDLE_FINGERPRINT,
                candidate.providerPolicy().providerProfileId(),
                candidate.providerPolicy().regionEvidenceId(),
                candidate.providerPolicy().nonTrainingEvidenceId(),
                candidate.providerPolicy().retentionEvidenceId(),
                candidate.providerPolicy().deletionSlaEvidenceId(),
                candidate.calibration().corpusEvidenceId(),
                candidate.calibration().acousticCalibrationEvidenceId(),
                candidate.calibration().fairnessEvidenceId(),
                candidate.calibration().repeatabilityEvidenceId(),
                candidate.audio().digest()));
    }

    private String rejection(Candidate candidate) {
        if (candidate == null || !present(candidate.requestId())
                || candidate.userId() == null || candidate.attemptId() == null
                || candidate.questionId() == null) {
            return "REQUEST_IDENTITY_INCOMPLETE";
        }
        AudioEvidence audio = candidate.audio();
        if (audio == null || !audio.ownerBound() || !audio.ready()
                || audio.deleted() || audio.bytes().length == 0
                || !present(audio.handleId()) || !present(audio.mimeType())
                || !present(audio.digest())
                || !audio.digest().equals(sha256(audio.bytes()))) {
            return "AUDIO_NOT_AUTHORIZED_OR_AVAILABLE";
        }
        ConsentEvidence consent = candidate.consent();
        if (consent == null || consent.state() != ConsentState.ACTIVE
                || !PURPOSE.equals(consent.purpose())
                || !present(consent.evidenceId())
                || !present(consent.disclosureVersion())
                || !consent.withdrawalSupported()) {
            return "CONSENT_NOT_ACTIVE_FOR_PURPOSE";
        }
        ReviewerPolicy reviewer = candidate.reviewerPolicy();
        if (reviewer == null || !reviewer.explicitAccessRequired()
                || !reviewer.leastPrivilegeVerified()
                || !present(reviewer.evidenceId())) {
            return "REVIEWER_AUDIO_ACCESS_POLICY_UNVERIFIED";
        }
        ProviderPolicy providerPolicy = candidate.providerPolicy();
        if (providerPolicy == null
                || !present(providerPolicy.providerProfileId())
                || !present(providerPolicy.regionEvidenceId())
                || !present(providerPolicy.nonTrainingEvidenceId())
                || !present(providerPolicy.retentionEvidenceId())
                || !present(providerPolicy.deletionSlaEvidenceId())
                || !readiness.providerPolicyAllowed(providerPolicy)) {
            return "PROVIDER_DATA_POLICY_UNVERIFIED";
        }
        CalibrationEvidence calibration = candidate.calibration();
        if (calibration == null
                || !present(calibration.corpusEvidenceId())
                || !present(calibration.acousticCalibrationEvidenceId())
                || !present(calibration.fairnessEvidenceId())
                || !present(calibration.repeatabilityEvidenceId())
                || !readiness.calibrationApproved(calibration)) {
            return "ACOUSTIC_CALIBRATION_READINESS_UNVERIFIED";
        }
        if (candidate.rolloutState() != RolloutState.DARK_CAPTURE) {
            return candidate.rolloutState() == RolloutState.SCORE_ENABLED
                    ? "SCORE_RELEASE_NOT_IMPLEMENTED"
                    : "DIRECT_AUDIO_DARK_ROLLOUT_DISABLED";
        }
        return null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public enum ConsentState { ACTIVE, WITHDRAWN, DELETED }
    public enum RolloutState { DISABLED, DARK_CAPTURE, SCORE_ENABLED }

    public record AudioEvidence(
            String handleId,
            byte[] bytes,
            String mimeType,
            String digest,
            boolean ownerBound,
            boolean ready,
            boolean deleted) {
        public AudioEvidence {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() {
            return "AudioEvidence[handleId=<redacted>,bytes=<redacted>,mimeType="
                    + mimeType + ",digest=<redacted>,ownerBound=" + ownerBound
                    + ",ready=" + ready + ",deleted=" + deleted + "]";
        }
    }

    public record ConsentEvidence(
            String evidenceId,
            ConsentState state,
            String purpose,
            String disclosureVersion,
            boolean withdrawalSupported) {
    }

    public record ReviewerPolicy(
            String evidenceId,
            boolean explicitAccessRequired,
            boolean leastPrivilegeVerified) {
    }

    public record ProviderPolicy(
            String providerProfileId,
            String regionEvidenceId,
            String nonTrainingEvidenceId,
            String retentionEvidenceId,
            String deletionSlaEvidenceId) {
    }

    public record CalibrationEvidence(
            String corpusEvidenceId,
            String acousticCalibrationEvidenceId,
            String fairnessEvidenceId,
            String repeatabilityEvidenceId) {
    }

    public record Candidate(
            String requestId,
            Long userId,
            Long attemptId,
            Long questionId,
            AudioEvidence audio,
            ConsentEvidence consent,
            ReviewerPolicy reviewerPolicy,
            ProviderPolicy providerPolicy,
            CalibrationEvidence calibration,
            RolloutState rolloutState) {
    }

    public record Outcome(
            String state,
            boolean scoreAvailable,
            boolean acousticScoresAvailable,
            String rejectionReason,
            String cacheIdentity) {
        static Outcome rejected(String reason) {
            return new Outcome("REJECTED_NON_SCORE_BEARING", false, false,
                    reason, null);
        }
    }

    public interface AuditSink {
        void record(AuditEvent event);
    }

    /** Trusted runtime authority; candidate data cannot self-approve readiness. */
    public interface ReadinessAuthority {
        boolean providerPolicyAllowed(ProviderPolicy policy);
        boolean calibrationApproved(CalibrationEvidence evidence);
    }

    public record AuditEvent(
            String requestId,
            Long userId,
            Long attemptId,
            Long questionId,
            String eventType,
            String reason,
            String policyBundleId,
            String policyBundleFingerprint) {
        static AuditEvent of(Candidate candidate, String eventType, String reason) {
            return new AuditEvent(
                    candidate == null ? null : candidate.requestId(),
                    candidate == null ? null : candidate.userId(),
                    candidate == null ? null : candidate.attemptId(),
                    candidate == null ? null : candidate.questionId(),
                    eventType,
                    reason,
                    POLICY_BUNDLE_ID,
                    POLICY_BUNDLE_FINGERPRINT);
        }
    }
}
