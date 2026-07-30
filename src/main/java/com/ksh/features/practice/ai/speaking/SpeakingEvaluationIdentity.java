package com.ksh.features.practice.ai.speaking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record SpeakingEvaluationIdentity(
        Long attemptId,
        Long questionId,
        Long questionVersionId,
        String promptContextFingerprint,
        String promptContextContractIdentity,
        SpeakingEvaluationSource source,
        Long audioMediaId,
        Long mediaVersion,
        String textFallbackHash,
        String transcriptionModel,
        String evaluatorModel,
        String promptVersion,
        String rubricVersion,
        String schemaVersion,
        String policyBundleId,
        String policyBundleFingerprint
) {
    public static SpeakingEvaluationIdentity audio(
            Long attemptId,
            Long questionId,
            Long questionVersionId,
            String promptContextFingerprint,
            String promptContextContractIdentity,
            Long audioMediaId,
            Long mediaVersion,
            String transcriptionModel,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion
    ) {
        return audio(
                attemptId, questionId, questionVersionId,
                promptContextFingerprint, promptContextContractIdentity,
                audioMediaId, mediaVersion, transcriptionModel, evaluatorModel,
                promptVersion, rubricVersion, schemaVersion,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                SpeakingAssessmentPolicyBundle.fingerprint());
    }

    public static SpeakingEvaluationIdentity audio(
            Long attemptId,
            Long questionId,
            Long questionVersionId,
            String promptContextFingerprint,
            String promptContextContractIdentity,
            Long audioMediaId,
            Long mediaVersion,
            String transcriptionModel,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion,
            String policyBundleId
    ) {
        return audio(
                attemptId, questionId, questionVersionId,
                promptContextFingerprint, promptContextContractIdentity,
                audioMediaId, mediaVersion, transcriptionModel, evaluatorModel,
                promptVersion, rubricVersion, schemaVersion, policyBundleId,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID.equals(
                        policyBundleId)
                        ? SpeakingAssessmentPolicyBundle.fingerprint()
                        : null);
    }

    public static SpeakingEvaluationIdentity audio(
            Long attemptId,
            Long questionId,
            Long questionVersionId,
            String promptContextFingerprint,
            String promptContextContractIdentity,
            Long audioMediaId,
            Long mediaVersion,
            String transcriptionModel,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion,
            String policyBundleId,
            String policyBundleFingerprint
    ) {
        return new SpeakingEvaluationIdentity(
                attemptId,
                questionId,
                questionVersionId,
                blankToNull(promptContextFingerprint),
                blankToNull(promptContextContractIdentity),
                SpeakingEvaluationSource.PROVIDER,
                audioMediaId,
                mediaVersion,
                null,
                blankToNull(transcriptionModel),
                blankToNull(evaluatorModel),
                blankToNull(promptVersion),
                blankToNull(rubricVersion),
                blankToNull(schemaVersion),
                blankToNull(policyBundleId),
                blankToNull(policyBundleFingerprint));
    }

    public static SpeakingEvaluationIdentity audio(
            Long attemptId,
            Long questionId,
            Long audioMediaId,
            Long mediaVersion,
            String transcriptionModel,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion
    ) {
        return audio(
                attemptId, questionId, null, null, null,
                audioMediaId, mediaVersion, transcriptionModel, evaluatorModel,
                promptVersion, rubricVersion, schemaVersion);
    }

    public static SpeakingEvaluationIdentity textFallback(
            Long attemptId,
            Long questionId,
            Long questionVersionId,
            String promptContextFingerprint,
            String promptContextContractIdentity,
            String textFallbackAnswer,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion
    ) {
        return textFallback(
                attemptId, questionId, questionVersionId,
                promptContextFingerprint, promptContextContractIdentity,
                textFallbackAnswer, evaluatorModel, promptVersion,
                rubricVersion, schemaVersion,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                SpeakingAssessmentPolicyBundle.fingerprint());
    }

    public static SpeakingEvaluationIdentity textFallback(
            Long attemptId,
            Long questionId,
            Long questionVersionId,
            String promptContextFingerprint,
            String promptContextContractIdentity,
            String textFallbackAnswer,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion,
            String policyBundleId
    ) {
        return textFallback(
                attemptId, questionId, questionVersionId,
                promptContextFingerprint, promptContextContractIdentity,
                textFallbackAnswer, evaluatorModel, promptVersion,
                rubricVersion, schemaVersion, policyBundleId,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID.equals(
                        policyBundleId)
                        ? SpeakingAssessmentPolicyBundle.fingerprint()
                        : null);
    }

    public static SpeakingEvaluationIdentity textFallback(
            Long attemptId,
            Long questionId,
            Long questionVersionId,
            String promptContextFingerprint,
            String promptContextContractIdentity,
            String textFallbackAnswer,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion,
            String policyBundleId,
            String policyBundleFingerprint
    ) {
        return new SpeakingEvaluationIdentity(
                attemptId,
                questionId,
                questionVersionId,
                blankToNull(promptContextFingerprint),
                blankToNull(promptContextContractIdentity),
                SpeakingEvaluationSource.TEXT_FALLBACK,
                null,
                null,
                hashNormalizedText(textFallbackAnswer),
                null,
                blankToNull(evaluatorModel),
                blankToNull(promptVersion),
                blankToNull(rubricVersion),
                blankToNull(schemaVersion),
                blankToNull(policyBundleId),
                blankToNull(policyBundleFingerprint));
    }

    public static SpeakingEvaluationIdentity textFallback(
            Long attemptId,
            Long questionId,
            String textFallbackAnswer,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion
    ) {
        return textFallback(
                attemptId, questionId, null, null, null,
                textFallbackAnswer, evaluatorModel, promptVersion,
                rubricVersion, schemaVersion);
    }

    public boolean matches(SpeakingEvaluationResult stored) {
        if (stored == null) {
            return false;
        }
        if (source == SpeakingEvaluationSource.PROVIDER) {
            return stored.source() == SpeakingEvaluationSource.PROVIDER
                    && java.util.Objects.equals(audioMediaId, stored.audioMediaId())
                    && java.util.Objects.equals(mediaVersion, stored.mediaVersion())
                    && java.util.Objects.equals(transcriptionModel, blankToNull(stored.transcriptionModel()))
                    && commonFieldsMatch(stored);
        }
        if (source == SpeakingEvaluationSource.TEXT_FALLBACK) {
            return stored.source() == SpeakingEvaluationSource.TEXT_FALLBACK
                    && java.util.Objects.equals(textFallbackHash, hashNormalizedText(stored.actuallyHeardTranscript()))
                    && commonFieldsMatch(stored);
        }
        return false;
    }

    private boolean commonFieldsMatch(SpeakingEvaluationResult stored) {
        return stored.currentEvidenceContract()
                && java.util.Objects.equals(
                questionVersionId, stored.questionVersionId())
                && java.util.Objects.equals(
                promptContextFingerprint,
                blankToNull(stored.promptContextFingerprint()))
                && java.util.Objects.equals(
                promptContextContractIdentity,
                blankToNull(stored.promptContextContractIdentity()))
                && java.util.Objects.equals(evaluatorModel, blankToNull(stored.model()))
                && java.util.Objects.equals(promptVersion, blankToNull(stored.promptVersion()))
                && java.util.Objects.equals(rubricVersion, blankToNull(stored.rubricVersion()))
                && java.util.Objects.equals(schemaVersion, blankToNull(stored.schemaVersion()))
                && java.util.Objects.equals(
                policyBundleId, blankToNull(stored.policyBundleId()))
                && java.util.Objects.equals(
                policyBundleFingerprint,
                blankToNull(stored.policyBundleFingerprint()));
    }

    public static String hashNormalizedText(String text) {
        String normalized = normalizeText(text);
        if (normalized == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "SpeakingEvaluationIdentity{"
                + "attemptId=" + attemptId
                + ", questionId=" + questionId
                + ", questionVersionId=" + questionVersionId
                + ", promptContextFingerprintPresent="
                + (promptContextFingerprint != null)
                + ", promptContextContractIdentity='"
                + promptContextContractIdentity + '\''
                + ", source=" + source
                + ", audioMediaPresent=" + (audioMediaId != null)
                + ", mediaVersionPresent=" + (mediaVersion != null)
                + ", textFallbackHashPresent=" + (textFallbackHash != null)
                + ", transcriptionModel='" + transcriptionModel + '\''
                + ", evaluatorModel='" + evaluatorModel + '\''
                + ", promptVersion='" + promptVersion + '\''
                + ", rubricVersion='" + rubricVersion + '\''
                + ", schemaVersion='" + schemaVersion + '\''
                + ", policyBundleId='" + policyBundleId + '\''
                + ", policyBundleFingerprintPresent="
                + (policyBundleFingerprint != null)
                + '}';
    }
}
