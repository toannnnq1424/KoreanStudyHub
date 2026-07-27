package com.ksh.features.practice.manage.speaking;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Provider-neutral lecturer prompt-authoring contract.
 *
 * <p>These types are deliberately separate from learner-response transcription.
 * A prompt transcript is evaluator context only and is never a learner answer or
 * acoustic evidence.</p>
 */
public final class SpeakingPromptAiContract {

    public static final String CONTRACT_VERSION = "speaking-prompt-authoring-v1";
    public static final int MAX_AUDIO_BYTES = 52_428_800;
    public static final long MAX_AUDIO_DURATION_MILLIS = 600_000L;
    public static final int MAX_PROMPT_TEXT_CHARS = 16_000;
    public static final int MAX_PROMPT_TRANSCRIPT_CHARS = 1_000_000;

    private SpeakingPromptAiContract() {
    }

    public enum Operation {
        STT("stt"),
        TTS("tts");

        private final String code;

        Operation(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public enum PublicErrorCategory {
        INVALID_INPUT,
        CONFIGURATION,
        RATE_LIMIT,
        TIMEOUT,
        TRANSPORT,
        PROVIDER_REJECTED,
        EMPTY_OUTPUT,
        MALFORMED_OUTPUT,
        STALE_COMPLETION
    }

    /**
     * Verified private lecturer audio passed to an STT adapter or returned by a
     * TTS adapter. The byte array is defensively copied at both boundaries.
     */
    record VerifiedAudio(
            byte[] bytes,
            String filename,
            String mimeType,
            String sha256,
            long durationMillis
    ) {
        VerifiedAudio {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Verified audio bytes must not be empty");
            }
            if (bytes.length > MAX_AUDIO_BYTES) {
                throw new IllegalArgumentException(
                        "Verified audio exceeds the authoring byte limit");
            }
            bytes = Arrays.copyOf(bytes, bytes.length);
            filename = bounded(filename, "verified audio filename", 255);
            mimeType = bounded(mimeType, "verified audio MIME type", 128)
                    .toLowerCase(Locale.ROOT);
            sha256 = normalizedSha256(sha256, "verified audio SHA-256");
            if (!sha256.equals(exactBytesSha256(bytes))) {
                throw new IllegalArgumentException(
                        "Verified audio SHA-256 does not match exact audio bytes");
            }
            if (durationMillis <= 0L || durationMillis > MAX_AUDIO_DURATION_MILLIS) {
                throw new IllegalArgumentException(
                        "Verified audio duration is outside the authoring limit");
            }
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            return candidate instanceof VerifiedAudio other
                    && durationMillis == other.durationMillis
                    && Arrays.equals(bytes, other.bytes)
                    && filename.equals(other.filename)
                    && mimeType.equals(other.mimeType)
                    && sha256.equals(other.sha256);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(filename, mimeType, sha256, durationMillis);
            return 31 * result + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "VerifiedAudio{"
                    + "byteSize=" + bytes.length
                    + ", mimeType='" + mimeType + '\''
                    + ", sha256Present=true"
                    + ", durationMillis=" + durationMillis
                    + '}';
        }
    }

    record SttRequest(
            VerifiedAudio audio,
            String languageTag,
            String contractVersion
    ) {
        SttRequest {
            audio = Objects.requireNonNull(audio, "audio");
            languageTag = bounded(languageTag, "STT language tag", 32);
            contractVersion = exactContractVersion(contractVersion);
        }
    }

    /**
     * Original provider transcript plus bounded provenance. Correction history
     * and lecturer confirmation belong to the persistence layer, not this DTO.
     */
    record SttResult(
            String providerTranscript,
            BigDecimal confidence,
            String providerCode,
            String modelCode,
            String languageTag,
            String providerRequestReference,
            String purposeCode,
            String retentionCode
    ) {
        SttResult {
            providerTranscript = boundedPreservingText(
                    providerTranscript,
                    "provider prompt transcript",
                    MAX_PROMPT_TRANSCRIPT_CHARS);
            if (confidence != null
                    && (confidence.compareTo(BigDecimal.ZERO) < 0
                    || confidence.compareTo(BigDecimal.ONE) > 0)) {
                throw new IllegalArgumentException("STT confidence must be between 0 and 1");
            }
            providerCode = bounded(providerCode, "STT provider code", 64);
            modelCode = bounded(modelCode, "STT model code", 128);
            languageTag = bounded(languageTag, "STT language tag", 32);
            providerRequestReference = optional(providerRequestReference, 255);
            purposeCode = bounded(purposeCode, "STT purpose code", 64);
            retentionCode = bounded(retentionCode, "STT retention code", 64);
        }

        @Override
        public String toString() {
            return "SttResult{"
                    + "transcriptPresent=true"
                    + ", transcriptLength=" + providerTranscript.length()
                    + ", confidence=" + confidence
                    + ", providerCode='" + providerCode + '\''
                    + ", modelCode='" + modelCode + '\''
                    + ", languageTag='" + languageTag + '\''
                    + ", providerRequestReferencePresent="
                    + (providerRequestReference != null)
                    + ", purposeCode='" + purposeCode + '\''
                    + ", retentionCode='" + retentionCode + '\''
                    + '}';
        }
    }

    /**
     * Prompt text is preserved exactly for the TTS adapter and immutable
     * snapshot. Its fingerprint identity hashes a Unicode-NFC view without
     * trimming, case folding, punctuation removal or whitespace collapsing.
     */
    record TtsRequest(
            String promptText,
            String promptTextSha256,
            String languageTag,
            String voiceCode,
            BigDecimal speed,
            String outputFormat,
            String contractVersion
    ) {
        TtsRequest {
            promptText = boundedPreservingText(
                    promptText, "TTS prompt text", MAX_PROMPT_TEXT_CHARS);
            promptTextSha256 = normalizedSha256(
                    promptTextSha256, "TTS prompt text SHA-256");
            if (!promptTextSha256.equals(unicodeNfcUtf8Sha256(promptText))) {
                throw new IllegalArgumentException(
                        "TTS prompt text SHA-256 does not match Unicode-NFC UTF-8 prompt text");
            }
            languageTag = bounded(languageTag, "TTS language tag", 32);
            voiceCode = bounded(voiceCode, "TTS voice code", 128);
            speed = boundedSpeed(speed);
            outputFormat = bounded(outputFormat, "TTS output format", 32)
                    .toLowerCase(Locale.ROOT);
            contractVersion = exactContractVersion(contractVersion);
        }

        @Override
        public String toString() {
            return "TtsRequest{"
                    + "promptTextPresent=true"
                    + ", promptTextLength=" + promptText.length()
                    + ", promptTextSha256Present=true"
                    + ", languageTag='" + languageTag + '\''
                    + ", voiceCode='" + voiceCode + '\''
                    + ", speed=" + speed
                    + ", outputFormat='" + outputFormat + '\''
                    + ", contractVersion='" + contractVersion + '\''
                    + '}';
        }
    }

    record TtsResult(
            VerifiedAudio generatedAudio,
            String providerCode,
            String modelCode,
            String languageTag,
            String voiceCode,
            BigDecimal speed,
            String outputFormat,
            String providerRequestReference,
            String purposeCode,
            String retentionCode
    ) {
        TtsResult {
            generatedAudio = Objects.requireNonNull(generatedAudio, "generatedAudio");
            providerCode = bounded(providerCode, "TTS provider code", 64);
            modelCode = bounded(modelCode, "TTS model code", 128);
            languageTag = bounded(languageTag, "TTS language tag", 32);
            voiceCode = bounded(voiceCode, "TTS voice code", 128);
            speed = boundedSpeed(speed);
            outputFormat = bounded(outputFormat, "TTS output format", 32)
                    .toLowerCase(Locale.ROOT);
            providerRequestReference = optional(providerRequestReference, 255);
            purposeCode = bounded(purposeCode, "TTS purpose code", 64);
            retentionCode = bounded(retentionCode, "TTS retention code", 64);
        }

        @Override
        public String toString() {
            return "TtsResult{"
                    + "generatedAudioPresent=true"
                    + ", providerCode='" + providerCode + '\''
                    + ", modelCode='" + modelCode + '\''
                    + ", languageTag='" + languageTag + '\''
                    + ", voiceCode='" + voiceCode + '\''
                    + ", speed=" + speed
                    + ", outputFormat='" + outputFormat + '\''
                    + ", providerRequestReferencePresent="
                    + (providerRequestReference != null)
                    + ", purposeCode='" + purposeCode + '\''
                    + ", retentionCode='" + retentionCode + '\''
                    + '}';
        }
    }

    public static final class ProviderFailure extends RuntimeException {
        private final PublicErrorCategory publicCategory;
        private final boolean retryable;

        public ProviderFailure(
                PublicErrorCategory publicCategory,
                boolean retryable,
                String providerRequestReference,
                Throwable cause) {
            super("Speaking prompt provider operation failed: "
                    + Objects.requireNonNull(publicCategory, "publicCategory"), cause);
            this.publicCategory = publicCategory;
            this.retryable = retryable;
            optional(providerRequestReference, 255);
        }

        public PublicErrorCategory publicCategory() {
            return publicCategory;
        }

        public boolean retryable() {
            return retryable;
        }

    }

    private static BigDecimal boundedSpeed(BigDecimal speed) {
        if (speed == null
                || speed.compareTo(new BigDecimal("0.25")) < 0
                || speed.compareTo(new BigDecimal("4.00")) > 0
                || speed.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("TTS speed must be between 0.25 and 4.00");
        }
        return speed.stripTrailingZeros();
    }

    /**
     * Hashes a Unicode-NFC view of the exact text. The original text remains
     * unchanged for storage and TTS; no trimming or whitespace/punctuation
     * normalization occurs.
     */
    public static String unicodeNfcUtf8Sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Text to hash must not be null");
        }
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        return exactBytesSha256(nfc.getBytes(StandardCharsets.UTF_8));
    }

    public static String exactBytesSha256(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Bytes to hash must not be null");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", exception);
        }
    }

    private static String exactContractVersion(String value) {
        String version = required(value, "prompt-authoring contract version");
        if (!CONTRACT_VERSION.equals(version)) {
            throw new IllegalArgumentException(
                    "Unsupported prompt-authoring contract version: " + version);
        }
        return version;
    }

    private static String normalizedSha256(String value, String label) {
        String hash = required(value, label).toLowerCase(Locale.ROOT);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must contain 64 hexadecimal characters");
        }
        return hash;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + label);
        }
        return value.trim();
    }

    private static String bounded(String value, String label, int maximumLength) {
        String bounded = required(value, label);
        if (bounded.length() > maximumLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return bounded;
    }

    private static String boundedPreservingText(
            String value,
            String label,
            int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + label);
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return value;
    }

    private static String optional(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("Optional provider reference is too long");
        }
        return normalized;
    }
}
