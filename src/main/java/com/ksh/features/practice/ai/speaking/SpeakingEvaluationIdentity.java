package com.ksh.features.practice.ai.speaking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record SpeakingEvaluationIdentity(
        Long attemptId,
        Long questionId,
        SpeakingEvaluationSource source,
        Long audioMediaId,
        Long mediaVersion,
        String textFallbackHash,
        String transcriptionModel,
        String evaluatorModel,
        String promptVersion,
        String rubricVersion,
        String schemaVersion
) {
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
        return new SpeakingEvaluationIdentity(
                attemptId,
                questionId,
                SpeakingEvaluationSource.PROVIDER,
                audioMediaId,
                mediaVersion,
                null,
                blankToNull(transcriptionModel),
                blankToNull(evaluatorModel),
                blankToNull(promptVersion),
                blankToNull(rubricVersion),
                blankToNull(schemaVersion));
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
        return new SpeakingEvaluationIdentity(
                attemptId,
                questionId,
                SpeakingEvaluationSource.TEXT_FALLBACK,
                null,
                null,
                hashNormalizedText(textFallbackAnswer),
                null,
                blankToNull(evaluatorModel),
                blankToNull(promptVersion),
                blankToNull(rubricVersion),
                blankToNull(schemaVersion));
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
                && java.util.Objects.equals(evaluatorModel, blankToNull(stored.model()))
                && java.util.Objects.equals(promptVersion, blankToNull(stored.promptVersion()))
                && java.util.Objects.equals(rubricVersion, blankToNull(stored.rubricVersion()))
                && java.util.Objects.equals(schemaVersion, blankToNull(stored.schemaVersion()));
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
                + ", source=" + source
                + ", audioMediaPresent=" + (audioMediaId != null)
                + ", mediaVersionPresent=" + (mediaVersion != null)
                + ", textFallbackHashPresent=" + (textFallbackHash != null)
                + ", transcriptionModel='" + transcriptionModel + '\''
                + ", evaluatorModel='" + evaluatorModel + '\''
                + ", promptVersion='" + promptVersion + '\''
                + ", rubricVersion='" + rubricVersion + '\''
                + ", schemaVersion='" + schemaVersion + '\''
                + '}';
    }
}
